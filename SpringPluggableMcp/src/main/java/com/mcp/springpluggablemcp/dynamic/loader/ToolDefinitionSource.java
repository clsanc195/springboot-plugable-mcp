package com.mcp.springpluggablemcp.dynamic.loader;

import com.mcp.springpluggablemcp.dynamic.mapping.DynamicToolRecord;

import java.time.Instant;
import java.util.List;

public interface ToolDefinitionSource {

    List<DynamicToolRecord> loadAll();

    default List<DynamicToolRecord> loadSince(Instant since) {
        return List.of();
    }
}
