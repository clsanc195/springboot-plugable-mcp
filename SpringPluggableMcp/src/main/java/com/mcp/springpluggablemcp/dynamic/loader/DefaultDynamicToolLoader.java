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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultDynamicToolLoader implements DynamicToolLoader {

    private static final Logger log = LoggerFactory.getLogger(DefaultDynamicToolLoader.class);

    private final List<ToolDefinitionSource> sources;
    private final ToolExecutionStrategyRegistry strategyRegistry;
    private final McpSyncServer mcpSyncServer;
    private final DynamicToolProperties properties;
    private final TaskScheduler taskScheduler;

    private final Set<String> registeredTools = ConcurrentHashMap.newKeySet();
    private final Map<String, Instant> lastReadTimes = new ConcurrentHashMap<>();
    private final Set<String> sourcesWithSuccessfulInitialLoad = ConcurrentHashMap.newKeySet();

    public DefaultDynamicToolLoader(List<ToolDefinitionSource> sources,
                                    ToolExecutionStrategyRegistry strategyRegistry,
                                    McpSyncServer mcpSyncServer,
                                    DynamicToolProperties properties,
                                    TaskScheduler taskScheduler) {
        this.sources = sources;
        this.strategyRegistry = strategyRegistry;
        this.mcpSyncServer = mcpSyncServer;
        this.properties = properties;
        this.taskScheduler = taskScheduler;
    }

    @Override
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        for (ToolDefinitionSource source : sources) {
            loadAllFromSource(source);
        }

        if (properties.refresh() != null && properties.refresh().enabled()) {
            var interval = properties.refresh().interval();
            taskScheduler.scheduleWithFixedDelay(this::refreshTools, interval);
            log.info("Dynamic tool refresh enabled, polling every {}", interval);
        }
    }

    private void loadAllFromSource(ToolDefinitionSource source) {
        String sourceName = source.getClass().getSimpleName();
        try {
            lastReadTimes.put(sourceName, Instant.now());
            var records = source.loadAll();
            log.info("[{}] Loaded {} dynamic tool definitions", sourceName, records.size());
            records.forEach(record -> registerTool(record, sourceName));
            sourcesWithSuccessfulInitialLoad.add(sourceName);
        } catch (Exception e) {
            log.warn("[{}] Failed to load tools at startup. Will retry on next refresh cycle. Cause: {}",
                    sourceName, e.getMessage());
        }
    }

    private void refreshTools() {
        for (ToolDefinitionSource source : sources) {
            refreshFromSource(source);
        }
    }

    private void refreshFromSource(ToolDefinitionSource source) {
        String sourceName = source.getClass().getSimpleName();
        try {
            if (!sourcesWithSuccessfulInitialLoad.contains(sourceName)) {
                log.info("[{}] Retrying initial full load...", sourceName);
                loadAllFromSource(source);
                return;
            }

            var since = lastReadTimes.getOrDefault(sourceName, Instant.now());
            lastReadTimes.put(sourceName, Instant.now());

            var deltas = source.loadSince(since);
            if (deltas.isEmpty()) {
                log.debug("[{}] No new tool definitions since {}", sourceName, since);
                return;
            }

            log.info("[{}] Found {} new/updated tool definitions", sourceName, deltas.size());
            for (var record : deltas) {
                if (registeredTools.contains(record.name())) {
                    mcpSyncServer.removeTool(record.name());
                    log.info("[{}] Removed outdated tool for re-registration: {}", sourceName, record.name());
                }
                registerTool(record, sourceName);
            }
        } catch (Exception e) {
            log.error("[{}] Failed to refresh tools: {}", sourceName, e.getMessage());
        }
    }

    private void registerTool(DynamicToolRecord record, String sourceName) {
        try {
            var strategy = strategyRegistry.resolve(record.executorType());
            var callback = new DynamicToolCallback(record, strategy);
            var spec = McpToolUtils.toSyncToolSpecification(callback);
            mcpSyncServer.addTool(spec);
            registeredTools.add(record.name());
            log.info("[{}] Registered dynamic tool: {} (executor: {})",
                    sourceName, record.name(), record.executorType());
        } catch (Exception e) {
            log.error("[{}] Failed to register tool '{}': {}",
                    sourceName, record.name(), e.getMessage());
        }
    }
}
