package com.mcp.springpluggablemcp.dynamic.mapping;

import com.mcp.springpluggablemcp.dynamic.config.DynamicToolJdbcProperties;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Default JDBC-based mapper that reads columns by the names configured
 * in {@link DynamicToolJdbcProperties.ColumnMapping}.
 */
public class DefaultToolRecordMapper implements ToolRecordMapper<ResultSet> {

    private final DynamicToolJdbcProperties.ColumnMapping columns;

    public DefaultToolRecordMapper(DynamicToolJdbcProperties.ColumnMapping columns) {
        this.columns = columns;
    }

    @Override
    public DynamicToolRecord map(ResultSet rs) throws SQLException {
        return new DynamicToolRecord(
                rs.getString(columns.name()),
                rs.getString(columns.description()),
                rs.getString(columns.inputSchema()),
                rs.getString(columns.executorType()),
                rs.getString(columns.executorConfig())
        );
    }
}
