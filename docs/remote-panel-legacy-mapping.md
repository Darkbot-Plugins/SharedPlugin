# Remote Panel - DKS Legacy Mapping

This document maps old `RemoteStatsServer` behavior to the new `Remote Panel` flow.

## Protocol Mapping

### Telemetry push

- Legacy:
  - `POST /`
  - Body contains nested structures (`hero`, `stats`, `module`, `map`, etc.).
  - Response is current server command payload (`action`, `id`, `parameter`, `tick`).
- V2:
  - `POST /v1/telemetry`
  - Body is normalized snapshot with `botPublicId` and status fields.

Compatibility in mock backend:
- `POST /` is supported and auto-normalized to V2 telemetry storage.

### Command pull

- Legacy:
  - Usually via websocket `/stream`:
    - `switch:<accountId>`
    - `multiple`
    - `action:<action>:<parameter>`
- V2:
  - `GET /v1/commands/next?botPublicId=<id>`
  - Returns JSON:
    - `requestId`
    - `action`
    - `parameter`

Compatibility in mock backend:
- `POST /v1/legacy/command` with body:
  - `{"command":"switch:12345"}`
  - `{"command":"multiple"}`
  - `{"command":"action:start:"}`
  - `{"command":"action:set_module:npc_killer"}`
- Action commands are mirrored into V2 queue for selected account.

## Action Name Mapping

- `start` -> `START`
- `stop` -> `STOP`
- `module` or `set_module` -> `SET_MODULE`
- `map` or `set_map` -> `SET_MAP`
- `refresh` -> `REFRESH`
- `reset_bot_stats` or `reset_stats` -> `RESET_STATS`

`RemotePanel` parser already normalizes case and `-`/`_`.

## Domain Setup (current plugin)

- `command_url`: set only endpoint, no query needed.
  - Example: `https://your-domain.com/v1/commands/next`
- Plugin auto-adds `botPublicId` query param using pilot username.
- `telemetry_url`: `https://your-domain.com/v1/telemetry`

## Token Policy

- Local/dev: token can be empty.
- Public domain: token must be required server-side.

