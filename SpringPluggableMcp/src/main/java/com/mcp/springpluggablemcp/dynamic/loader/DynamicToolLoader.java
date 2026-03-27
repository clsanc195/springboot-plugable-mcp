package com.mcp.springpluggablemcp.dynamic.loader;

/**
 * Responsible for loading dynamic tool definitions from a
 * {@link ToolDefinitionSource}, registering them with the MCP server,
 * and optionally refreshing them on a schedule.
 * <p>
 * Clients can replace the default implementation by providing their
 * own {@code DynamicToolLoader} bean (e.g. lazy loading, multi-source
 * merging, event-driven reload, etc.).
 */
public interface DynamicToolLoader {

    void onStartup();
}
