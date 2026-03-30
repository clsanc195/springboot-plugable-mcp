package com.mcp.springpluggablemcp.dynamic.execution;

import com.mcp.springpluggablemcp.dynamic.mapping.DynamicToolRecord;
import com.mcp.springpluggablemcp.tool.ToolCallback;
import com.mcp.springpluggablemcp.tool.ToolDefinition;

public class DynamicToolCallback implements ToolCallback {

    private final ToolDefinition toolDefinition;
    private final ToolExecutionStrategy strategy;
    private final String executorConfig;

    public DynamicToolCallback(DynamicToolRecord record, ToolExecutionStrategy strategy) {
        this.toolDefinition = ToolDefinition.builder()
                .name(record.name())
                .description(record.description())
                .inputSchema(record.inputSchema())
                .build();
        this.strategy = strategy;
        this.executorConfig = record.executorConfig();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        return strategy.execute(toolInput, executorConfig);
    }
}
