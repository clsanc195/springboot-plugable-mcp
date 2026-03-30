package com.mcp.mcphostapp.mcp.tools;

import com.mcp.mcphostapp.service.TextService;
import com.mcp.mcphostapp.service.TextService.TextStats;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class TextTools {

    private final TextService textService;

    public TextTools(TextService textService) {
        this.textService = textService;
    }

    @Tool(name = "analyze_text", description = "Analyze text and return statistics: character count, word count, line count, and sentence count")
    public TextStats analyzeText(
            @ToolParam(description = "The text to analyze") String text) {
        return textService.analyze(text);
    }

    @Tool(name = "transform_text", description = "Transform text using a specified operation: uppercase, lowercase, reverse, or trim")
    public String transformText(
            @ToolParam(description = "The text to transform") String text,
            @ToolParam(description = "The transformation operation: uppercase, lowercase, reverse, trim") String operation) {
        return textService.transform(text, operation);
    }
}
