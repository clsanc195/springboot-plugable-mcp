package com.mcp.mcphostapp.mcp.tools;

import com.mcp.springpluggablemcp.tool.McpTool;
import com.mcp.springpluggablemcp.tool.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class HostTools {

    @McpTool(name = "ping", description = "Ping the MCP host app and get a pong response with a message")
    public String ping(
            @McpToolParam(description = "Optional message to include in the pong") String message) {
        return "pong: " + message;
    }
}
