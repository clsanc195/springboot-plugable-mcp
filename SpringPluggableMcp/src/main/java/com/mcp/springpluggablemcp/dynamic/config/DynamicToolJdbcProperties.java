package com.mcp.springpluggablemcp.dynamic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JDBC-specific properties for the default tool loading path
 * ({@code SimpleQueryToolSource} + {@code DefaultToolRecordMapper}).
 * <p>
 * Clients that provide their own {@code ToolDefinitionSource} bean
 * do not need to configure any of these.
 */
@ConfigurationProperties(prefix = "dynamic-tools.jdbc")
public record DynamicToolJdbcProperties(
        Datasource datasource,
        String query,
        ColumnMapping columnMapping,
        String deltaQuery
) {

    public record Datasource(
            String url,
            String username,
            String password,
            String driverClassName
    ) {}

    public record ColumnMapping(
            String name,
            String description,
            String inputSchema,
            String executorType,
            String executorConfig
    ) {}
}
