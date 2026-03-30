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

            String url = ToolExecutionUtils.resolvePlaceholders(config.get("url").asText(), input);
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

            boolean hasBody = !"GET".equalsIgnoreCase(method) && !"DELETE".equalsIgnoreCase(method);
            String body = hasBody ? ToolExecutionUtils.resolveBodyTemplate(config, input) : null;

            requestBuilder.method(method.toUpperCase(), body != null
                    ? HttpRequest.BodyPublishers.ofString(body)
                    : HttpRequest.BodyPublishers.noBody());

            if (body != null && (!config.has("headers")
                    || !config.get("headers").has("Content-Type"))) {
                requestBuilder.header("Content-Type", "application/json");
            }

            log.info("Executing HTTP {} {}", method, url);
            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            return objectMapper.writeValueAsString(Map.of(
                    "status", response.statusCode(),
                    "body", ToolExecutionUtils.parseBodySafe(response.body(), objectMapper)
            ));

        } catch (Exception e) {
            log.error("HTTP strategy execution failed: {}", e.getMessage());
            return ToolExecutionUtils.errorJson(e, objectMapper);
        }
    }
}
