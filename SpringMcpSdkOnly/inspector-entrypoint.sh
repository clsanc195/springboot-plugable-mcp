#!/bin/sh
set -e

# Pre-install the inspector so we can patch before running
npx -y @modelcontextprotocol/inspector@latest --help >/dev/null 2>&1 || true

# Patch the strict Accept header validation in the MCP SDK server transport.
# The inspector UI only sends "text/event-stream" but the SDK requires both
# "application/json" AND "text/event-stream". This relaxes the POST handler
# to only require "text/event-stream" (matching the GET handler behaviour).
SDK_FILE=$(find /root/.npm/_npx -name "webStandardStreamableHttp.js" -path "*/esm/server/*" 2>/dev/null | head -1)
if [ -n "$SDK_FILE" ]; then
  sed -i 's/!acceptHeader?.includes(.application\/json.) || !acceptHeader.includes(.text\/event-stream.)/!acceptHeader?.includes("text\/event-stream")/g' "$SDK_FILE"
  echo "Patched Accept header validation in $SDK_FILE"
fi

# Now start the inspector
exec npx -y @modelcontextprotocol/inspector@latest "$@"
