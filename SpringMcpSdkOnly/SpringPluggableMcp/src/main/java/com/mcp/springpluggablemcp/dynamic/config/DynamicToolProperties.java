package com.mcp.springpluggablemcp.dynamic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Configuration properties for the SpringPluggableMcp library.
 *
 * <pre>
 * spring-pluggable-mcp:
 *   server:
 *     name: my-mcp-server
 *     version: 1.0.0
 *     mcp-endpoint: /mcp
 *   refresh:
 *     enabled: true
 *     interval: 5m
 *   datasources:
 *     - name: primary
 *       url: jdbc:postgresql://localhost:5432/my_db
 *       username: postgres
 *       password: postgres
 *       driver-class-name: org.postgresql.Driver
 * </pre>
 */
@ConfigurationProperties(prefix = "spring-pluggable-mcp")
public record DynamicToolProperties(
        Server server,
        Refresh refresh,
        List<NamedDatasource> datasources
) {

    public record Server(
            String name,
            String version,
            String mcpEndpoint
    ) {}


    public record Refresh(
            boolean enabled,
            Duration interval
    ) {
        public Refresh {
            if (enabled && interval == null) {
                throw new IllegalArgumentException(
                        "spring-pluggable-mcp.refresh.interval is required when refresh is enabled");
            }
        }
    }

    public record NamedDatasource(
            String name,
            String url,
            String username,
            String password,
            String driverClassName
    ) {}
}
