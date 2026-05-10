# SopParty Specification

## Overview

`SopParty` is a standalone party system intended for reuse across multiple server modes and plugins.

Primary goal:
- provide a single consistent party model for the server ecosystem
- let game plugins such as `SopPillars` consume party information without implementing their own incompatible logic

This plugin should not be tightly coupled to a single minigame.

## Scope

`SopParty` manages:
- party creation
- invites
- leader/member roles
- joining/leaving/disbanding
- party chat
- party metadata
- integration hooks for minigames and future server systems

It should later support behavior like:
- leader joins a survival flow and party follows
- leader joins a minigame queue and party follows
- a game plugin requests party-aware reservation or matchmaking

## Design Goals

- minimal commands for players
- strong internal API/service for other plugins
- no hard dependency on one mode plugin
- clear separation between:
  - core party state
  - UI/UX
  - external integrations

## Target Versions

Priority versions:
- `1.16.5`
- `1.21.1`
- `1.21.11`

## Core Model

Party fields:
- `id`
- `leader`
- `members`
- `max-size`
- `invite-policy`
- `join-policy`
- `disband-on-empty`
- `created-at`

Member fields:
- `uuid`
- `name`
- `joined-at`
- `online`
- `role`

Roles:
- `LEADER`
- `MEMBER`

## Basic Player Commands

Suggested commands:

```text
/party create
/party invite <player>
/party accept <player>
/party deny <player>
/party leave
/party kick <player>
/party disband
/party transfer <player>
/party chat
/party list
```

Optional short aliases can be added later.

## Core Behavior

### Party Creation
- a player not already in a party can create one
- creator becomes leader

### Invites
- leader invites player
- invite has expiration time
- invited player can accept or deny

### Leaving
- member can leave party
- if leader leaves:
  - either disband
  - or transfer leadership automatically

Preferred default:
- transfer to oldest online member if available
- otherwise disband

### Disband
- leader can disband party manually
- all members are notified

## Chat

Optional party chat mode:
- toggle on/off
- while enabled, player messages go only to party members

Messages configurable manually through config.

## Integration API

This is the most important part for long-term use.

`SopParty` should expose a service/interface for other plugins, e.g.:
- get party by player
- check if player is leader
- get all members
- count online members
- try reserve members for match join
- check if all members fit a target

Suggested operations:
- `getParty(player)`
- `isInParty(player)`
- `isLeader(player)`
- `getOnlineMembers(player)`
- `canFitParty(party, capacity)`
- `lockPartyForGame(party, gameId)`
- `unlockParty(party, gameId)`

## Reservation / Match Integration

For minigame plugins such as `SopPillars`, `SopParty` should support temporary reservation semantics.

Example flow:
- `SopPillars` wants to join a party into arena queue
- it asks `SopParty` for party members
- it reserves them for a specific arena/match
- if join succeeds, reservation becomes active participation context
- if join fails, reservation is cleared

This prevents race conditions where members join different games simultaneously.

## Follow-Leader Concept

Long-term feature:
- "follow leader" behavior across server activities

Examples:
- leader joins a minigame queue, party follows
- leader joins a specific arena, party follows if capacity allows
- leader moves to a survival/event mode, party is routed consistently

This should not be fully implemented in MVP, but architecture should not block it.

## Interaction with SopPillars

`SopPillars` should not own the global party system.

Instead:
- if `SopParty` is present, `SopPillars` uses it
- if absent, `SopPillars` may fall back to solo join behavior or a minimal internal temporary group behavior

Recommended join rules when integrated:
- keep party together if possible
- if one team can fit all, use it
- if no single team can fit all but total arena slots can, split party intelligently
- if total arena slots cannot fit all, reject the whole join

## Configurability

Main config should allow:
- max party size default
- invite timeout
- auto-transfer leader toggle
- party chat format
- party system messages
- join restrictions when already reserved/in game

## Persistence

MVP may be memory-first if acceptable.

Persistent storage may be needed later for:
- reconnect behavior
- long-lived party metadata
- cross-server sync

For initial implementation:
- in-memory state is acceptable
- with clean API boundaries for later persistence

## MVP Boundary

Must-have:
- create/invite/accept/deny/leave/kick/disband
- leader/member roles
- party chat
- integration API for other plugins
- reservation support for game joins

Can wait:
- proxy-wide party sync
- leader-follow automation across all server modes
- GUI menus
- persistent storage
- analytics/statistics

## Relationship to SopPillars

`SopParty` is a separate plugin because:
- party logic is not specific to one mode
- future reuse is expected
- it keeps `SopPillars` smaller and easier to maintain

`SopPillars` should depend on an abstraction so future `SopParty` integration is straightforward.
