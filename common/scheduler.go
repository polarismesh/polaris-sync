// Tencent is pleased to support the open source community by making polaris-go available.
//
// Copyright (C) 2019 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissionsr and limitations under the License.
//

package common

import (
	"container/list"
	"context"
	"sync"
	"sync/atomic"
	"time"
)

//异步任务的Future持有，可以通过这个取消一个异步任务的运行
type Future interface {
	run()
	Cancel()
}

type CtxFuture struct {
	ctx    context.Context
	cancel context.CancelFunc
}

func NewCtxFuture(ctx context.Context, cancel context.CancelFunc) *CtxFuture {
	return &CtxFuture{
		ctx:    ctx,
		cancel: cancel,
	}
}

func (f *CtxFuture) run() {
}

func (f *CtxFuture) Cancel() {
	f.cancel()
}

//Go 将一个方法异步放入协程池中执行
func Go(arg interface{}, work func(arg interface{})) Future {
	ctx, cancel := context.WithCancel(context.Background())

	DefaultScheduler.Submit(&Job{
		F:   work,
		Arg: arg,
		ctx: ctx,
	})

	return NewCtxFuture(ctx, cancel)
}

//DoTimerSchedule 利用 time.Timer 实现的周期执行，其中每次任务执行的间隔是可以动态调整的，通过 supplier func() time.Duration 函数
// 如果在 work 方法运行期间出现 panic，则无法保证任务可以继续正常执行，因此需要 work 自行 defer 去 recover 住 panic 的 Error 信息并进行处理
func DoTimerSchedule(work func(), delay time.Duration, supplier func() time.Duration) Future {
	ctx, cancel := context.WithCancel(context.Background())
	go func(ctx context.Context) {
		timer := time.NewTimer(delay)
		for {
			select {
			case <-ctx.Done():
				timer.Stop()
			case <-timer.C:
				work()
				timer.Reset(supplier())
			}
		}
	}(ctx)
	return NewCtxFuture(ctx, cancel)
}

// DoTickerSchedule 利用 time.Ticker 实现的固定周期的执行
func DoTickerSchedule(work func(), delay time.Duration) Future {
	ctx, cancel := context.WithCancel(context.Background())
	go func(ctx context.Context) {
		ticker := time.NewTicker(delay)
		for {
			select {
			case <-ctx.Done():
				ticker.Stop()
			case <-ticker.C:
				work()
			}
		}
	}(ctx)
	return NewCtxFuture(ctx, cancel)
}

//DelaySchedule 利用 time.After 实现的延迟执行
func DelaySchedule(work func(), delay time.Duration) Future {
	ctx, cancel := context.WithCancel(context.Background())
	after := time.After(delay)
	go func(ctx context.Context) {
		select {
		case <-ctx.Done():
			return
		case <-after:
			work()
		}
	}(ctx)
	return NewCtxFuture(ctx, cancel)
}

type Options struct {
	Tick        int64
	SlotNum     int32
	Interval    time.Duration
	MaxDealTask int32
}

type Option func(opts *Options)

type HashTimeWheel struct {
	rwLock     sync.RWMutex
	buckets    []*timeBucket
	timeTicker *time.Ticker
	stopSign   chan bool
	opts       *Options
}

func NewTimeWheel(opt ...Option) *HashTimeWheel {
	opts := &Options{}
	for _, op := range opt {
		op(opts)
	}

	htw := &HashTimeWheel{
		rwLock:     sync.RWMutex{},
		opts:       opts,
		timeTicker: time.NewTicker(opts.Interval),
		stopSign:   make(chan bool),
		buckets:    make([]*timeBucket, opts.Interval, opts.SlotNum),
	}

	for i := int32(0); i < opts.SlotNum; i++ {
		htw.buckets[i] = newTimeBucket()
	}
	return htw
}

//Start 必须显示的执行此方法来开启时间轮
func (htw *HashTimeWheel) Start() {
	Go(context.Background(), func(arg interface{}) {
		for {
			select {
			case <-htw.timeTicker.C:
				htw.process()
			case <-htw.stopSign:
				return
			}
		}
	})
}

