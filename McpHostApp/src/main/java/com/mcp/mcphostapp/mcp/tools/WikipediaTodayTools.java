package com.mcp.mcphostapp.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class WikipediaTodayTools {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WikipediaTodayTools(ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.wikimedia.org/feed/v1/wikipedia/en")
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("User-Agent", "McpHostApp/1.0")
                .build();
        this.objectMapper = objectMapper;
    }

    @Tool(name = "today_in_history",
          description = "Get historical events that happened on a given date from Wikipedia. Returns notable events, births, and deaths.")
    public String todayInHistory(
            @ToolParam(description = "Month (1-12). Defaults to current month if not provided.") Integer month,
            @ToolParam(description = "Day (1-31). Defaults to current day if not provided.") Integer day) {

        LocalDate now = LocalDate.now();
        int m = month != null ? month : now.getMonthValue();
        int d = day != null ? day : now.getDayOfMonth();

        try {
            String json = restClient.get()
                    .uri("/onthisday/all/{month}/{day}",
                            String.format("%02d", m), String.format("%02d", d))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(json);

            List<String> selected = extractEntries(root, "selected", 10);
            List<String> events = extractEntries(root, "events", 10);
            List<String> births = extractEntries(root, "births", 10);
            List<String> deaths = extractEntries(root, "deaths", 10);
            List<String> holidays = extractEntries(root, "holidays", 10);

            return objectMapper.writeValueAsString(Map.of(
                    "date", "%02d/%02d".formatted(m, d),
                    "selected", selected,
                    "events", events,
                    "births", births,
                    "deaths", deaths,
                    "holidays", holidays
            ));
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage().replace("\"", "\\\"") + "\"}";
        }
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
