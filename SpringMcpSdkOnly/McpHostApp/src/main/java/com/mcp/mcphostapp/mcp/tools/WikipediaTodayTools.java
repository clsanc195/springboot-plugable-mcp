package com.mcp.mcphostapp.mcp.tools;

import com.mcp.mcphostapp.mcp.tools.wikipedia.WikipediaClient;
import com.mcp.springpluggablemcp.tool.McpTool;
import com.mcp.springpluggablemcp.tool.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class WikipediaTodayTools {

    private final WikipediaClient wikipediaClient;

    public WikipediaTodayTools(WikipediaClient wikipediaClient) {
        this.wikipediaClient = wikipediaClient;
    }

    @McpTool(name = "today_in_history",
          description = "Get historical events that happened on a given date from Wikipedia. Returns notable events, births, and deaths.")
    public String todayInHistory(
            @McpToolParam(description = "Month (1-12). Defaults to current month if not provided.", required = false) Integer month,
            @McpToolParam(description = "Day (1-31). Defaults to current day if not provided.", required = false) Integer day) {

        LocalDate now = LocalDate.now();
        int m = month != null ? month : now.getMonthValue();
        int d = day != null ? day : now.getDayOfMonth();

        try {
            return wikipediaClient.getOnThisDay(m, d);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage().replace("\"", "\\\"") + "\"}";
        }
    }
}
