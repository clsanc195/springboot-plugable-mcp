package com.mcp.springpluggablemcp.dynamic.loader;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks the current state of dynamic tool loading. Populated by the
 * {@link DynamicToolLoader} implementation and read by the Actuator endpoint.
 */
public class DynamicToolStatus {

    public record RegisteredTool(String name, String executorType, String source, Instant registeredAt) {}
    public record SourceStatus(String name, String state, int toolCount, int retryAttempts, Instant lastLoadTime) {}

    private final List<RegisteredTool> tools = new CopyOnWriteArrayList<>();
    private final Map<String, SourceStatus> sources = new ConcurrentHashMap<>();

    public void toolRegistered(String name, String executorType, String source) {
        tools.removeIf(t -> t.name().equals(name));
        tools.add(new RegisteredTool(name, executorType, source, Instant.now()));
    }

    public void sourceLoaded(String sourceName, int toolCount) {
        sources.put(sourceName, new SourceStatus(sourceName, "healthy", toolCount, 0, Instant.now()));
    }

    public void sourceRetrying(String sourceName, int attempt) {
        var existing = sources.get(sourceName);
        sources.put(sourceName, new SourceStatus(sourceName, "retrying",
                existing != null ? existing.toolCount() : 0, attempt, Instant.now()));
    }

    public void sourceGaveUp(String sourceName, int attempts) {
        var existing = sources.get(sourceName);
        sources.put(sourceName, new SourceStatus(sourceName, "gave_up",
                existing != null ? existing.toolCount() : 0, attempts, Instant.now()));
    }

    public List<RegisteredTool> getTools() {
        return List.copyOf(tools);
    }

    public Map<String, SourceStatus> getSources() {
        return Map.copyOf(sources);
    }

    public int totalToolCount() {
        return tools.size();
    }
}
