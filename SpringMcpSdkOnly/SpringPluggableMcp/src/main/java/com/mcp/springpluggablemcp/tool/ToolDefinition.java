package com.mcp.springpluggablemcp.tool;

/**
 * Immutable description of an MCP tool: its name, what it does,
 * and the JSON Schema that describes its input.
 */
public record ToolDefinition(
        String name,
        String description,
        String inputSchema
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private String description;
        private String inputSchema;

        private Builder() {}

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder inputSchema(String inputSchema) {
            this.inputSchema = inputSchema;
            return this;
        }

        public ToolDefinition build() {
            return new ToolDefinition(name, description, inputSchema);
        }
    }
}
