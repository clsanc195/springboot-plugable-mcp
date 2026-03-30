package com.mcp.springpluggablemcp.dynamic.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Dynamically registers a named {@code DataSource} and {@code JdbcTemplate}
 * bean for each entry in {@code spring-pluggable-mcp.datasources}.
 * <p>
 * Bean names follow the pattern {@code {name}-dataSource} and
 * {@code {name}-jdbcTemplate}. Clients inject them by qualifier:
 * <pre>
 * &#64;Qualifier("primary-jdbcTemplate") JdbcTemplate jdbcTemplate
 * </pre>
 */
@Configuration
public class DynamicToolJdbcConfig implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

    private static final Logger log = LoggerFactory.getLogger(DynamicToolJdbcConfig.class);

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        var props = Binder.get(environment)
                .bind("spring-pluggable-mcp", DynamicToolProperties.class)
                .orElse(null);

        if (props == null || props.datasources() == null || props.datasources().isEmpty()) {
            return;
        }

        for (var ds : props.datasources()) {
            String name = ds.name();
            if (name == null || name.isBlank()) {
                log.warn("Skipping datasource entry with missing 'name' field");
                continue;
            }

            if (ds.url() == null) {
                log.warn("[{}] Skipping datasource — url is required", name);
                continue;
            }

            // DataSource bean
            var dsBd = new RootBeanDefinition();
            dsBd.setBeanClass(DataSource.class);
            dsBd.setLazyInit(true);
            dsBd.setInstanceSupplier(() -> DataSourceBuilder.create()
                    .url(ds.url())
                    .username(ds.username())
                    .password(ds.password())
                    .driverClassName(ds.driverClassName())
                    .build());
            registry.registerBeanDefinition(name + "-dataSource", dsBd);

            // JdbcTemplate bean
            var jtBd = new RootBeanDefinition();
            jtBd.setBeanClass(JdbcTemplate.class);
            jtBd.setLazyInit(true);
            String dsRef = name + "-dataSource";
            jtBd.setInstanceSupplier(() -> {
                var bf = (ConfigurableListableBeanFactory) registry;
                return new JdbcTemplate(bf.getBean(dsRef, DataSource.class));
            });
            registry.registerBeanDefinition(name + "-jdbcTemplate", jtBd);

            log.info("[{}] Registered JDBC beans: {}-dataSource, {}-jdbcTemplate", name, name, name);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // no-op
    }
}
