package com.mcp.springpluggablemcp.dynamic.execution;

public interface ToolExecutionStrategy {

    String getType();

    String execute(String toolInput, String executorConfig);
}
