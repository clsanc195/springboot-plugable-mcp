package com.mcp.mcphostapp.mcp.tools;

import com.mcp.mcphostapp.service.TimeService;
import com.mcp.springpluggablemcp.tool.McpTool;
import com.mcp.springpluggablemcp.tool.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class TimeTools {

    private final TimeService timeService;

    public TimeTools(TimeService timeService) {
        this.timeService = timeService;
    }

    @McpTool(name = "get_current_time", description = "Get the current date and time in a given timezone")
    public String getCurrentTime(
            @McpToolParam(description = "IANA timezone identifier, e.g. America/New_York, Europe/London, Asia/Tokyo") String timezone) {
        return timeService.getCurrentTime(timezone);
    }
}
