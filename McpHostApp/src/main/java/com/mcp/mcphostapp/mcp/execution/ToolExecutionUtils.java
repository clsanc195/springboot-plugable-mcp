package com.mcp.mcphostapp.mcp.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Iterator;
import java.util.Map;

/**
 * Shared utilities for tool execution strategies.
 */
final class ToolExecutionUtils {

    private ToolExecutionUtils() {}

    static String resolvePlaceholders(String template, JsonNode input) {
        Iterator<Map.Entry<String, JsonNode>> fields = input.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            template = template.replace("{" + field.getKey() + "}", field.getValue().asText());
        }
        return template;
    }

    static String resolveBodyTemplate(JsonNode config, JsonNode input) {
        if (!config.has("bodyTemplate")) {
            return input.toString();
        }
        String template = config.get("bodyTemplate").toString();
        Iterator<Map.Entry<String, JsonNode>> fields = input.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            template = template.replace("\"{" + field.getKey() + "}\"", field.getValue().toString());
            template = template.replace("{" + field.getKey() + "}", field.getValue().asText());
        }
        return template;
    }

    static Object parseBodySafe(String body, ObjectMapper objectMapper) {
        if (body == null) return null;
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            return body;
        }
    }

    static String errorJson(Exception e, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        } catch (Exception ignored) {
            return "{\"error\":\"An unexpected error occurred\"}";
        }
    }
}
