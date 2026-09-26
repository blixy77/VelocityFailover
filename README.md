# VelocityFailover

When one of your backend servers crashes or restarts, players on it are moved to a limbo server, and once the server is back they are moved back. Nobody gets the disconnect screen.

## How it works

1. A server goes down. Velocity kicks its players with a reason like "Server closed".
2. The plugin sees that kick, marks the server as down and sends the player to limbo instead.
3. While they wait, animated title messages and a small action-bar spinner stay visible.
4. The plugin pings the downed server until it answers a few times in a row, then waits a moment so its plugins can load.
5. Players are moved back one at a time, so a freshly started server is not hit all at once.
6. Anyone trying to join the server while it is down gets a message instead.

Only downed servers are pinged. When everything is running, the plugin does nothing.

Normal kicks (bans, anticheat and so on) are left alone. Only reasons that match `shutdown-keywords` count as a crash.

## Requirements

- Velocity 4.x
- Java 25
- A limbo server registered in `velocity.toml`, for example [PicoLimbo](https://github.com/Quozul/PicoLimbo) or an empty Paper server

## Setup

1. Drop the jar into the proxy's `plugins/` folder and start the proxy once.
2. Open `plugins/velocityfailover/config.yml` and fill in your limbo server and the servers to watch.
3. Run `/failoverreload` (permission `velocityfailover.reload`) or restart the proxy.

## Config

```yaml
limbo-server: "limbo"

# Servers to watch. Groups are only for keeping the list tidy.
groups:
  lobby:
    servers: ["lobby1", "lobby2"]
  spawn:
    servers: ["spawn1", "spawn2"]

recovery:
  ping-interval-ms: 2000      # how often a downed server is pinged
  pings-to-ready: 3           # answers in a row before it counts as back
  grace-period-ms: 5000       # extra wait so its plugins finish loading
  transfer-interval-ms: 50    # pause between moving one player and the next
  ping-timeout-ms: 2000

# Kick reasons that mean the server went down, matched with "contains".
shutdown-keywords:
  - "Server closed"
  - "Server shutting down"

# MiniMessage. {spinner} in the action bar is replaced with the current frame.
messages:
  sent-to-limbo: "<red>The server is temporarily unavailable. You will be moved back automatically when it returns."
  reconnecting: "<green>The server is back online! Reconnecting..."
  connection-blocked: "<red>This server is currently unavailable. Please try again in a moment."
  # Set to "" if the limbo server already provides an action bar.
  waiting-action-bar: "<yellow>Connecting to the server <gray>{spinner}"

# Persistent title animations. Each list entry is one animation frame.
titles:
  interval-ms: 1000
  connecting-delay-ms: 2000
  animation-stay-ms: 30000
  waiting:
    - { title: "<red><bold>Server unavailable.</bold>", subtitle: "<gray>Please wait..." }
    - { title: "<red><bold>Server unavailable..</bold>", subtitle: "<gray>Please wait..." }
    - { title: "<red><bold>Server unavailable...</bold>", subtitle: "<gray>Please wait..." }
  connecting:
    - { title: "<green><bold>Reconnecting.</bold>", subtitle: "<gray>Please wait..." }
    - { title: "<green><bold>Reconnecting..</bold>", subtitle: "<gray>Please wait..." }
    - { title: "<green><bold>Reconnecting...</bold>", subtitle: "<gray>Please wait..." }
  fade-in-ms: 300
  stay-ms: 2500
  fade-out-ms: 500
  connection-blocked:
    title: "<red><bold>Server unavailable</bold>"
    subtitle: "<gray>Please try again in a moment"

sounds:
  waiting:
    name: "minecraft:entity.experience_orb.pickup"
    source: "master"
    volume: 0.5
    pitch: 1.0
  connecting:
    name: "minecraft:entity.player.levelup"
    source: "master"
    volume: 1.0
    pitch: 1.0

action-bar:
  interval-ms: 400
  spinner-frames: ["[|]", "[/]", "[-]", "[\\]"]
```

Server names must match `velocity.toml` exactly. Do not list the limbo server itself.

Titles use MiniMessage too. Existing configs without a `titles` section automatically use the defaults above. `connecting-delay-ms` guarantees time for the connecting animation even if `grace-period-ms` is shorter. Set `waiting: []` or `connecting: []` to disable either animation. The earlier single `sent-to-limbo` and `reconnecting` title format remains accepted as a one-frame animation.

Only one plugin should own the action bar. If PicoLimbo or another limbo plugin already displays one, set `messages.waiting-action-bar: ""`; VelocityFailover will then stop sending action-bar packets entirely.

The waiting sound plays with every title frame; the connecting sound plays once when recovery starts. Both use vanilla client sounds and need no resource pack. Set a sound's `name` to `""` to disable it.

## Good to know

- A player who leaves while waiting is forgotten. When they come back, your usual hub or fallback plugin takes over.
- A player who goes somewhere else on their own while waiting is forgotten too.
- If the server dies again mid-transfer, players already moved land back on limbo and the whole cycle starts over.
- This is not a hub plugin. It only handles crashes and restarts.

## License

MIT
