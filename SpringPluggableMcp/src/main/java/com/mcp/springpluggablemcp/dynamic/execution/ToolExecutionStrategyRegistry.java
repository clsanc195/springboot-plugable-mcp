package com.mcp.springpluggablemcp.dynamic.execution;

import java.util.List;

public interface ToolExecutionStrategyRegistry {

    ToolExecutionStrategy resolve(String type);

    List<String> availableTypes();
}
