package com.mcp.mcphostapp.mcp.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolExecutionUtilsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void resolvesPlaceholdersInTemplate() throws Exception {
        JsonNode input = mapper.readTree("{\"name\":\"Alice\",\"id\":\"42\"}");
        String result = ToolExecutionUtils.resolvePlaceholders("/users/{id}/profile/{name}", input);
        assertEquals("/users/42/profile/Alice", result);
    }

    @Test
    void noPlaceholdersReturnsTemplateUnchanged() throws Exception {
        JsonNode input = mapper.readTree("{\"name\":\"Alice\"}");
        String result = ToolExecutionUtils.resolvePlaceholders("/static/path", input);
        assertEquals("/static/path", result);
    }

    @Test
    void resolveBodyTemplateSubstitutesValues() throws Exception {
        JsonNode config = mapper.readTree("{\"bodyTemplate\":{\"title\":\"{title}\",\"count\":\"{count}\"}}");
        JsonNode input = mapper.readTree("{\"title\":\"Hello\",\"count\":\"5\"}");
        String result = ToolExecutionUtils.resolveBodyTemplate(config, input);
        JsonNode parsed = mapper.readTree(result);
        assertEquals("Hello", parsed.get("title").asText());
    }

    @Test
    void resolveBodyTemplateWithoutTemplateReturnsInput() throws Exception {
        JsonNode config = mapper.readTree("{\"method\":\"POST\"}");
        JsonNode input = mapper.readTree("{\"data\":\"value\"}");
        String result = ToolExecutionUtils.resolveBodyTemplate(config, input);
        assertEquals("{\"data\":\"value\"}", result);
    }

    @Test
    void parseBodySafeReturnsJsonNodeForValidJson() {
        Object result = ToolExecutionUtils.parseBodySafe("{\"key\":\"val\"}", mapper);
        assertInstanceOf(JsonNode.class, result);
    }

    @Test
    void parseBodySafeReturnsStringForInvalidJson() {
        Object result = ToolExecutionUtils.parseBodySafe("not json", mapper);
        assertEquals("not json", result);
    }

    @Test
    void parseBodySafeHandlesNull() {
        assertNull(ToolExecutionUtils.parseBodySafe(null, mapper));
    }

    @Test
    void errorJsonProducesValidJson() throws Exception {
        String result = ToolExecutionUtils.errorJson(new RuntimeException("something broke"), mapper);
        JsonNode parsed = mapper.readTree(result);
        assertEquals("something broke", parsed.get("error").asText());
    }

    @Test
    void errorJsonHandlesNullMessage() throws Exception {
        String result = ToolExecutionUtils.errorJson(new RuntimeException((String) null), mapper);
        JsonNode parsed = mapper.readTree(result);
        assertEquals("Unknown error", parsed.get("error").asText());
    }

    @Test
    void errorJsonHandlesSpecialCharacters() throws Exception {
        String result = ToolExecutionUtils.errorJson(
                new RuntimeException("bad \"quotes\" and \\backslashes\\"), mapper);
        // Must be valid JSON — parsing should not throw
        JsonNode parsed = mapper.readTree(result);
        assertNotNull(parsed.get("error"));
    }
}
