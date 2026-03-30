package com.mcp.mcphostapp.mcp.tools;

import com.mcp.mcphostapp.service.TextService;
import com.mcp.mcphostapp.service.TextService.TextStats;
import com.mcp.springpluggablemcp.tool.McpTool;
import com.mcp.springpluggablemcp.tool.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class TextTools {

    private final TextService textService;

    public TextTools(TextService textService) {
        this.textService = textService;
    }

    @McpTool(name = "analyze_text", description = "Analyze text and return statistics: character count, word count, line count, and sentence count")
    public TextStats analyzeText(
            @McpToolParam(description = "The text to analyze") String text) {
        return textService.analyze(text);
    }

    @McpTool(name = "transform_text", description = "Transform text using a specified operation: uppercase, lowercase, reverse, or trim")
    public String transformText(
            @McpToolParam(description = "The text to transform") String text,
            @McpToolParam(description = "The transformation operation: uppercase, lowercase, reverse, trim") String operation) {
        return textService.transform(text, operation);
    }
}
