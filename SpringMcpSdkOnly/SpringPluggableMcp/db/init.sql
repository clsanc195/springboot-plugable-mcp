CREATE TABLE IF NOT EXISTS tool_registry (
    id          SERIAL PRIMARY KEY,
    server_id   VARCHAR(255) NOT NULL,
    tool_id     INTEGER NOT NULL,
    enabled     BOOLEAN DEFAULT true,
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now(),
    UNIQUE(server_id, tool_id)
);

CREATE TABLE IF NOT EXISTS tool_definitions (
    id              SERIAL PRIMARY KEY,
    tool_name       VARCHAR(255) NOT NULL UNIQUE,
    tool_description TEXT NOT NULL,
    input_schema    JSONB NOT NULL,
    executor_type   VARCHAR(100) NOT NULL,
    executor_config JSONB,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now()
);

-- Sample tools
INSERT INTO tool_definitions (id, tool_name, tool_description, input_schema, executor_type, executor_config)
VALUES
    (1, 'echo_message', 'Echo back a message with a prefix',
     '{"type":"object","properties":{"message":{"type":"string","description":"The message to echo"}},"required":["message"]}',
     'echo', '{"prefix":"[McpHostApp]"}'),
    (2, 'lookup_user', 'Look up a user by ID',
     '{"type":"object","properties":{"user_id":{"type":"string","description":"The user ID to look up"}},"required":["user_id"]}',
     'echo', '{"source":"user_database"}'),
    (3, 'unrelated_tool', 'A tool that belongs to a different server',
     '{"type":"object","properties":{"x":{"type":"string"}},"required":["x"]}',
     'echo', '{}')
ON CONFLICT (tool_name) DO NOTHING;

-- Only tools 1 and 2 are assigned to mcp-host-app. Tool 3 is NOT.
INSERT INTO tool_registry (server_id, tool_id)
VALUES
    ('mcp-host-app', 1),
    ('mcp-host-app', 2)
ON CONFLICT (server_id, tool_id) DO NOTHING;
