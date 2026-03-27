package com.mcp.springpluggablemcp.dynamic.config;

import com.mcp.springpluggablemcp.dynamic.loader.SimpleQueryToolSource;
import com.mcp.springpluggablemcp.dynamic.loader.ToolDefinitionSource;
import com.mcp.springpluggablemcp.dynamic.mapping.DefaultToolRecordMapper;
import com.mcp.springpluggablemcp.dynamic.mapping.ToolRecordMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;

/**
 * JDBC-specific configuration — only activates when no custom
 * {@link ToolDefinitionSource} bean is provided. Supplies the default
 * DataSource, JdbcTemplate, mapper, and query-based source.
 */
@Configuration
@EnableConfigurationProperties(DynamicToolJdbcProperties.class)
public class DynamicToolJdbcConfig {

    @Bean
    @Lazy
    @ConditionalOnMissingBean(name = "dynamicToolsDataSource")
    @Qualifier("dynamicToolsDataSource")
    public DataSource dynamicToolsDataSource(DynamicToolJdbcProperties properties) {
        var ds = properties.datasource();
        return DataSourceBuilder.create()
                .url(ds.url())
                .username(ds.username())
                .password(ds.password())
                .driverClassName(ds.driverClassName())
                .build();
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean(name = "dynamicToolsJdbcTemplate")
    @Qualifier("dynamicToolsJdbcTemplate")
    public JdbcTemplate dynamicToolsJdbcTemplate(
            @Qualifier("dynamicToolsDataSource") DataSource dynamicToolsDataSource) {
        return new JdbcTemplate(dynamicToolsDataSource);
    }

    @Bean
    @ConditionalOnMissingBean(ToolRecordMapper.class)
    public ToolRecordMapper<ResultSet> defaultToolRecordMapper(DynamicToolJdbcProperties properties) {
        return new DefaultToolRecordMapper(properties.columnMapping());
    }

    @Bean
    @ConditionalOnMissingBean(ToolDefinitionSource.class)
    public ToolDefinitionSource simpleQueryToolSource(
            @Qualifier("dynamicToolsJdbcTemplate") JdbcTemplate jdbcTemplate,
            DynamicToolJdbcProperties properties,
            ToolRecordMapper<ResultSet> mapper) {
        return new SimpleQueryToolSource(jdbcTemplate, properties, mapper);
    }
}
