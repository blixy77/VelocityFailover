# VelocityFailover

A lightweight Velocity proxy plugin that automatically handles server crashes and restarts — no player left behind.

## What does it do?

When a backend server goes down (crash, restart, `/stop`), VelocityFailover:

1. **Instantly detects** the server is down
2. **Moves affected players** to a limbo server
3. **Monitors** the downed server for recovery
4. **Automatically reconnects** players back to their original server once it's ready
5. **Blocks** other players from joining unavailable servers with a friendly message

All of this happens seamlessly — players see a short message, wait on limbo, and get moved back automatically.

## Why VelocityFailover?

- **Zero external dependencies** — only the Velocity API, nothing else
- **Extremely lightweight** — no constant heartbeat pinging all servers. Recovery pings run *only* on servers that are actually down
- **Instant detection** — uses Velocity's kick events instead of slow polling intervals. Players are redirected in milliseconds, not seconds
- **Gradual reconnection** — players are transferred back one at a time to avoid overloading a freshly started server
- **Smart kick detection** — distinguishes between server crashes and normal kicks (bans, anticheat, etc.). A banned player won't end up on limbo
- **Connection blocking** — players trying to manually join a recovering server get a message instead of being thrown into limbo
- **No Paper plugin needed** — runs entirely on the Velocity proxy side

## Requirements

- Velocity 3.4.0+
- Java 17+
- A limbo server registered in `velocity.toml` (e.g. [PicoLimbo](https://github.com/Quozul/PicoLimbo) or an empty Paper server)

## Installation

1. Download the latest `.jar` from [Releases](../../releases)
2. Place it in your Velocity `plugins/` folder
3. Start the proxy — a default `config.yml` will be generated in `plugins/velocityfailover/`
4. Edit `config.yml` to match your server setup
5. Restart the proxy or use /failoverreload

## Commands and permissions
/failoverreload - reloads config (permission: velocityfailover.reload) 

## Configuration

```yaml
# Name of the limbo server registered in velocity.toml
limbo-server: "limbo"

# Server groups to monitor.
# Groups are for organization only — each server is tracked individually.
groups:
  lobby:
    servers:
      - "lobby1"
      - "lobby2"
  spawn:
    servers:
      - "spawn1"
      - "spawn2"

# Recovery monitor settings
recovery:
  ping-interval-ms: 2000       # How often to ping downed servers
  pings-to-ready: 3            # Successful pings in a row before recovery starts
  grace-period-ms: 5000        # Extra wait after pings pass (lets plugins load)
  transfer-interval-ms: 50     # Delay between each player transfer
  ping-timeout-ms: 2000        # Timeout for a single ping

# Kick reasons that indicate a server shutdown (checked via String.contains)
# If a player is kicked with one of these reasons, the server will be marked as offline
shutdown-keywords:
  - "Server closed"
  - "Server shutting down"

# Messages sent to players (MiniMessage format)
messages:
  sent-to-limbo: "<red>The server is temporarily unavailable. You will be moved back automatically when it returns."
  reconnecting: "<green>The server is back online! Reconnecting..."
  connection-blocked: "<red>This server is currently unavailable. Please try again in a moment."
```

### Important notes

- The **limbo server must not be listed** in any monitored group — it is always treated as available
- Server names in the config must match exactly what is in your `velocity.toml`
- Messages support [MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatting

## How it works

```
Player on spawn2 ──> spawn2 crashes
                          │
          KickListener catches the crash kick
                          │
          Player redirected to limbo instantly
                          │
          RecoveryMonitor starts pinging spawn2
                          │
          3 successful pings ──> 5s grace period
                          │
          Players transferred back one by one
                          │
          spawn2 marked as ONLINE again
```

## FAQ

**Q: What if a player disconnects while waiting on limbo?**
They are removed from the reconnect queue. When they rejoin, your existing routing plugin handles them normally.

**Q: What if the server crashes again during player transfers?**
The plugin handles this gracefully. Already-transferred players get kicked back to limbo. Remaining players stay on limbo. Recovery restarts from scratch.

**Q: Does this replace my hub plugin?**
No. This only handles crashes/restarts.
