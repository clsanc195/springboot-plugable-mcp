package com.mcp.springpluggablemcp.dynamic.mapping;

/**
 * Maps a raw data row into a {@link DynamicToolRecord}.
 * <p>
 * The generic type parameter allows different source implementations
 * to use their own row representation (e.g. {@code ResultSet} for JDBC,
 * {@code JsonNode} for a REST API, {@code Map} for a file-based source).
 *
 * @param <T> the type of the source row
 */
public interface ToolRecordMapper<T> {

    DynamicToolRecord map(T row) throws Exception;
}
