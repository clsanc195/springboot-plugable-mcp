package com.mcp.springpluggablemcp.dynamic.loader;

import com.mcp.springpluggablemcp.dynamic.config.DynamicToolJdbcProperties;
import com.mcp.springpluggablemcp.dynamic.mapping.DynamicToolRecord;
import com.mcp.springpluggablemcp.dynamic.mapping.ToolRecordMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

public class SimpleQueryToolSource implements ToolDefinitionSource {

    private final JdbcTemplate jdbcTemplate;
    private final DynamicToolJdbcProperties properties;
    private final ToolRecordMapper<ResultSet> mapper;

    public SimpleQueryToolSource(@Qualifier("dynamicToolsJdbcTemplate") JdbcTemplate jdbcTemplate,
                                 DynamicToolJdbcProperties properties,
                                 ToolRecordMapper<ResultSet> mapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public List<DynamicToolRecord> loadAll() {
        return jdbcTemplate.query(properties.query(),
                (rs, rowNum) -> {
                    try { return mapper.map(rs); }
                    catch (Exception e) { throw new RuntimeException(e); }
                });
    }

    @Override
    public List<DynamicToolRecord> loadSince(Instant since) {
        if (properties.deltaQuery() == null) {
            return List.of();
        }
        return jdbcTemplate.query(properties.deltaQuery(),
                (rs, rowNum) -> {
                    try { return mapper.map(rs); }
                    catch (Exception e) { throw new RuntimeException(e); }
                },
                Timestamp.from(since));
    }
}
