package com.mcp.springpluggablemcp.dynamic.loader;

import com.mcp.springpluggablemcp.dynamic.mapping.DynamicToolRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolDefinitionSourceTest {

    @Test
    void loadSinceDefaultsToEmptyList() {
        ToolDefinitionSource source = () -> List.of(
                new DynamicToolRecord("tool1", "desc", "{}", "echo", null)
        );

        // Default loadSince returns empty — no delta support unless overridden
        List<DynamicToolRecord> deltas = source.loadSince(Instant.now());
        assertTrue(deltas.isEmpty());
    }

    @Test
    void loadAllReturnsRecords() {
        ToolDefinitionSource source = () -> List.of(
                new DynamicToolRecord("tool1", "desc1", "{}", "echo", null),
                new DynamicToolRecord("tool2", "desc2", "{}", "http", "{\"url\":\"http://example.com\"}")
        );

        List<DynamicToolRecord> records = source.loadAll();
        assertEquals(2, records.size());
        assertEquals("tool1", records.get(0).name());
        assertEquals("http", records.get(1).executorType());
    }
}
