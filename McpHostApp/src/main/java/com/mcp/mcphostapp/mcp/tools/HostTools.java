package com.mcp.mcphostapp.mcp.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class HostTools {

    @Tool(name = "ping", description = "Ping the MCP host app and get a pong response with a message")
    public String ping(
            @ToolParam(description = "Optional message to include in the pong") String message) {
        return "pong: " + message;
    }
}
