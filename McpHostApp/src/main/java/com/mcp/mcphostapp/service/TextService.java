package com.mcp.mcphostapp.service;

import org.springframework.stereotype.Service;

@Service
public class TextService {

    public record TextStats(int characters, int words, int lines, int sentences) {}

    public TextStats analyze(String text) {
        int characters = text.length();
        int words = text.isBlank() ? 0 : text.trim().split("\\s+").length;
        int lines = text.split("\\r?\\n", -1).length;
        int sentences = text.split("[.!?]+").length;
        return new TextStats(characters, words, lines, sentences);
    }

    public String transform(String text, String operation) {
        return switch (operation.toLowerCase()) {
            case "uppercase" -> text.toUpperCase();
            case "lowercase" -> text.toLowerCase();
            case "reverse" -> new StringBuilder(text).reverse().toString();
            case "trim" -> text.trim();
            default -> throw new IllegalArgumentException("Unknown operation: " + operation
                    + ". Supported: uppercase, lowercase, reverse, trim");
        };
    }
}
