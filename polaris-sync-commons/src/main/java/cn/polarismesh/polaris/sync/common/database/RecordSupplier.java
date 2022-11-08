package cn.polarismesh.polaris.sync.common.database;

import java.sql.ResultSet;
import java.util.Map;
import java.util.function.Function;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface RecordSupplier<T> {

	String getMoreSqlTemplate(boolean first);

	T apply(ResultSet t) throws Exception;

}
