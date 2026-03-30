package com.mcp.mcphostapp.mcp.tools;

import com.mcp.mcphostapp.service.TimeService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class TimeTools {

    private final TimeService timeService;

    public TimeTools(TimeService timeService) {
        this.timeService = timeService;
    }

    @Tool(name = "get_current_time", description = "Get the current date and time in a given timezone")
    public String getCurrentTime(
            @ToolParam(description = "IANA timezone identifier, e.g. America/New_York, Europe/London, Asia/Tokyo") String timezone) {
        return timeService.getCurrentTime(timezone);
    }
}
