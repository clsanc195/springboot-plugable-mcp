package com.mcp.springpluggablemcp.dynamic.mapping;

public record DynamicToolRecord(
        String name,
        String description,
        String inputSchema,
        String executorType,
        String executorConfig
) {}
