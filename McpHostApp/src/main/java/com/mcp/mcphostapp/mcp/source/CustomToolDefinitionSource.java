package com.mcp.mcphostapp.mcp.source;

import com.mcp.springpluggablemcp.dynamic.loader.ToolDefinitionSource;
import com.mcp.springpluggablemcp.dynamic.mapping.DynamicToolRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Component
public class CustomToolDefinitionSource implements ToolDefinitionSource {

    private static final Logger log = LoggerFactory.getLogger(CustomToolDefinitionSource.class);

    private final JdbcTemplate jdbcTemplate;
    private final String serverId;

    public CustomToolDefinitionSource(
            @Qualifier("dynamicToolsJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Value("${spring.ai.mcp.server.name}") String serverId) {
        this.jdbcTemplate = jdbcTemplate;
        this.serverId = serverId;
    }

    @Override
    public List<DynamicToolRecord> loadAll() {
        log.info("Loading tool definitions for server '{}'", serverId);

        return jdbcTemplate.query("""
                SELECT td.tool_name, td.tool_description, td.input_schema,
                       td.executor_type, td.executor_config
                FROM tool_registry tr
                JOIN tool_definitions td ON td.id = tr.tool_id
                WHERE tr.server_id = ? AND tr.enabled = true
                """,
                (rs, rowNum) -> new DynamicToolRecord(
                        rs.getString("tool_name"),
                        rs.getString("tool_description"),
                        rs.getString("input_schema"),
                        rs.getString("executor_type"),
                        rs.getString("executor_config")
                ),
                serverId);
    }

    @Override
    public List<DynamicToolRecord> loadSince(Instant since) {
        log.info("Loading delta tool definitions for server '{}' since {}", serverId, since);

        return jdbcTemplate.query("""
                SELECT td.tool_name, td.tool_description, td.input_schema,
                       td.executor_type, td.executor_config
                FROM tool_registry tr
                JOIN tool_definitions td ON td.id = tr.tool_id
                WHERE tr.server_id = ? AND tr.enabled = true
                  AND (td.updated_at > ? OR tr.updated_at > ?)
                """,
                (rs, rowNum) -> new DynamicToolRecord(
                        rs.getString("tool_name"),
                        rs.getString("tool_description"),
                        rs.getString("input_schema"),
                        rs.getString("executor_type"),
                        rs.getString("executor_config")
                ),
                serverId, Timestamp.from(since), Timestamp.from(since));
    }
}
