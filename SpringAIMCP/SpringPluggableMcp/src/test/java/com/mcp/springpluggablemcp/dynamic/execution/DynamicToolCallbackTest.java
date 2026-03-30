package com.mcp.springpluggablemcp.dynamic.execution;

import com.mcp.springpluggablemcp.dynamic.mapping.DynamicToolRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DynamicToolCallbackTest {

    @Test
    void callDelegatesToStrategy() {
        var record = new DynamicToolRecord(
                "test_tool", "A test tool",
                "{\"type\":\"object\",\"properties\":{}}",
                "mock", "{\"key\":\"value\"}"
        );

        ToolExecutionStrategy strategy = new ToolExecutionStrategy() {
            @Override public String getType() { return "mock"; }
            @Override public String execute(String toolInput, String executorConfig) {
                return "input=" + toolInput + " config=" + executorConfig;
            }
        };

        var callback = new DynamicToolCallback(record, strategy);

        assertEquals("test_tool", callback.getToolDefinition().name());
        assertEquals("A test tool", callback.getToolDefinition().description());

        String result = callback.call("{\"arg\":\"hello\"}");
        assertEquals("input={\"arg\":\"hello\"} config={\"key\":\"value\"}", result);
    }

    @Test
    void toolDefinitionDoesNotExposeExecutorFields() {
        var record = new DynamicToolRecord(
                "my_tool", "desc", "{}", "secret_type", "{\"secret\":true}"
        );
        ToolExecutionStrategy strategy = new ToolExecutionStrategy() {
            @Override public String getType() { return "noop"; }
            @Override public String execute(String i, String c) { return "ok"; }
        };

        var callback = new DynamicToolCallback(record, strategy);
        var def = callback.getToolDefinition();

        assertEquals("my_tool", def.name());
        assertEquals("desc", def.description());
        // executorType and executorConfig must NOT appear in the tool definition
        assertFalse(def.name().contains("secret"));
    }
}
