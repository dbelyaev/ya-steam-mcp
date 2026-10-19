# Implementation Plan

Source: `.tasks/steam-mcp-server-spec.md`. Base package `io.dmitriy.yasmcp`; sub-packages per spec 3.1 (`config`, `tool`, `service`, `client`, `model`, `steamdto`, `support`).

| Phase | Work | Gate |
|---|---|---|
| P0 done | pom (Spring AI 2.0.1, restclient, validation), application.yml, logback to stderr, stub `get_library` | verify green; STDIO handshake clean |
| P1 | `SteamProperties` (+startup validation), `SteamIdValidator`, `AppIdValidator`, `SteamApiClient` (RestClient, timeouts, retry/backoff+jitter, Retry-After, key redaction), upstream DTOs, `SteamClientException`/error mapping, WireMock tests | stub tests pass |
| P2 | `SteamService`, `get_library` (filter/sort/paginate, `visibilityStatus=UNKNOWN`), `get_playtime`, `get_recent_games`, `ToolResponse`/`ToolError` envelope, read-only hints | unit + stub tests |
| P3 | `get_achievements` (schema join, partial), `get_game_news` (sanitize, https-only), `get_game_details` (lib + schema; documented fallback) | partial/missing-field tests |
| P4 | Caffeine cache + TTLs, concurrency limit (4), secrets-leak tests, STDIO E2E child-process test (exactly 6 tools, stdout pure, failure isolation) | `verify` green |
| P5 | README, GitHub Actions (JDK 25), LICENSE choice, acceptance checklist | checklist 12 |

Risks: Boot4/Jackson 3 `tools.jackson` imports; key in query string leaking via RestClient logs/exceptions; missing key must not block startup; `appid` as `long`; Steam privacy ≠ empty library.
