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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultDynamicToolLoader implements DynamicToolLoader {

    private static final Logger log = LoggerFactory.getLogger(DefaultDynamicToolLoader.class);
    private static final int MAX_RETRY_ATTEMPTS = 10;
    private static final Duration SOURCE_TIMEOUT = Duration.ofSeconds(30);

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
            var records = callWithTimeout(() -> source.loadAll(), sourceName, "loadAll");
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

    private void refreshTools() {
        for (ToolDefinitionSource source : sources) {
            refreshFromSource(source);
        }
    }

    private void refreshFromSource(ToolDefinitionSource source) {
        String sourceName = source.getClass().getSimpleName();
        try {
            if (!retryCounters.containsKey(sourceName) && lastReadTimes.containsKey(sourceName)
                    && toolOwnership.containsValue(sourceName)) {
                var since = lastReadTimes.getOrDefault(sourceName, Instant.now());
                lastReadTimes.put(sourceName, Instant.now());

                var deltas = callWithTimeout(() -> source.loadSince(since), sourceName, "loadSince");
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

                if (attempt > MAX_RETRY_ATTEMPTS) {
                    log.error("[{}] Giving up after {} failed attempts. Source will not be retried until restart.",
                            sourceName, MAX_RETRY_ATTEMPTS);
                    status.sourceGaveUp(sourceName, MAX_RETRY_ATTEMPTS);
                    return;
                }

                log.info("[{}] Retrying initial full load (attempt {}/{})...",
                        sourceName, attempt, MAX_RETRY_ATTEMPTS);
                status.sourceRetrying(sourceName, attempt);
                loadAllFromSource(source);
            }
        } catch (Exception e) {
            log.error("[{}] Failed to refresh tools: {}", sourceName, e.getMessage());
        }
    }

    private <T> T callWithTimeout(Callable<T> task, String sourceName, String operation) {
        Future<T> future = sourceExecutor.submit(task);
        try {
            return future.get(SOURCE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("[{}] {} timed out after {}s", sourceName, operation, SOURCE_TIMEOUT.toSeconds());
            return null;
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[{}] {} was interrupted", sourceName, operation);
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
