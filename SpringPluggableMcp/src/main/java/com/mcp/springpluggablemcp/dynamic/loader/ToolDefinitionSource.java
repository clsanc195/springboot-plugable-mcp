package com.mcp.springpluggablemcp.dynamic.loader;

import com.mcp.springpluggablemcp.dynamic.mapping.DynamicToolRecord;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Provides dynamic tool definitions to the MCP server. Implement this
 * interface as a {@code @Component} to load tools from any source —
 * a database, REST API, config file, or hardcoded list.
 * <p>
 * Multiple sources can coexist. The loader calls each one independently.
 * If one fails, the others still load.
 */
public interface ToolDefinitionSource {

    /**
     * Load all tool definitions from this source.
     * Called on startup and on retry if the initial load failed.
     */
    List<DynamicToolRecord> loadAll();

    /**
     * Load tool definitions that changed since the given timestamp.
     * Called on each refresh cycle after a successful initial load.
     * <p>
     * Override this to support incremental refresh. The default
     * returns an empty list (no delta support).
     */
    default List<DynamicToolRecord> loadSince(Instant since) {
        return List.of();
    }

    /**
     * How often this source should be refreshed. Each source gets its
     * own independent refresh schedule.
     * <p>
     * Return {@code null} to disable refresh for this source (load
     * once at startup only). The default returns {@code null}.
     */
    default Duration refreshInterval() {
        return null;
    }

    /**
     * Maximum time to wait for {@link #loadAll()} or {@link #loadSince}
     * to complete before cancelling the call. Protects the refresh thread
     * from hanging sources.
     * <p>
     * The default is 30 seconds.
     */
    default Duration sourceTimeout() {
        return Duration.ofSeconds(30);
    }

    /**
     * How many times to retry the initial full load if it fails at startup.
     * After this many consecutive failures the source is abandoned until
     * the application restarts.
     * <p>
     * The default is 10.
     */
    default int maxRetryAttempts() {
        return 10;
    }
}
