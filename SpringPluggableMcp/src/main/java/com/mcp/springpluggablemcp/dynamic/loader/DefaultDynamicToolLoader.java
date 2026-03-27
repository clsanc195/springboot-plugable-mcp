package com.mcp.springpluggablemcp.dynamic.loader;

import com.mcp.springpluggablemcp.dynamic.config.DynamicToolProperties;
import com.mcp.springpluggablemcp.dynamic.execution.DynamicToolCallback;
import com.mcp.springpluggablemcp.dynamic.execution.ToolExecutionStrategyRegistry;
import com.mcp.springpluggablemcp.dynamic.mapping.DynamicToolRecord;
import io.modelcontextprotocol.server.McpSyncServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultDynamicToolLoader implements DynamicToolLoader {

    private static final Logger log = LoggerFactory.getLogger(DefaultDynamicToolLoader.class);

    private final ToolDefinitionSource source;
    private final ToolExecutionStrategyRegistry strategyRegistry;
    private final McpSyncServer mcpSyncServer;
    private final DynamicToolProperties properties;
    private final TaskScheduler taskScheduler;

    private final Set<String> registeredTools = ConcurrentHashMap.newKeySet();
    private volatile Instant lastReadTime;
    private volatile boolean initialLoadSucceeded = false;

    public DefaultDynamicToolLoader(ToolDefinitionSource source,
                                    ToolExecutionStrategyRegistry strategyRegistry,
                                    McpSyncServer mcpSyncServer,
                                    DynamicToolProperties properties,
                                    TaskScheduler taskScheduler) {
        this.source = source;
        this.strategyRegistry = strategyRegistry;
        this.mcpSyncServer = mcpSyncServer;
        this.properties = properties;
        this.taskScheduler = taskScheduler;
    }

    @Override
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            lastReadTime = Instant.now();
            var records = source.loadAll();
            log.info("Loaded {} dynamic tool definitions from source", records.size());
            records.forEach(this::registerTool);
            initialLoadSucceeded = true;
        } catch (Exception e) {
            log.warn("Dynamic tools source is not available at startup. "
                    + "Tools will be loaded on the next refresh cycle. Cause: {}", e.getMessage());
        }

        if (properties.refresh() != null && properties.refresh().enabled()) {
            var interval = properties.refresh().interval();
            taskScheduler.scheduleWithFixedDelay(this::refreshTools, interval);
            log.info("Dynamic tool refresh enabled, polling every {}", interval);
        }
    }

    private void refreshTools() {
        try {
            if (!initialLoadSucceeded) {
                log.info("Retrying initial full load of dynamic tools...");
                lastReadTime = Instant.now();
                var records = source.loadAll();
                log.info("Loaded {} dynamic tool definitions from source", records.size());
                records.forEach(this::registerTool);
                initialLoadSucceeded = true;
                return;
            }

            var since = lastReadTime;
            lastReadTime = Instant.now();

            var deltas = source.loadSince(since);
            if (deltas.isEmpty()) {
                log.debug("No new dynamic tool definitions since {}", since);
                return;
            }

            log.info("Found {} new/updated dynamic tool definitions", deltas.size());
            for (var record : deltas) {
                if (registeredTools.contains(record.name())) {
                    mcpSyncServer.removeTool(record.name());
                    log.info("Removed outdated tool for re-registration: {}", record.name());
                }
                registerTool(record);
            }
        } catch (Exception e) {
            log.error("Failed to refresh dynamic tools: {}", e.getMessage());
        }
    }

    private void registerTool(DynamicToolRecord record) {
        var strategy = strategyRegistry.resolve(record.executorType());
        var callback = new DynamicToolCallback(record, strategy);
        var spec = McpToolUtils.toSyncToolSpecification(callback);
        mcpSyncServer.addTool(spec);
        registeredTools.add(record.name());
        log.info("Registered dynamic tool: {} (executor: {})", record.name(), record.executorType());
    }
}
