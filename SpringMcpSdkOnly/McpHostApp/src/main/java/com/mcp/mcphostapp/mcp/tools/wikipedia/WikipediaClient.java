package com.mcp.mcphostapp.mcp.tools.wikipedia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class WikipediaClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WikipediaClient(ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.wikimedia.org/feed/v1/wikipedia/en")
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("User-Agent", "McpHostApp/1.0")
                .build();
        this.objectMapper = objectMapper;
    }

    public String getOnThisDay(int month, int day) throws Exception {
        String json = restClient.get()
                .uri("/onthisday/all/{month}/{day}",
                        String.format("%02d", month), String.format("%02d", day))
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(json);

        return objectMapper.writeValueAsString(Map.of(
                "date", "%02d/%02d".formatted(month, day),
                "selected", extractEntries(root, "selected", 10),
                "events", extractEntries(root, "events", 10),
                "births", extractEntries(root, "births", 10),
                "deaths", extractEntries(root, "deaths", 10),
                "holidays", extractEntries(root, "holidays", 10)
        ));
    }

    private List<String> extractEntries(JsonNode root, String section, int limit) {
        List<String> results = new ArrayList<>();
        JsonNode items = root.get(section);
        if (items == null || !items.isArray()) return results;

        for (int i = 0; i < Math.min(items.size(), limit); i++) {
            JsonNode item = items.get(i);
            String text = item.has("text") ? item.get("text").asText() : "";
            if (item.has("year")) {
                results.add(item.get("year").asText() + " - " + text);
            } else {
                results.add(text);
            }
        }
        return results;
    }
}
