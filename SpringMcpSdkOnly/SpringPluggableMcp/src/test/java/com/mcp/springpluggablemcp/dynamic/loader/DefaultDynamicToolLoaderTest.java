package com.mcp.springpluggablemcp.dynamic.loader;

import com.mcp.springpluggablemcp.dynamic.config.DynamicToolProperties;
import com.mcp.springpluggablemcp.dynamic.execution.ToolExecutionStrategy;
import com.mcp.springpluggablemcp.dynamic.execution.DefaultToolExecutionStrategyRegistry;
import com.mcp.springpluggablemcp.dynamic.execution.ToolExecutionStrategyRegistry;
import com.mcp.springpluggablemcp.dynamic.mapping.DynamicToolRecord;
import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DefaultDynamicToolLoaderTest {

    private McpSyncServer mcpSyncServer;
    private ToolExecutionStrategyRegistry registry;
    private DynamicToolProperties properties;
    private DynamicToolStatus status;
    private org.springframework.scheduling.TaskScheduler taskScheduler;

    @BeforeEach
    void setUp() {
        mcpSyncServer = mock(McpSyncServer.class);
        taskScheduler = mock(org.springframework.scheduling.TaskScheduler.class);
        properties = new DynamicToolProperties(null, new DynamicToolProperties.Refresh(false, null), null);
        status = new DynamicToolStatus();

        ToolExecutionStrategy echoStrategy = new ToolExecutionStrategy() {
            @Override public String getType() { return "echo"; }
            @Override public String execute(String i, String c) { return i; }
        };
        registry = new DefaultToolExecutionStrategyRegistry(List.of(echoStrategy));
    }

    private DynamicToolRecord tool(String name) {
        return new DynamicToolRecord(name, "desc", "{\"type\":\"object\",\"properties\":{}}", "echo", "{}");
    }

    @Test
    void loadsToolsFromSingleSource() {
        ToolDefinitionSource source = () -> List.of(tool("tool_a"), tool("tool_b"));
        var loader = new DefaultDynamicToolLoader(List.of(source), registry, mcpSyncServer, properties, taskScheduler, status);

        loader.onStartup();

        verify(mcpSyncServer, times(2)).addTool(any());
    }

    @Test
    void loadsToolsFromMultipleSources() {
        ToolDefinitionSource source1 = () -> List.of(tool("from_source_1"));
        ToolDefinitionSource source2 = () -> List.of(tool("from_source_2"), tool("another_from_2"));
        var loader = new DefaultDynamicToolLoader(List.of(source1, source2), registry, mcpSyncServer, properties, taskScheduler, status);

        loader.onStartup();

        verify(mcpSyncServer, times(3)).addTool(any());
    }

    @Test
    void failingSourceDoesNotBlockOthers() {
        ToolDefinitionSource failingSource = () -> { throw new RuntimeException("DB is down"); };
        ToolDefinitionSource workingSource = () -> List.of(tool("healthy_tool"));
        var loader = new DefaultDynamicToolLoader(List.of(failingSource, workingSource), registry, mcpSyncServer, properties, taskScheduler, status);

        loader.onStartup();

        verify(mcpSyncServer, times(1)).addTool(any());
    }

    @Test
    void failingToolRegistrationDoesNotBlockOthers() {
        ToolDefinitionSource source = () -> List.of(
                new DynamicToolRecord("bad_tool", "desc", "{}", "nonexistent_type", "{}"),
                tool("good_tool")
        );
        var loader = new DefaultDynamicToolLoader(List.of(source), registry, mcpSyncServer, properties, taskScheduler, status);

        loader.onStartup();

        // bad_tool fails (no strategy for "nonexistent_type"), good_tool succeeds
        verify(mcpSyncServer, times(1)).addTool(any());
    }

    @Test
    void duplicateToolNameFromSameSourceOverrides() {
        ToolDefinitionSource source = () -> List.of(tool("dupe"), tool("dupe"));
        var loader = new DefaultDynamicToolLoader(List.of(source), registry, mcpSyncServer, properties, taskScheduler, status);

        loader.onStartup();

        // First registers, second removes + re-registers
        verify(mcpSyncServer, times(1)).removeTool("dupe");
        verify(mcpSyncServer, times(2)).addTool(any());
    }

    @Test
    void emptySourceIsHandledGracefully() {
        ToolDefinitionSource empty = List::of;
        var loader = new DefaultDynamicToolLoader(List.of(empty), registry, mcpSyncServer, properties, taskScheduler, status);

        loader.onStartup();

        verify(mcpSyncServer, never()).addTool(any());
    }

    @Test
    void schedulesRefreshWhenEnabled() {
        var refreshProps = new DynamicToolProperties(null, new DynamicToolProperties.Refresh(true, Duration.ofMinutes(5)), null);
        ToolDefinitionSource source = List::of;
        var loader = new DefaultDynamicToolLoader(List.of(source), registry, mcpSyncServer, refreshProps, taskScheduler, status);

        loader.onStartup();

        verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofMinutes(5)));
    }

    @Test
    void doesNotScheduleRefreshWhenDisabled() {
        ToolDefinitionSource source = List::of;
        var loader = new DefaultDynamicToolLoader(List.of(source), registry, mcpSyncServer, properties, taskScheduler, status);

        loader.onStartup();

        verify(taskScheduler, never()).scheduleWithFixedDelay(any(), any(Duration.class));
    }

    @Test
    void statusTracksRegisteredTools() {
        ToolDefinitionSource source = () -> List.of(tool("tool_x"), tool("tool_y"));
        var loader = new DefaultDynamicToolLoader(List.of(source), registry, mcpSyncServer, properties, taskScheduler, status);

        loader.onStartup();

        assertEquals(2, status.totalToolCount());
        assertEquals(2, status.getTools().size());
        assertEquals("tool_x", status.getTools().get(0).name());
        assertEquals(1, status.getSources().size());
        assertEquals("healthy", status.getSources().values().iterator().next().state());
    }

    @Test
    void hangingSourceTimesOutWithoutBlockingOthers() {
        ToolDefinitionSource hangingSource = () -> {
            try { Thread.sleep(60_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return List.of();
        };
        ToolDefinitionSource fastSource = () -> List.of(tool("fast_tool"));
        var loader = new DefaultDynamicToolLoader(List.of(hangingSource, fastSource), registry, mcpSyncServer, properties, taskScheduler, status);

        long start = System.currentTimeMillis();
        loader.onStartup();
        long elapsed = System.currentTimeMillis() - start;

        // Should complete well under 60s (the timeout is 30s, fast source is instant)
        assertTrue(elapsed < 35_000, "Should have timed out, took " + elapsed + "ms");
        verify(mcpSyncServer, times(1)).addTool(any());
    }
}
