package com.mcp.springpluggablemcp.dynamic.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DynamicToolJdbcConfigTest {

    private StandardEnvironment envWith(Map<String, Object> props) {
        var env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", props));
        return env;
    }

    @Test
    void registersBeanDefinitionsForEachSource() {
        var config = new DynamicToolJdbcConfig();
        config.setEnvironment(envWith(Map.of(
                "spring-pluggable-mcp.datasources[0].name", "alpha",
                "spring-pluggable-mcp.datasources[0].url", "jdbc:h2:mem:alpha",
                "spring-pluggable-mcp.datasources[0].driver-class-name", "org.h2.Driver",
                "spring-pluggable-mcp.datasources[1].name", "beta",
                "spring-pluggable-mcp.datasources[1].url", "jdbc:h2:mem:beta",
                "spring-pluggable-mcp.datasources[1].driver-class-name", "org.h2.Driver"
        )));

        var registry = new DefaultListableBeanFactory();
        config.postProcessBeanDefinitionRegistry(registry);

        assertTrue(registry.containsBeanDefinition("alpha-dataSource"));
        assertTrue(registry.containsBeanDefinition("alpha-jdbcTemplate"));
        assertTrue(registry.containsBeanDefinition("beta-dataSource"));
        assertTrue(registry.containsBeanDefinition("beta-jdbcTemplate"));
        assertEquals(4, registry.getBeanDefinitionCount());
    }

    @Test
    void skipsEntriesWithMissingName() {
        var config = new DynamicToolJdbcConfig();
        config.setEnvironment(envWith(Map.of(
                "spring-pluggable-mcp.datasources[0].url", "jdbc:h2:mem:noname",
                "spring-pluggable-mcp.datasources[0].driver-class-name", "org.h2.Driver"
        )));

        var registry = new DefaultListableBeanFactory();
        config.postProcessBeanDefinitionRegistry(registry);

        assertEquals(0, registry.getBeanDefinitionCount());
    }

    @Test
    void skipsEntriesWithMissingUrl() {
        var config = new DynamicToolJdbcConfig();
        config.setEnvironment(envWith(Map.of(
                "spring-pluggable-mcp.datasources[0].name", "broken"
        )));

        var registry = new DefaultListableBeanFactory();
        config.postProcessBeanDefinitionRegistry(registry);

        assertEquals(0, registry.getBeanDefinitionCount());
    }

    @Test
    void noSourcesConfiguredRegistersNothing() {
        var config = new DynamicToolJdbcConfig();
        config.setEnvironment(envWith(Map.of()));

        var registry = new DefaultListableBeanFactory();
        config.postProcessBeanDefinitionRegistry(registry);

        assertEquals(0, registry.getBeanDefinitionCount());
    }

    @Test
    void dataSourceBeanIsLazy() {
        var config = new DynamicToolJdbcConfig();
        config.setEnvironment(envWith(Map.of(
                "spring-pluggable-mcp.datasources[0].name", "lazy",
                "spring-pluggable-mcp.datasources[0].url", "jdbc:h2:mem:lazy",
                "spring-pluggable-mcp.datasources[0].driver-class-name", "org.h2.Driver"
        )));

        var registry = new DefaultListableBeanFactory();
        config.postProcessBeanDefinitionRegistry(registry);

        assertTrue(registry.getBeanDefinition("lazy-dataSource").isLazyInit());
        assertTrue(registry.getBeanDefinition("lazy-jdbcTemplate").isLazyInit());
    }

    @Test
    void beanDefinitionsHaveCorrectTypes() {
        var config = new DynamicToolJdbcConfig();
        config.setEnvironment(envWith(Map.of(
                "spring-pluggable-mcp.datasources[0].name", "typed",
                "spring-pluggable-mcp.datasources[0].url", "jdbc:h2:mem:typed",
                "spring-pluggable-mcp.datasources[0].driver-class-name", "org.h2.Driver"
        )));

        var registry = new DefaultListableBeanFactory();
        config.postProcessBeanDefinitionRegistry(registry);

        assertEquals(DataSource.class.getName(),
                registry.getBeanDefinition("typed-dataSource").getBeanClassName());
        assertEquals(JdbcTemplate.class.getName(),
                registry.getBeanDefinition("typed-jdbcTemplate").getBeanClassName());
    }
}
