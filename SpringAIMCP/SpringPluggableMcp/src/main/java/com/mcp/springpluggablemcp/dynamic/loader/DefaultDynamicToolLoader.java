package com.mcp.springpluggablemcp.dynamic.loader;

import com.mcp.springpluggablemcp.dynamic.config.DynamicToolProperties;
import com.mcp.springpluggablemcp.dynamic.execution.DynamicToolCallback;
import com.mcp.springpluggablemcp.dynamic.execution.ToolExecutionStrategyRegistry;
import com.mcp.springpluggablemcp.dynamic.mapping.DynamicToolRecord;
import io.modelcontextprotocol.server.McpSyncServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultDynamicToolLoader implements DynamicToolLoader, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DefaultDynamicToolLoader.class);

    private final List<ToolDefinitionSource> sources;
    private final ToolExecutionStrategyRegistry strategyRegistry;
    private final McpSyncServer mcpSyncServer;
    private final DynamicToolProperties properties;
    private final TaskScheduler taskScheduler;
    private final DynamicToolStatus status;
    private final ExecutorService sourceExecutor = Executors.newCachedThreadPool();

    private final Map<String, String> toolOwnership = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastReadTimes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> retryCounters = new ConcurrentHashMap<>();
    private final ReentrantLock registrationLock = new ReentrantLock();

    public DefaultDynamicToolLoader(List<ToolDefinitionSource> sources,
                                    ToolExecutionStrategyRegistry strategyRegistry,
                                    McpSyncServer mcpSyncServer,
                                    DynamicToolProperties properties,
                                    TaskScheduler taskScheduler,
                                    DynamicToolStatus status) {
        this.sources = sources;
        this.strategyRegistry = strategyRegistry;
        this.mcpSyncServer = mcpSyncServer;
        this.properties = properties;
        this.taskScheduler = taskScheduler;
        this.status = status;
    }

    @Override
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        // Load all sources concurrently
        var futures = sources.stream()
                .map(source -> sourceExecutor.submit(() -> {
                    loadAllFromSource(source);
                    return source;
                }))
                .toList();

        // Wait for all to complete, then schedule refresh for each
        for (var future : futures) {
            try {
                var source = future.get();
                scheduleRefresh(source);
            } catch (Exception e) {
                log.error("Unexpected error during parallel source loading: {}", e.getMessage());
            }
        }
    }

    @Override
    public void destroy() {
        sourceExecutor.shutdownNow();
        log.info("Dynamic tool loader shut down");
    }

    private void scheduleRefresh(ToolDefinitionSource source) {
        String sourceName = source.getClass().getSimpleName();

        Duration interval = source.refreshInterval();

        if (interval == null && properties.refresh() != null && properties.refresh().enabled()) {
            interval = properties.refresh().interval();
        }

        if (interval != null) {
            taskScheduler.scheduleWithFixedDelay(() -> refreshFromSource(source), interval);
            log.info("[{}] Refresh scheduled every {}", sourceName, interval);
        }
    }

    private void loadAllFromSource(ToolDefinitionSource source) {
        String sourceName = source.getClass().getSimpleName();
        try {
            lastReadTimes.put(sourceName, Instant.now());
            var records = callWithTimeout(() -> source.loadAll(), source);
            if (records == null) return;
            log.info("[{}] Loaded {} dynamic tool definitions", sourceName, records.size());
            int registered = 0;
            for (var record : records) {
                if (registerTool(record, sourceName)) registered++;
            }
            retryCounters.remove(sourceName);
            status.sourceLoaded(sourceName, registered);
        } catch (Exception e) {
            log.warn("[{}] Failed to load tools at startup. Will retry on next refresh cycle. Cause: {}",
                    sourceName, e.getMessage());
        }
    }

    private void refreshFromSource(ToolDefinitionSource source) {
        String sourceName = source.getClass().getSimpleName();
        int maxRetries = source.maxRetryAttempts();
        try {
            if (!retryCounters.containsKey(sourceName) && lastReadTimes.containsKey(sourceName)
                    && toolOwnership.containsValue(sourceName)) {
                var since = lastReadTimes.getOrDefault(sourceName, Instant.now());
                lastReadTimes.put(sourceName, Instant.now());

                var deltas = callWithTimeout(() -> source.loadSince(since), source);
                if (deltas == null || deltas.isEmpty()) {
                    log.debug("[{}] No new tool definitions since {}", sourceName, since);
                    return;
                }

                log.info("[{}] Found {} new/updated tool definitions", sourceName, deltas.size());
                for (var record : deltas) {
                    registerTool(record, sourceName);
                }
            } else {
                AtomicInteger counter = retryCounters.computeIfAbsent(sourceName, k -> new AtomicInteger(0));
                int attempt = counter.incrementAndGet();

                if (attempt > maxRetries) {
                    log.error("[{}] Giving up after {} failed attempts. Source will not be retried until restart.",
                            sourceName, maxRetries);
                    status.sourceGaveUp(sourceName, maxRetries);
                    return;
                }

                log.info("[{}] Retrying initial full load (attempt {}/{})...",
                        sourceName, attempt, maxRetries);
                status.sourceRetrying(sourceName, attempt);
                loadAllFromSource(source);
            }
        } catch (Exception e) {
            log.error("[{}] Failed to refresh tools: {}", sourceName, e.getMessage());
        }
    }

    private <T> T callWithTimeout(Callable<T> task, ToolDefinitionSource source) {
        String sourceName = source.getClass().getSimpleName();
        Duration timeout = source.sourceTimeout();
        Future<T> future = sourceExecutor.submit(task);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("[{}] Timed out after {}s", sourceName, timeout.toSeconds());
            return null;
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[{}] Interrupted", sourceName);
            return null;
        }
    }

    private boolean registerTool(DynamicToolRecord record, String sourceName) {
        registrationLock.lock();
        try {
            String existingOwner = toolOwnership.get(record.name());
            if (existingOwner != null) {
                if (!existingOwner.equals(sourceName)) {
                    log.warn("[{}] Tool '{}' is already registered by [{}] — overriding",
                            sourceName, record.name(), existingOwner);
                }
                mcpSyncServer.removeTool(record.name());
            }

            var strategy = strategyRegistry.resolve(record.executorType());
            var callback = new DynamicToolCallback(record, strategy);
            var spec = McpToolUtils.toSyncToolSpecification(callback);
            mcpSyncServer.addTool(spec);
            toolOwnership.put(record.name(), sourceName);
            status.toolRegistered(record.name(), record.executorType(), sourceName);
            log.info("[{}] Registered dynamic tool: {} (executor: {})",
                    sourceName, record.name(), record.executorType());
            return true;
        } catch (Exception e) {
            log.error("[{}] Failed to register tool '{}': {}",
                    sourceName, record.name(), e.getMessage());
            return false;
        } finally {
            registrationLock.unlock();
        }
    }
}
