package cn.polarismesh.polaris.sync.extension.config;

import java.util.Map;
import java.util.function.Function;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface RecordSupplier<T> extends Function<Map<String, Object>, T> {

	String getMoreSqlTemplate(boolean first);

}
