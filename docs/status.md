# Status
- Done: P0 bootstrap; P1 Steam client (properties + base-URL allowlist, validators, upstream DTOs, `SteamApiClientImpl` with retry/error mapping, 23 WireMock tests).
- Next: P2 core tools (SteamService, get_library, get_playtime, get_recent_games).
- Blockers: none.
- Notes: `SteamException(ErrorCode, msg)` in `model/` is the single public failure type (client, validators, config). `INTERNAL_ERROR` added as catch-all code (not in spec table).
