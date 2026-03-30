package com.mcp.springpluggablemcp.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Converts the library's {@link ToolCallback} into an MCP SDK
 * {@link McpServerFeatures.SyncToolSpecification} that can be
 * registered directly with {@link io.modelcontextprotocol.server.McpSyncServer}.
 * <p>
 * Replaces Spring AI's {@code McpToolUtils} with a zero-Spring-AI
 * equivalent that depends only on the MCP Java SDK.
 */
public final class McpToolUtils {

    private static final Logger log = LoggerFactory.getLogger(McpToolUtils.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpToolUtils() {}

    /**
     * Build an MCP SDK tool specification from a library {@link ToolCallback}.
     */
    public static McpServerFeatures.SyncToolSpecification toSyncToolSpecification(ToolCallback callback) {
        ToolDefinition def = callback.getToolDefinition();
        McpSchema.Tool tool = buildTool(def);

        return new McpServerFeatures.SyncToolSpecification(
                tool,
                (exchange, arguments) -> {
                    try {
                        String input = MAPPER.writeValueAsString(arguments);
                        String result = callback.call(input);
                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(result)), false);
                    } catch (Exception e) {
                        log.error("Tool '{}' execution failed: {}", def.name(), e.getMessage(), e);
                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent("Error: " + e.getMessage())), true);
                    }
                }
        );
    }

    private static McpSchema.Tool buildTool(ToolDefinition def) {
        McpSchema.JsonSchema jsonSchema = parseInputSchema(def.inputSchema());
        return new McpSchema.Tool(
                def.name(),
                null,
                def.description(),
                jsonSchema,
                null,
                null,
                null
        );
    }

    /**
     * Parse a JSON Schema string into the MCP SDK's {@link McpSchema.JsonSchema} record.
     */
    static McpSchema.JsonSchema parseInputSchema(String inputSchemaJson) {
        if (inputSchemaJson == null || inputSchemaJson.isBlank()) {
            return new McpSchema.JsonSchema("object", Map.of(), List.of(), null, null, null);
        }
        try {
            Map<String, Object> raw = MAPPER.readValue(inputSchemaJson, new TypeReference<>() {});
            String type = (String) raw.getOrDefault("type", "object");

            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) raw.getOrDefault("properties", Map.of());

            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) raw.getOrDefault("required", List.of());

            Boolean additionalProperties = raw.containsKey("additionalProperties")
                    ? (Boolean) raw.get("additionalProperties") : null;

            return new McpSchema.JsonSchema(type, properties, required, additionalProperties, null, null);
        } catch (Exception e) {
            log.warn("Failed to parse input schema, using empty schema: {}", e.getMessage());
            return new McpSchema.JsonSchema("object", Map.of(), List.of(), null, null, null);
        }
    }
}
