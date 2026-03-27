package com.mcp.mcphostapp.mcp.execution;

import com.mcp.springpluggablemcp.dynamic.execution.ToolExecutionStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;

@Component
public class HttpStrategy implements ToolExecutionStrategy {

    private static final Logger log = LoggerFactory.getLogger(HttpStrategy.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpStrategy(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String getType() {
        return "http";
    }

    @Override
    public String execute(String toolInput, String executorConfig) {
        try {
            JsonNode config = objectMapper.readTree(executorConfig);
            JsonNode input = objectMapper.readTree(toolInput);

            String url = resolveUrl(config, input);
            String method = config.has("method") ? config.get("method").asText() : "GET";
            Duration timeout = config.has("timeoutSeconds")
                    ? Duration.ofSeconds(config.get("timeoutSeconds").asLong())
                    : Duration.ofSeconds(30);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout);

            if (config.has("headers")) {
                config.get("headers").fields().forEachRemaining(entry ->
                        requestBuilder.header(entry.getKey(), entry.getValue().asText()));
            }

            String body = resolveBody(config, input, method);
            requestBuilder.method(method.toUpperCase(), body != null
                    ? HttpRequest.BodyPublishers.ofString(body)
                    : HttpRequest.BodyPublishers.noBody());

            if (body != null && !config.has("headers")
                    || (config.has("headers") && !config.get("headers").has("Content-Type"))) {
                requestBuilder.header("Content-Type", "application/json");
            }

            log.info("Executing HTTP {} {}", method, url);
            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            return objectMapper.writeValueAsString(Map.of(
                    "status", response.statusCode(),
                    "body", parseBodySafe(response.body())
            ));

        } catch (Exception e) {
            log.error("HTTP strategy execution failed: {}", e.getMessage());
            return "{\"error\": \"" + e.getMessage().replace("\"", "\\\"") + "\"}";
        }
    }

    private String resolveUrl(JsonNode config, JsonNode input) {
        String url = config.get("url").asText();
        Iterator<Map.Entry<String, JsonNode>> fields = input.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            url = url.replace("{" + field.getKey() + "}", field.getValue().asText());
        }
        return url;
    }

    private String resolveBody(JsonNode config, JsonNode input, String method) {
        if ("GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            return null;
        }
        if (config.has("bodyTemplate")) {
            String template = config.get("bodyTemplate").toString();
            Iterator<Map.Entry<String, JsonNode>> fields = input.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                template = template.replace("\"{" + field.getKey() + "}\"", field.getValue().toString());
                template = template.replace("{" + field.getKey() + "}", field.getValue().asText());
            }
            return template;
        }
        return input.toString();
    }

    private Object parseBodySafe(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            return body;
        }
    }
}
