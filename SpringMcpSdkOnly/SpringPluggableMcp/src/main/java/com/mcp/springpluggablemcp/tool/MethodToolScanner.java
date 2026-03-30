package com.mcp.springpluggablemcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * Scans a set of objects for {@link McpTool}-annotated methods and
 * creates a {@link ToolCallback} for each one.
 * <p>
 * This replaces Spring AI's {@code MethodToolCallbackProvider}. It uses
 * only standard Java reflection — no Spring AI dependency.
 * <p>
 * <b>Note:</b> compile with {@code -parameters} so real parameter names
 * are available at runtime. Otherwise names default to {@code arg0, arg1, ...}.
 */
public final class MethodToolScanner {

    private static final Logger log = LoggerFactory.getLogger(MethodToolScanner.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MethodToolScanner() {}

    /**
     * Scan the given objects and return a callback for every
     * {@code @McpTool}-annotated method found.
     */
    public static List<ToolCallback> scan(Object... toolBeans) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (Object bean : toolBeans) {
            for (Method method : bean.getClass().getDeclaredMethods()) {
                McpTool annotation = method.getAnnotation(McpTool.class);
                if (annotation == null) continue;

                String toolName = annotation.name().isEmpty() ? method.getName() : annotation.name();
                String description = annotation.description();
                String inputSchema = buildInputSchema(method);

                ToolDefinition definition = ToolDefinition.builder()
                        .name(toolName)
                        .description(description)
                        .inputSchema(inputSchema)
                        .build();

                callbacks.add(new ReflectiveToolCallback(definition, bean, method));
                log.debug("Discovered @McpTool method: {} -> {}", toolName, method);
            }
        }
        return callbacks;
    }

    /**
     * Build a JSON Schema string from the method's parameters.
     */
    private static String buildInputSchema(Method method) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Parameter param : method.getParameters()) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", jsonType(param.getType()));

            McpToolParam meta = param.getAnnotation(McpToolParam.class);
            if (meta != null) {
                if (!meta.description().isEmpty()) {
                    prop.put("description", meta.description());
                }
                if (meta.required()) {
                    required.add(param.getName());
                }
            } else {
                required.add(param.getName());
            }

            properties.put(param.getName(), prop);
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        try {
            return MAPPER.writeValueAsString(schema);
        } catch (Exception e) {
            log.warn("Failed to serialize input schema for method, using empty schema: {}", e.getMessage());
            return "{\"type\":\"object\"}";
        }
    }

    private static String jsonType(Class<?> type) {
        if (type == String.class) return "string";
        if (type == int.class || type == Integer.class) return "integer";
        if (type == long.class || type == Long.class) return "integer";
        if (type == double.class || type == Double.class) return "number";
        if (type == float.class || type == Float.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        return "string";
    }

    /**
     * A {@link ToolCallback} that invokes a method via reflection and
     * serialises the return value as JSON.
     */
    private static final class ReflectiveToolCallback implements ToolCallback {

        private final ToolDefinition definition;
        private final Object target;
        private final Method method;

        ReflectiveToolCallback(ToolDefinition definition, Object target, Method method) {
            this.definition = definition;
            this.target = target;
            this.method = method;
            this.method.setAccessible(true);
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> args = MAPPER.readValue(toolInput, Map.class);
                Object[] invokeArgs = resolveArguments(args);
                Object result = method.invoke(target, invokeArgs);
                if (result instanceof String s) {
                    return s;
                }
                return MAPPER.writeValueAsString(result);
            } catch (Exception e) {
                throw new RuntimeException("Failed to invoke tool '" + definition.name() + "': " + e.getMessage(), e);
            }
        }

        private Object[] resolveArguments(Map<String, Object> args) {
            Parameter[] params = method.getParameters();
            Object[] resolved = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                Object value = args.get(params[i].getName());
                resolved[i] = coerce(value, params[i].getType());
            }
            return resolved;
        }

        private Object coerce(Object value, Class<?> target) {
            if (value == null) return null;
            if (target.isInstance(value)) return value;
            return MAPPER.convertValue(value, target);
        }
    }
}
