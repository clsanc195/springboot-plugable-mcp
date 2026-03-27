package com.mcp.mcphostapp.mcp.source;

import com.mcp.springpluggablemcp.dynamic.loader.ToolDefinitionSource;
import com.mcp.springpluggablemcp.dynamic.mapping.DynamicToolRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Loads tools from the "secondary" datasource. Demonstrates a second
 * JDBC-backed source using its own named JdbcTemplate — both sources
 * coexist and load independently.
 * <p>
 * In production these would point at different databases. Here they
 * share the same DB but query different data to show the pattern.
 */
@Component
public class SecondaryJdbcToolDefinitionSource implements ToolDefinitionSource {

    private static final Logger log = LoggerFactory.getLogger(SecondaryJdbcToolDefinitionSource.class);

    private final JdbcTemplate jdbcTemplate;

    public SecondaryJdbcToolDefinitionSource(
            @Qualifier("secondary-jdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Duration refreshInterval() {
        return Duration.ofMinutes(10);
    }

    @Override
    public Duration sourceTimeout() {
        return Duration.ofSeconds(60);
    }

    @Override
    public int maxRetryAttempts() {
        return 3;
    }

    @Override
    public List<DynamicToolRecord> loadAll() {
        log.info("Loading tool definitions from secondary source");

        return jdbcTemplate.query("""
                SELECT tool_name, tool_description, input_schema,
                       executor_type, executor_config
                FROM tool_definitions
                WHERE id = 4
                """,
                (rs, rowNum) -> new DynamicToolRecord(
                        rs.getString("tool_name"),
                        rs.getString("tool_description"),
                        rs.getString("input_schema"),
                        rs.getString("executor_type"),
                        rs.getString("executor_config")
                ));
    }
}
