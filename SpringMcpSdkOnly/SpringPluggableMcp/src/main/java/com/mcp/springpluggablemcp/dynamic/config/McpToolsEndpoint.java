package com.mcp.springpluggablemcp.dynamic.config;

import com.mcp.springpluggablemcp.dynamic.loader.DynamicToolStatus;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Actuator endpoint at {@code /actuator/mcptools}. Returns the current
 * state of all dynamic tool sources and registered tools.
 */
@Component
@Endpoint(id = "mcptools")
public class McpToolsEndpoint {

    private final DynamicToolStatus status;

    public McpToolsEndpoint(DynamicToolStatus status) {
        this.status = status;
    }

    @ReadOperation
    public Map<String, Object> mcpTools() {
        return Map.of(
                "totalTools", status.totalToolCount(),
                "tools", status.getTools(),
                "sources", status.getSources()
        );
    }
}
