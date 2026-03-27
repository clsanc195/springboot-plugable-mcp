package com.mcp.springpluggablemcp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class McpToolConfig {

    private static final Logger log = LoggerFactory.getLogger(McpToolConfig.class);

    @Bean
    @ConditionalOnMissingBean(MethodToolCallbackProvider.class)
    public MethodToolCallbackProvider toolCallbackProvider(ConfigurableApplicationContext context) {
        List<Object> toolObjects = new ArrayList<>();

        for (String beanName : context.getBeanDefinitionNames()) {
            Class<?> beanType = resolveBeanClass(context, beanName);
            if (beanType != null && hasToolMethods(beanType)) {
                toolObjects.add(context.getBean(beanName));
            }
        }

        return MethodToolCallbackProvider.builder()
                .toolObjects(toolObjects.toArray())
                .build();
    }

    private Class<?> resolveBeanClass(ConfigurableApplicationContext context, String beanName) {
        try {
            String className = context.getBeanFactory()
                    .getBeanDefinition(beanName)
                    .getBeanClassName();
            if (className != null) {
                return Class.forName(className);
            }
            return null;
        } catch (ClassNotFoundException e) {
            log.warn("Could not resolve class for bean '{}': {}", beanName, e.getMessage());
            return null;
        } catch (Exception e) {
            log.debug("Skipping bean '{}' during @Tool scan: {}", beanName, e.getMessage());
            return null;
        }
    }

    private boolean hasToolMethods(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                return true;
            }
        }
        return false;
    }
}
