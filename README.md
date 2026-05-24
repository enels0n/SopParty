# SopParty

`SopParty` is a shared party system for the `Sop*` network.

It uses `Velocity` as the authoritative party layer for network setups and provides a Bukkit/Paper bridge for:

- invites and accept/deny flow
- leave, kick, transfer, and list
- PlaceholderAPI-ready player-facing messages through backend delivery
- proxy-wide tab completion with standalone local fallback

## Modules

- `SopParty-Velocity` - authoritative party state and proxy command handling
- `SopParty-Bukkit` - backend bridge, command access, and PlaceholderAPI-aware messaging
- `sop-party-api` - shared API for dependent plugins
- `sop-party-protocol` - proxy/backend transport models

## Version Model

- Bukkit side targets the `1.16.5` API baseline and Java 8
- Velocity side uses Java 17, as required by modern Velocity
- Runtime support for backend plugins is designed around old stable Spigot/Paper baselines

## Build

From repository root:

```bash
mvn -DskipTests package
```

Main outputs:

- `SopParty-Bukkit/target/SopParty.jar`
- `SopParty-Velocity/target/SopPartyVelocity.jar`

## Requirements

Network mode:

- Velocity proxy
- one or more Bukkit/Paper backend servers

Standalone/local backend features:

- local tab completion falls back to Bukkit online players when no proxy-wide snapshot is available

Optional:

- PlaceholderAPI on Bukkit/Paper for player-facing message formatting

## Commands

Base command:

- `/party`

Typical actions:

- `create`
- `invite`
- `accept`
- `deny`
- `leave`
- `kick`
- `transfer`
- `list`

## For Integrations

Game plugins should prefer the shared API instead of reimplementing party logic.

Examples in the current ecosystem:

- `SopPillars`
- `SopTNTRun`
