package com.mcp.mcphostapp.mcp.execution;

import com.mcp.springpluggablemcp.dynamic.execution.ToolExecutionStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class IdaasHttpStrategy implements ToolExecutionStrategy {

    private static final Logger log = LoggerFactory.getLogger(IdaasHttpStrategy.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public IdaasHttpStrategy(
            @Value("${idaas.base-url:https://jsonplaceholder.typicode.com}") String baseUrl,
            @Value("${idaas.api-key:demo-key}") String apiKey,
            ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Api-Key", apiKey)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String getType() {
        return "http_idaas";
    }

    @Override
    public String execute(String toolInput, String executorConfig) {
        try {
            JsonNode config = objectMapper.readTree(executorConfig);
            JsonNode input = objectMapper.readTree(toolInput);

            String path = ToolExecutionUtils.resolvePlaceholders(config.get("path").asText(), input);
            String method = config.has("method") ? config.get("method").asText() : "GET";

            log.info("IDaaS {} {}", method, path);

            String responseBody = switch (method.toUpperCase()) {
                case "GET" -> restClient.get()
                        .uri(path)
                        .retrieve()
                        .body(String.class);
                case "POST" -> restClient.post()
                        .uri(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(ToolExecutionUtils.resolveBodyTemplate(config, input))
                        .retrieve()
                        .body(String.class);
                case "PUT" -> restClient.put()
                        .uri(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(ToolExecutionUtils.resolveBodyTemplate(config, input))
                        .retrieve()
                        .body(String.class);
                case "DELETE" -> restClient.delete()
                        .uri(path)
                        .retrieve()
                        .body(String.class);
                default -> throw new IllegalArgumentException("Unsupported method: " + method);
            };

            return objectMapper.writeValueAsString(Map.of(
                    "source", "idaas",
                    "data", ToolExecutionUtils.parseBodySafe(responseBody, objectMapper)
            ));

        } catch (Exception e) {
            log.error("IDaaS execution failed: {}", e.getMessage());
            return ToolExecutionUtils.errorJson(e, objectMapper);
        }
    }
}
