package com.mcp.springpluggablemcp.dynamic.execution;

/**
 * Defines how a dynamic tool executes when called by an MCP client.
 * Implement this as a {@code @Component} — the registry auto-discovers it.
 * <p>
 * The {@link #getType()} value must match the {@code executorType} field
 * in the tool's {@code DynamicToolRecord}. When a tool is called, the
 * loader resolves the strategy by type and calls {@link #execute}.
 */
public interface ToolExecutionStrategy {

    /**
     * The type identifier for this strategy (e.g. "http", "echo").
     * Must match the {@code executorType} in tool records that should
     * be handled by this strategy.
     */
    String getType();

    /**
     * Execute the tool.
     *
     * @param toolInput      JSON string with the arguments the MCP client sent
     * @param executorConfig JSON string from the tool definition — per-tool
     *                       configuration set at registration time
     * @return the result as a string (typically JSON)
     */
    String execute(String toolInput, String executorConfig);
}
