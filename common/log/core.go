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
//@Author: chuntaojun <liaochuntao@live.com>
//@Description:
//@Time: 2021/12/22 02:05

package log

import (
	"context"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"runtime"
	"sync/atomic"
	"time"
)

const LogPrefix = "%s %s %s=>%d : "

type LogLevel int32

const (
	Debug LogLevel = iota
	Info
	Warn
	Error
)

const (
	ErrorLevel = "[error]"
	WarnLevel  = "[warn]"
	InfoLevel  = "[info]"
	DebugLevel = "[debug]"

	TimeFormatStr = "2006-01-02 15:04:05"
)

type LogOptions func(opt *LogOption)

type LogOption struct {
	Name         string
	RollingCycle time.Duration
}

//InitLogger 初始化一个默认的 Logger
func InitLogger(baseDir string) (err error) {
	filePath = filepath.Join(baseDir, "logs")
	if err := os.MkdirAll(filePath, os.ModePerm); err != nil {
		return err
	}
	filePath = baseDir
	return nil
}

// Logger 日志打印
type Logger interface {
	//SetLevel 设置日志等级
	SetLevel(level LogLevel)

	//Debug debug 级别日志打印
	Debug(format string, args ...interface{})

	//Info info 级别日志打印
	Info(format string, args ...interface{})

	//Warn warn 级别日志打印
	Warn(format string, args ...interface{})

	//Error error 级别日志打印
	Error(format string, args ...interface{})

	//Close 关闭一个 Logger
	Close()

	//Sink 获取日志的 LogSink 对象，Sink 是实际日志输出的接口
	Sink() LogSink
}

//abstractLogger
type abstractLogger struct {
	opt          LogOption
	sink         LogSink
	level        LogLevel
	logEventChan chan LogEvent
	ctx          context.Context
	isClose      int32
}

var filePath string

//NewConsoleLogger 构建测试用的 Logger, 打印在控制台上
func NewConsoleLogger(name string) Logger {
	l := &abstractLogger{
		opt: LogOption{
			Name:         name,
			RollingCycle: 0,
		},
		sink:         &ConsoleLogSink{},
		logEventChan: make(chan LogEvent, 16384),
		ctx:          context.Background(),
	}
	l.sink.Start(l.opt)
	l.start()
	return l
}

//NewLogger 创建一个 Logger，并设置日志的文件名为
func NewLogger(options ...LogOptions) (Logger, error) {
	opt := new(LogOption)
	for _, option := range options {
		option(opt)
	}

	f, err := os.OpenFile(filepath.Join(filePath, opt.Name+".log"), os.O_CREATE|os.O_RDWR|os.O_APPEND, 0666)
	if err != nil {
		return nil, err
	}
	l := &abstractLogger{
		opt: *opt,
		sink: &FileLogSink{
			logger: log.New(f, "", log.Lmsgprefix),
		},
		logEventChan: make(chan LogEvent, 16384),
		ctx:          context.Background(),
	}

	l.start()
	return l, nil
}

//NewLoggerWithSink 构建一个 Logger, 但是日志的真实输出的 LogSink 可以自定义实现
func NewLoggerWithSink(sink LogSink, options ...LogOptions) Logger {
	opt := new(LogOption)
	for _, option := range options {
		option(opt)
	}

	l := &abstractLogger{

		sink: sink,
		ctx:  context.Background(),
	}
	l.start()
	return l
}

func (l *abstractLogger) SetLevel(level LogLevel) {
	atomic.StoreInt32((*int32)(&l.level), int32(level))
}

func (l *abstractLogger) Debug(format string, args ...interface{}) {
	if atomic.LoadInt32(&l.isClose) == 1 {
		return
	}
	l.canLog(Debug, func() {
		l.logEventChan <- LogEvent{
			Level:  Debug,
			Format: format,
			Args:   convertToLogArgs(DebugLevel, time.Now().Format(TimeFormatStr), args...),
		}
	})
}

func (l *abstractLogger) Info(format string, args ...interface{}) {
	if atomic.LoadInt32(&l.isClose) == 1 {
		return
	}
	l.canLog(Info, func() {
		l.logEventChan <- LogEvent{
			Level:  Info,
			Format: format,
			Args:   convertToLogArgs(InfoLevel, time.Now().Format(TimeFormatStr), args...),
		}
	})
}

func (l *abstractLogger) Warn(format string, args ...interface{}) {
	if atomic.LoadInt32(&l.isClose) == 1 {
		return
	}
	l.canLog(Warn, func() {
		l.logEventChan <- LogEvent{
			Level:  Warn,
			Format: format,
			Args:   convertToLogArgs(WarnLevel, time.Now().Format(TimeFormatStr), args...),
		}
	})
}

func (l *abstractLogger) Error(format string, args ...interface{}) {
	if atomic.LoadInt32(&l.isClose) == 1 {
		return
	}
	l.canLog(Error, func() {
		l.logEventChan <- LogEvent{
			Level:  Error,
			Format: format,
			Args:   convertToLogArgs(ErrorLevel, time.Now().Format(TimeFormatStr), args...),
		}
	})
}

func (l *abstractLogger) canLog(level LogLevel, print func()) {
	if atomic.LoadInt32((*int32)(&l.level)) <= int32(level) {
		print()
	}
}

func (l *abstractLogger) Close() {
	atomic.StoreInt32(&l.isClose, 1)
	l.ctx.Done()
	close(l.logEventChan)
}

func (l *abstractLogger) Sink() LogSink {
	return l.sink
}

//start 开启一个异步任务，监听logEventChan实时将日志信息输出到对应的LogSink
func (l *abstractLogger) start() {
	go func(ctx context.Context) {
		for {
			var e LogEvent
			select {
			case e = <-l.logEventChan:
				l.sink.OnEvent(e.Level, LogPrefix+e.Format, e.Args...)
			case <-ctx.Done():
				l.sink.OnEvent(Info, LogPrefix+"close logger")
				return
			}
		}
	}(l.ctx)
}

type LogEvent struct {
	Level  LogLevel
	Format string
	Args   []interface{}
}

type LogSink interface {
	//Start 开启 LogSink
	Start(opt LogOption)

	//OnEvent 处理所有的 LogEvent 事件
	OnEvent(level LogLevel, format string, args ...interface{})
}

type ConsoleLogSink struct {
}

func (fl *ConsoleLogSink) Start(opt LogOption) {
	//do nothing
}

func (fl *ConsoleLogSink) OnEvent(level LogLevel, format string, args ...interface{}) {
	fmt.Printf(format+"\n", args...)
}

type FileLogSink struct {
	opt    LogOption
	logger *log.Logger
}

func (fl *FileLogSink) Start(opt LogOption) {
	fl.opt = opt
}

func (fl *FileLogSink) OnEvent(level LogLevel, format string, args ...interface{}) {
	fl.logger.Printf(format, args...)
}

// 重新构建日志参数
func convertToLogArgs(level, time string, args ...interface{}) []interface{} {
	a := make([]interface{}, len(args)+4)
	a[0] = level
	a[1] = time
	a[2], a[3] = GetCaller(6)
	if args != nil {
		for i := 4; i < len(a); i++ {
			a[i] = args[i-4]
		}
	}
	return a
}

//GetCaller 获取调用的代码行
func GetCaller(depth int) (string, int) {
	_, file, line, _ := runtime.Caller(depth)
	return file, line
}
