package com.mcp.springpluggablemcp.dynamic.execution;

import java.util.List;

/**
 * Resolves a {@link ToolExecutionStrategy} by its type identifier.
 * The default implementation auto-collects all strategy beans from
 * the Spring context. Provide your own bean to customize resolution.
 */
public interface ToolExecutionStrategyRegistry {

    /**
     * Find the strategy that handles the given type.
     *
     * @param type the executor type (e.g. "http", "echo")
     * @return the matching strategy
     * @throws IllegalArgumentException if no strategy is registered for the type
     */
    ToolExecutionStrategy resolve(String type);

    /**
     * List all registered executor type identifiers.
     */
    List<String> availableTypes();
}
