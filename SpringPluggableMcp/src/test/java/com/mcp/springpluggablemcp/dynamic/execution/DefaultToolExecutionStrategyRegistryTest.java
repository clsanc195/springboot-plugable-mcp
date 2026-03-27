package com.mcp.springpluggablemcp.dynamic.execution;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultToolExecutionStrategyRegistryTest {

    private final ToolExecutionStrategy echoStrategy = new ToolExecutionStrategy() {
        @Override public String getType() { return "echo"; }
        @Override public String execute(String toolInput, String executorConfig) { return "echo:" + toolInput; }
    };

    private final ToolExecutionStrategy httpStrategy = new ToolExecutionStrategy() {
        @Override public String getType() { return "http"; }
        @Override public String execute(String toolInput, String executorConfig) { return "http:" + toolInput; }
    };

    @Test
    void resolvesStrategyByType() {
        var registry = new DefaultToolExecutionStrategyRegistry(List.of(echoStrategy, httpStrategy));
        assertSame(echoStrategy, registry.resolve("echo"));
        assertSame(httpStrategy, registry.resolve("http"));
    }

    @Test
    void throwsForUnknownType() {
        var registry = new DefaultToolExecutionStrategyRegistry(List.of(echoStrategy));
        var ex = assertThrows(IllegalArgumentException.class, () -> registry.resolve("unknown"));
        assertTrue(ex.getMessage().contains("unknown"));
        assertTrue(ex.getMessage().contains("echo"));
    }

    @Test
    void listsAvailableTypes() {
        var registry = new DefaultToolExecutionStrategyRegistry(List.of(echoStrategy, httpStrategy));
        var types = registry.availableTypes();
        assertEquals(2, types.size());
        assertTrue(types.contains("echo"));
        assertTrue(types.contains("http"));
    }

    @Test
    void emptyRegistryThrowsForAnyType() {
        var registry = new DefaultToolExecutionStrategyRegistry(List.of());
        assertThrows(IllegalArgumentException.class, () -> registry.resolve("echo"));
        assertTrue(registry.availableTypes().isEmpty());
    }

    @Test
    void duplicateTypeLastOneWins() {
        var echo2 = new ToolExecutionStrategy() {
            @Override public String getType() { return "echo"; }
            @Override public String execute(String i, String c) { return "echo2:" + i; }
        };
        // Collectors.toMap throws on duplicate keys by default
        assertThrows(IllegalStateException.class,
                () -> new DefaultToolExecutionStrategyRegistry(List.of(echoStrategy, echo2)));
    }
}
