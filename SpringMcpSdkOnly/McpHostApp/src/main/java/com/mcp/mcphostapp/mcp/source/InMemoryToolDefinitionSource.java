package com.mcp.mcphostapp.mcp.source;

import com.mcp.springpluggablemcp.dynamic.loader.ToolDefinitionSource;
import com.mcp.springpluggablemcp.dynamic.mapping.DynamicToolRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Loads tool definitions from an in-memory list. Demonstrates a
 * non-database ToolDefinitionSource that coexists with other sources.
 * <p>
 * In a real scenario this could load from a REST API, a YAML config
 * file, a service registry, etc.
 */
@Component
public class InMemoryToolDefinitionSource implements ToolDefinitionSource {

    private static final Logger log = LoggerFactory.getLogger(InMemoryToolDefinitionSource.class);

    @Override
    public List<DynamicToolRecord> loadAll() {
        log.info("Loading in-memory tool definitions");

        return List.of(
                new DynamicToolRecord(
                        "server_info",
                        "Return basic information about this MCP server",
                        """
                        {"type":"object","properties":{}}
                        """,
                        "echo",
                        "{\"message\":\"MCP Host App v1.0.0 — running with SpringPluggableMcp\"}"
                ),
                new DynamicToolRecord(
                        "random_number",
                        "Generate a random number between a min and max value",
                        """
                        {"type":"object","properties":{"min":{"type":"integer","description":"Minimum value (inclusive)"},"max":{"type":"integer","description":"Maximum value (inclusive)"}},"required":["min","max"]}
                        """,
                        "echo",
                        "{\"note\":\"This is a demo — the echo strategy just returns the input\"}"
                )
        );
    }
}
