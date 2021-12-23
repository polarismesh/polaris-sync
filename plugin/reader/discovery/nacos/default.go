//@Author: chuntaojun <liaochuntao@live.com>
//@Description:
//@Time: 2021/12/22 13:26

package nacos

import (
	"github.com/chuntaojun/polaris-syncer/api"
	"github.com/chuntaojun/polaris-syncer/plugin"
)

func init() {
	plugin.RegisterReaderSupplier(defaultName, func() api.Reader {
		return *newNacosReader()
	})
}
