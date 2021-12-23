//@Author: chuntaojun <liaochuntao@live.com>
//@Description:
//@Time: 2021/12/22 14:17

package polaris

import (
	"github.com/chuntaojun/polaris-syncer/api"
	"github.com/chuntaojun/polaris-syncer/plugin"
)

func init() {

	s := func() api.Writer {
		w, err := newPolarisWriter()
		if err != nil {
			panic(err)
		}
		return w
	}

	if _, err := plugin.RegisterWriterSupplier(defaultName, s); err != nil {
		panic(err)
	}
}
