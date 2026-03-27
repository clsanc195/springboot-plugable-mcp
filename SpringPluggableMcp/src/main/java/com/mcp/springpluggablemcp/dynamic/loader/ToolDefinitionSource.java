package com.mcp.springpluggablemcp.dynamic.loader;

import com.mcp.springpluggablemcp.dynamic.mapping.DynamicToolRecord;

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
}
