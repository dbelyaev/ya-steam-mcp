# Acceptance checklist (spec section 12)

Verified on 2026-10-20 against commit `HEAD` of branch `main`. "Evidence" names the automated check or the command that
was run; everything is reproducible with `./mvnw -B clean verify`.

| # | Item | Status | Evidence |
|---|---|---|---|
| 1 | `./mvnw -B clean verify` succeeds with JDK 25; `java -version` and `mvn -version` confirm it | Done | Run locally on Temurin 25 / Maven 3.10.0 (`.mvn/wrapper`); CI repeats `java -version` and fails unless it says 25 |
| 2 | `java -jar target/steam-mcp-server-*.jar` starts in STDIO mode | Done | `StdioHygieneIT`, `SteamMcpServerIT` start the built jar; also works with no configuration at all |
| 3 | A real MCP client completes handshake and `tools/list` | Done, with a note | The official MCP Java SDK client (`SteamMcpServerIT`) completes both. MCP Inspector was not run (it would download an unpinned npm package); `npx @modelcontextprotocol/inspector --cli java -jar <jar> --method tools/list` can be used to double-check |
| 4 | Exactly the six tools are registered | Done | `SteamToolsContractTest.listTools_...`, `SteamMcpServerIT.listTools_...` |
| 5 | All six tools expose correct input/output schemas, limits and read-only hints | Done, deviation | Input schemas, required arrays and hints asserted. **No output schema**: tools return `CallToolResult` so failures can set `isError` (D7) |
| 6 | All Steam requests are stubbed in automated tests; CI needs no real key | Done | WireMock everywhere; `.github/workflows/ci.yml` has no secrets; `live` tests excluded by default |
| 7 | Privacy restrictions, missing achievements, 429, 5xx, timeouts, malformed JSON are handled | Done | `SteamApiClientTest`, `AchievementServiceTest`, `SecretsLeakTest` (10 failure modes), `SteamMcpServerIT` |
| 8 | An empty `GetOwnedGames` response is not proof of an empty library | Done | `LibraryServiceTest.getLibrary_whenResponseEmpty_...` (`visibilityStatus=UNKNOWN`), empty answers are not cached |
| 9 | `SteamID64` is never rounded and always a string | Done | `SteamIdValidator` (string, unsigned range check), configuration is a string, round trip asserted in `SteamMcpServerIT` |
| 10 | `offset`/`limit` are bounded; no unbounded responses | Done | `LibraryServiceTest`, `RecentGamesServiceTest`, `AchievementServiceTest`, `NewsServiceTest` (bounds), 16 MiB body cap, news text capped |
| 11 | `stdout` contains no logs, banners or stack traces | Done | `StdioHygieneIT` (every stdout line is JSON-RPC, including a 41-request burst), `SourceHygieneTest` |
| 12 | The API key never appears in logs, error responses, Git history or CI artifacts | Done | `SecretsLeakTest`, `SteamApiClientTest`, `SteamPropertiesTest.toString_...`, `SteamMcpServerIT` (results + stderr). History scan: no 32-hex or key-shaped strings; only the synthetic test keys appear. CI uploads only the jar and test reports and uses no secrets |
| 13 | Only the public Steam API host is used | Done | `SteamPropertiesTest` (rejects http, other hosts, `partner.steam-api.com`, userinfo, ports, paths), client tests assert only the five official paths are requested |
| 14 | README covers setup, API key, SteamID64, privacy settings, STDIO configuration, limitations, test commands | Done | `README.md` |
| 15 | `docs/decisions.md` records all deviations from the specification | Done | D1-D20; spec itself updated to v1.3 where the change was intentional (retry policy) |
| 16 | Optional Store enrichment remains disabled | Done | Not implemented; `steam.store.enrichment-enabled=false` is a reserved property with no effect |

## Not verified here

- Calls against the real Steam API (needs the owner's key): run `./mvnw -Plive test` once with your own key and SteamID.
- GitHub Actions workflows were validated as YAML but not executed (no push yet).
- Windows hosts.

## Rollback plan

Keep the previous jar next to the new one and point the MCP host configuration back at it. No data, schema or
state is stored by the server, so there is nothing to migrate or undo.

## Post-release checks (first run with a real key)

1. `get_library` with `limit: 1` returns `visibilityStatus: "VISIBLE"` (if `UNKNOWN`, review the privacy settings).
2. `get_recent_games`, then `get_game_news` for a public AppID such as `440`.
3. Check the host's server log (stderr): lines look like `tool=get_library outcome=ok durationMs=...` and contain no key.
