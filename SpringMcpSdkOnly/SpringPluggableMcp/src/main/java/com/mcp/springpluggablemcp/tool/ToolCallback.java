package com.mcp.springpluggablemcp.tool;

/**
 * Represents a callable MCP tool with its definition and execution logic.
 * This is the library's own abstraction — no Spring AI dependency.
 */
public interface ToolCallback {

    /**
     * The tool's metadata (name, description, input schema).
     */
    ToolDefinition getToolDefinition();

    /**
     * Execute the tool.
     *
     * @param toolInput JSON string with the arguments sent by the MCP client
     * @return the result as a string (typically JSON)
     */
    String call(String toolInput);
}
