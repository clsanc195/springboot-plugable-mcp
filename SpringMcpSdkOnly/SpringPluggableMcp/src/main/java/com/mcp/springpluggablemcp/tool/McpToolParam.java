package com.mcp.springpluggablemcp.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provides metadata for a parameter of an {@link McpTool}-annotated method.
 * The description is included in the JSON Schema exposed to MCP clients.
 *
 * <pre>
 * &#64;McpTool(name = "greet", description = "Greet a user")
 * public String greet(@McpToolParam(description = "User's name") String name) { ... }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpToolParam {

    /**
     * Human-readable description of the parameter.
     */
    String description() default "";

    /**
     * Whether this parameter is required. Defaults to {@code true}.
     */
    boolean required() default true;
}
