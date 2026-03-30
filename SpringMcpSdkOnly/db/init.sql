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

CREATE TABLE IF NOT EXISTS tool_registry (
    id          SERIAL PRIMARY KEY,
    server_id   VARCHAR(255) NOT NULL,
    tool_id     INTEGER NOT NULL REFERENCES tool_definitions(id),
    enabled     BOOLEAN DEFAULT true,
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now(),
    UNIQUE(server_id, tool_id)
);

-- Sample dynamic tools (all use "echo" executor matching EchoStrategy in McpHostApp)
INSERT INTO tool_definitions (id, tool_name, tool_description, input_schema, executor_type, executor_config)
VALUES
    (1, 'echo_message', 'Echo back a message with a prefix',
     '{"type":"object","properties":{"message":{"type":"string","description":"The message to echo back"}},"required":["message"]}',
     'echo', '{"prefix":"[McpHostApp]"}'),

    (2, 'lookup_user', 'Look up a user by their ID and return user information',
     '{"type":"object","properties":{"user_id":{"type":"string","description":"The user ID to look up"}},"required":["user_id"]}',
     'echo', '{"source":"user_database"}'),

    (3, 'greet_user', 'Generate a greeting message for a user',
     '{"type":"object","properties":{"name":{"type":"string","description":"Name of the person to greet"},"language":{"type":"string","description":"Language for the greeting (en, es, fr)"}},"required":["name"]}',
     'echo', '{"style":"formal"}'),

    (4, 'unrelated_tool', 'A tool that belongs to a different server and should NOT appear for mcp-host-app',
     '{"type":"object","properties":{"x":{"type":"string"}},"required":["x"]}',
     'echo', '{}'),

    (5, 'get_random_activity', 'Suggest a random activity to do when bored',
     '{"type":"object","properties":{}}',
     'http', '{"url":"https://bored-api.appbrewery.com/random","method":"GET"}'),

    (6, 'get_github_user', 'Fetch public profile information for a GitHub user',
     '{"type":"object","properties":{"username":{"type":"string","description":"GitHub username"}},"required":["username"]}',
     'http', '{"url":"https://api.github.com/users/{username}","method":"GET","headers":{"Accept":"application/vnd.github.v3+json"}}'),

    (7, 'create_pastebin', 'Create a new paste via a JSON API',
     '{"type":"object","properties":{"title":{"type":"string","description":"Title of the paste"},"content":{"type":"string","description":"Content to paste"}},"required":["title","content"]}',
     'http', '{"url":"https://jsonplaceholder.typicode.com/posts","method":"POST","bodyTemplate":{"title":"{title}","body":"{content}","userId":1}}'),

    (8, 'idaas_get_user', 'Fetch a user from the IDaaS platform by ID',
     '{"type":"object","properties":{"id":{"type":"string","description":"The user ID"}},"required":["id"]}',
     'http_idaas', '{"path":"/users/{id}","method":"GET"}'),

    (9, 'idaas_create_post', 'Create a post through the IDaaS platform',
     '{"type":"object","properties":{"title":{"type":"string","description":"Post title"},"body":{"type":"string","description":"Post body"}},"required":["title","body"]}',
     'http_idaas', '{"path":"/posts","method":"POST","bodyTemplate":{"title":"{title}","body":"{body}","userId":1}}')
ON CONFLICT (tool_name) DO NOTHING;

-- Register tools 1-3 and 5-7 for mcp-host-app; tool 4 is deliberately NOT registered
INSERT INTO tool_registry (server_id, tool_id, enabled)
VALUES
    ('mcp-host-app', 1, true),
    ('mcp-host-app', 2, true),
    ('mcp-host-app', 3, true),
    ('mcp-host-app', 5, true),
    ('mcp-host-app', 6, true),
    ('mcp-host-app', 7, true),
    ('mcp-host-app', 8, true),
    ('mcp-host-app', 9, true)
ON CONFLICT (server_id, tool_id) DO NOTHING;
