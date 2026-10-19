# Implementation Plan (v2, post pre-mortem)

Source: `.tasks/steam-mcp-server-spec.md`. Mitigations: `docs/pre-mortem.md` (items cited as #N). Base package `io.dmitriy.yasmcp`; sub-packages `config`, `tool`, `service`, `client`, `model`, `steamdto`, `support`.

**Global rules**
- Every `@McpTool` returns `CallToolResult` through one helper and wraps its body in catch-all `Throwable` (#2, #3). Success `isError=false`; failure `{"error":{code,message,retryable}}`, `isError=true`.
- Optional params: `required=false`, boxed types. `sortBy`/`language` are `String`; `appid` is `Long` (#4, #5).
- `generateOutputSchema=false` (#14).
- Never `System.out`; guarded by test (#8).
- Jackson annotations stay `com.fasterxml.jackson.annotation`; databind/core are `tools.jackson` (#10).
- Small commits, conventional messages, one logical chunk each; `./mvnw verify` green before each commit.

## P0 — done
Bootstrap, stub tool, read-only hints (D4).

## P1 — Steam client (commits in order)
1. `build: add wiremock-standalone test dep`; run verify before any stub test (#6).
2. `fix: silence logback status output`; `NopStatusListener` (#8). Migrate stub `getLibrary` to `CallToolResult` helper + `ToolResponse`/`ToolError` (#3).
3. `feat: add SteamProperties with validation`; startup rules: empty key/ID start fine, tools return `CONFIGURATION_MISSING`; malformed ID handled at call time (#9); base-URL allowlist + `allow-insecure-test-base-url` (#1). Tests: prod rejects http/other hosts/partner host.
4. `feat: add SteamId and AppId validators`.
5. `feat: add Steam upstream DTOs`.
6. `feat: add SteamApiClient with retry and error mapping`: Boot `RestClient.Builder`, explicit timeouts, `UriBuilder` (#10); injected `Sleeper`; Retry-After capped 10s; retry IO/5xx/429 only; 401/403 -> `UPSTREAM_AUTH_FAILED`; all exceptions wrapped, generic messages (#2, #11).
7. `test: add WireMock coverage for client`: paths/versions/params (`include_appinfo=true`, `include_played_free_games=true`), empty/missing `games`, 429, 5xx, timeout, malformed JSON, request counts (3 / 1), zero requests to partner host, key/body leak test.

Gate: stub tests pass.

## P2 — Core tools
1. `feat: add SteamService and library filter/sort/paginate` (`visibilityStatus=UNKNOWN` on empty).
2. `feat: add get_library tool` (+ real hints, required arrays asserted).
3. `feat: add get_playtime tool`.
4. `feat: add get_recent_games tool`.
Tests: unit + stub; invalid values `0`, `-1`, `4294967296`, non-numeric -> `INVALID_ARGUMENT`.

## P3 — Rich tools (decisions fixed, #13)
1. `feat: add get_achievements` (schema join; `success:false`/400 -> `ACHIEVEMENTS_UNAVAILABLE`/`DATA_NOT_ACCESSIBLE`; enrichment failure -> `partial`).
2. `feat: add get_game_news` (sanitizer, https-only url, epoch->ISO, `maxContentChars=0` omits body and `maxlength`).
3. `feat: add get_game_details` (unowned -> partial object, `meta.partial=true`).
Language: allowlist miss -> `INVALID_ARGUMENT`.

## P4 — Hardening
1. `feat: add caching decorator` Caffeine direct, per-endpoint TTL, success-only, `cached` flag, no key in cache key (#12). Disabled in tests.
2. `feat: bound outbound concurrency` semaphore(4) around HTTP call only.
3. `test: add secrets and stdout hygiene tests` incl. `System.out` ban.
4. `test: add STDIO end-to-end IT` failsafe, jar via system property, SDK `McpClient` + `StdioClientTransport`, `-XX:+DisplayVMOutputToStderr`, WireMock in IT with insecure-base-url override, assertions per #7.
5. `build: add Mockito agent argLine`.

## P5 — Release
README (key, SteamID64, privacy, host config with `-XX:+DisplayVMOutputToStderr`, limits, test cmds), `docs/api-mapping.md`, `docs/test-plan.md`, `finalName` `steam-mcp-server-${project.version}`, `live` test tag (off by default), GitHub Actions (JDK 25, `java -version`), LICENSE choice, acceptance checklist pass.
