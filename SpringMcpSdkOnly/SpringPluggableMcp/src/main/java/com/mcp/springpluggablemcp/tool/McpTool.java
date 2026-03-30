package com.mcp.springpluggablemcp.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an MCP tool that will be auto-discovered and
 * registered with the MCP server at startup.
 * <p>
 * Replaces Spring AI's {@code @Tool} annotation with a framework-free
 * equivalent that depends only on the MCP Java SDK.
 *
 * <pre>
 * &#64;McpTool(name = "get_time", description = "Returns the current time")
 * public String getTime(@McpToolParam(description = "IANA timezone") String timezone) {
 *     ...
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpTool {

    /**
     * Tool name exposed to MCP clients. Defaults to the method name.
     */
    String name() default "";

    /**
     * Human-readable description of what the tool does.
     */
    String description() default "";
}
