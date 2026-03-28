# MCP Server

Expose your agent as an [MCP](https://modelcontextprotocol.io/) server so that Claude Desktop, Cursor, VS Code, or other MCP-compatible clients can invoke it directly.

## Enable

```yaml
agent:
  mcp:
    enabled: true
    mode: gateway            # gateway | transparent | both
    server:
      name: my-agent
      version: 1.0.0
    transport:
      type: sse
      path: /mcp
```

## Modes

### Gateway (default)

One MCP tool: `chat`. The client sends a message, the agent routes it through the full pipeline (guardrails, routing, memory, tools).

```
MCP Client → tool: chat(message, sessionId?, skillName?) → OrchestratorEngine → response
```

### Transparent

Exposes fine-grained primitives:

| MCP primitive | What it maps to |
|---------------|-----------------|
| Tool `invoke_skill` | OrchestratorEngine with forceSkill |
| Resource `gargantua://capabilities` | CapabilitiesService |
| Resource `gargantua://skill/{name}` | SkillRegistry (description only) |
| Prompt `use-skill` | SKILL.md body as prompt template |

## SSE Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/mcp/sse` | SSE initialization stream |
| POST | `/mcp/message` | JSON-RPC 2.0 messages |

The path prefix is configurable via `agent.mcp.transport.path`.

## Connect Claude Desktop

Add to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "my-agent": {
      "url": "http://localhost:8080/mcp/sse",
      "transport": "sse"
    }
  }
}
```

Restart Claude Desktop. The agent's `chat` tool will appear in the tool list.

## Security

```yaml
agent:
  mcp:
    security:
      require-api-key: true
      api-key: ${MCP_API_KEY}
```

When enabled, the client must send `Authorization: Bearer <key>` on the SSE connection.

## Coexistence with MCP Client

The agent can simultaneously:
- **Be an MCP server** (this module) — invoked by Claude Desktop, other agents
- **Be an MCP client** (`langchain4j-mcp` in the starter) — calling external MCP servers as tools

Both directions work at the same time.