type TimeFunction interface {
	Run()
}

type wrapTimeFunction struct {
	wheel  *HashTimeWheel
	target TimeFunction
	task   timeTask
	period time.Duration
}

func (w *wrapTimeFunction) Run() {
	defer func() {
		if atomic.LoadInt32(&w.task.isCancel) == 1 {
			return
		}
		pos, _ := w.wheel.getSlots(w.period)
		bucket := w.wheel.buckets[pos]
		bucket.addUserTask(w.task)
	}()
	w.target.Run()
}

type timeWheelFuture struct {
	task *timeTask
}

func (f *timeWheelFuture) run() {
	// do nothing
}

func (f *timeWheelFuture) Cancel() {
	atomic.StoreInt32(&f.task.isCancel, 1)
}

// DelayExec 延迟执行一个函数
// f TimeFunction 任务函数
// delay time.Duration : 执行延迟多久
func (htw *HashTimeWheel) DelayExec(f TimeFunction, delay time.Duration) Future {
	pos, circle := htw.getSlots(delay)
	task := timeTask{
		circle: circle,
		f:      f,
		delay:  delay,
	}
	bucket := htw.buckets[pos]
	bucket.addUserTask(task)
	return &timeWheelFuture{task: &task}
}

// ScheduleExec 定时调度执行，返回一个 Future，如果不想继续执行任务的话，就直接调用 Future.Cancel()
// f TimeFunction 任务函数
// delay time.Duration : 首次执行延迟多久
// period time.Duration : 每次任务间隔多久执行
func (htw *HashTimeWheel) ScheduleExec(f TimeFunction, delay, period time.Duration) Future {
	pos, circle := htw.getSlots(delay)
	task := timeTask{
		circle: circle,
		delay:  delay,
	}

	task.f = &wrapTimeFunction{
		wheel:  htw,
		target: f,
		task:   task,
		period: period,
	}

	bucket := htw.buckets[pos]
	bucket.addUserTask(task)
	return &timeWheelFuture{task: &task}
}

// Stop 关闭一个时间轮，所有的任务都不会在处理
func (htw *HashTimeWheel) Stop() {
	htw.stopSign <- true
	htw.clearAllAndProcess()
	for _, b := range htw.buckets {
		close(b.worker)
	}
}

func (htw *HashTimeWheel) process() {
	currentBucket := htw.buckets[htw.opts.Tick]
	htw.scanExpireAndRun(currentBucket)
	htw.opts.Tick = (htw.opts.Tick + 1) % int64(htw.opts.SlotNum)
}

func (htw *HashTimeWheel) clearAllAndProcess() {
	for i := htw.opts.SlotNum; i > 0; i-- {
		htw.process()
	}
}

type timeBucket struct {
	rwLock sync.RWMutex
	queue  *list.List
	worker chan timeTask
}

func (tb *timeBucket) addUserTask(t timeTask) {
	defer tb.rwLock.Unlock()
	tb.rwLock.Lock()
	tb.queue.PushBack(t)
}

func (tb *timeBucket) execUserTask() {
	for task := range tb.worker {
		if atomic.LoadInt32(&task.isCancel) == 1 {
			return
		}
		task.f.Run()
	}
}

func newTimeBucket() *timeBucket {
	tb := &timeBucket{
		rwLock: sync.RWMutex{},
		queue:  list.New(),
		worker: make(chan timeTask, 32),
	}
	go tb.execUserTask()
	return tb
}

