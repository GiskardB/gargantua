# CLI — Agent Shell

Interactive CLI built on Spring Shell 4.0.1.

## Start
```bash
# Embedded mode (in-process, needs MongoDB + Redis)
java -jar agent-shell/target/agent-shell-1.0.0.jar

# Remote mode (connects to running server)
java -jar agent-shell/target/agent-shell-1.0.0.jar --agent.shell.mode=remote --agent.shell.remote.url=http://localhost:8080

# Single message (non-interactive, for CI)
java -jar agent-shell/target/agent-shell-1.0.0.jar chat --message "Hello"
```

## Commands

### chat
Interactive conversation. Type message and press Enter.
Special commands inside chat:
| Command | Action |
|---------|--------|
| \exit | Return to shell |
| \new | New session |
| \dry | Toggle dry-run |
| \skill <name> | Force next skill |
| \history | Last 10 messages |
| \info | Session info |
| \clear | Clear screen |

### skill
```
skill list          — table of all skills
skill show <name>   — skill detail
skill reload        — hot reload
```

### eval
```
eval run --skill weather-skill
eval run --all
```

### session
```
session new
session list
session resume <sessionId>
```

### cost
```
cost summary --days 7
```

## Configuration
```yaml
agent:
  shell:
    mode: embedded       # embedded | remote
    user-id: dev-user
    show-meta: true
    show-timing: true
    ansi: auto           # auto | always | never
    remote:
      url: http://localhost:8080
      timeout-ms: 30000
```
