package com.mcp.springpluggablemcp.dynamic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Core properties for dynamic tool loading. Only contains what every
 * implementation needs — the refresh schedule. Source-specific config
 * (JDBC, REST, file, etc.) lives in separate properties classes.
 */
@ConfigurationProperties(prefix = "dynamic-tools")
public record DynamicToolProperties(
        Refresh refresh
) {

    public record Refresh(
            boolean enabled,
            Duration interval
    ) {
        public Refresh {
            if (enabled && interval == null) {
                throw new IllegalArgumentException(
                        "dynamic-tools.refresh.interval is required when refresh is enabled");
            }
        }
    }
}
