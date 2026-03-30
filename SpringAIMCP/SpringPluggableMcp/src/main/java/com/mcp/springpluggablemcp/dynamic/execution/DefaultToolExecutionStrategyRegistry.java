package com.mcp.springpluggablemcp.dynamic.execution;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DefaultToolExecutionStrategyRegistry implements ToolExecutionStrategyRegistry {

    private final Map<String, ToolExecutionStrategy> strategies;

    public DefaultToolExecutionStrategyRegistry(List<ToolExecutionStrategy> strategies) {
        this.strategies = strategies.stream()
                .collect(Collectors.toMap(ToolExecutionStrategy::getType, Function.identity()));
    }

    @Override
    public ToolExecutionStrategy resolve(String type) {
        ToolExecutionStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException(
                    "No execution strategy registered for type: " + type
                            + ". Available: " + strategies.keySet());
        }
        return strategy;
    }

    @Override
    public List<String> availableTypes() {
        return List.copyOf(strategies.keySet());
    }
}