func (htw *HashTimeWheel) scanExpireAndRun(tb *timeBucket) {
	execCnt := int32(0)
	maxDealTaskCnt := htw.opts.MaxDealTask
	timeout := time.NewTimer(htw.opts.Interval)
	defer tb.rwLock.Unlock()
	tb.rwLock.Lock()
	for item := tb.queue.Front(); item != nil && maxDealTaskCnt >= execCnt; {
		task := item.Value.(timeTask)
		if task.circle < 0 {
			deal := func() {
				defer timeout.Reset(htw.opts.Interval)
				select {
				case tb.worker <- task:
					execCnt++
					next := item.Next()
					tb.queue.Remove(item)
					item = next
				case <-timeout.C:
					item = item.Next()
					return
				}
			}
			deal()
		} else {
			task.circle -= 1
			item = item.Next()
			continue
		}
	}
}

type timeTask struct {
	isCancel int32
	circle   int32
	f        TimeFunction
	delay    time.Duration
}

func (htw *HashTimeWheel) getSlots(d time.Duration) (pos int32, circle int32) {
	delayTime := int64(d.Seconds())
	interval := int64(htw.opts.Interval.Seconds())
	return int32(htw.opts.Tick+delayTime/interval) % htw.opts.SlotNum, int32(delayTime / interval / int64(htw.opts.SlotNum))
}

type Job struct {
	F   func(arg interface{})
	Arg interface{}
	ctx context.Context
}

var DefaultScheduler *RoutinePool = NewRoutinePool(16, 128)

type RoutinePool struct {
	lock         sync.Locker
	size         int32
	cacheSize    int32
	isRunning    int32
	taskChan     chan *Job
	workers      []worker
	panicHandler func(err interface{})
}

//NewRoutinePool 构建一个新的协程池
//size int32 协程数量
//cacheSize 任务缓存通道大小
func NewRoutinePool(size, cacheSize int32) *RoutinePool {
	pool := &RoutinePool{
		size:      size,
		isRunning: 1,
		cacheSize: cacheSize,
		taskChan:  make(chan *Job, cacheSize),
	}

	pool.init()
	return pool
}

//Resize 重新调整协程池的大小
func (rp *RoutinePool) Resize(newSize int32) {
	defer rp.lock.Unlock()
	rp.lock.Lock()
	if newSize < rp.size {
		newWorkers := make([]worker, newSize)
		i := int32(0)
		for ; i < newSize; i++ {
			newWorkers[i] = rp.workers[i]
		}
		for ; i < rp.size; i++ {
			atomic.StoreInt32(&rp.workers[i].shutdown, 1)
		}
		rp.workers = newWorkers
		rp.size = newSize
		return
	}
	if newSize > rp.size {
		newWorkers := make([]worker, newSize)
		i := int32(0)
		for ; i < rp.size; i++ {
			newWorkers[i] = rp.workers[i]
		}
		for ; i < newSize; i++ {
			newWorkers[i] = worker{owner: rp}
			go newWorkers[i].run()
		}
		rp.workers = newWorkers
		rp.size = newSize
		return
	}
}

//SetPanicHandler 设置出现panic时的处理函数
func (rp *RoutinePool) SetPanicHandler(panicHandler func(err interface{})) {
	rp.panicHandler = panicHandler
}

func (rp *RoutinePool) init() {
	atomic.StoreInt32(&rp.isRunning, 1)
	workers := make([]worker, rp.size)
	for i := int32(0); i < rp.size; i++ {
		workers[i] = worker{owner: rp}
		go workers[i].run()
	}
}

//Submit(task func()) 提交一个函数任务
func (rp *RoutinePool) Submit(task *Job) {
	rp.taskChan <- task
}

//Close 关闭协程池
func (rp *RoutinePool) Close() {
	atomic.StoreInt32(&rp.isRunning, 0)
	close(rp.taskChan)
}

type worker struct {
	shutdown int32
	owner    *RoutinePool
}

func (w worker) run() {
	for task := range w.owner.taskChan {
		if atomic.LoadInt32(&w.shutdown) == 1 {
			return
		}
		deal := func() {
			defer func() {
				if err := recover(); err != nil {
					w.owner.panicHandler(err)
				}
			}()
			task.F(task.Arg)
		}
		deal()
		if atomic.LoadInt32(&w.owner.isRunning) == int32(0) {
			return
		}
	}
}
