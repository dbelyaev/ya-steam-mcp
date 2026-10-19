# Implementation Plan (v3, post pre-mortem and skill review)

Source: `.tasks/steam-mcp-server-spec.md`. Mitigations: `docs/pre-mortem.md` (#N). Skill findings: `docs/skill-review.md` (S#N). Base package `io.dmitriy.yasmcp`; sub-packages `config`, `tool`, `service`, `client`, `model`, `steamdto`, `support`.

**Global rules**
- Every `@McpTool` returns `CallToolResult` via `ToolResults.run(toolName, ...)` with catch-all (#2, #3). Optionals: `required=false`, boxed types; `sortBy`/`language` `String`; `appid` `Long` (#4, #5). `generateOutputSchema=false` (D7, S#7).
- Layering: tool (thin: parse, delegate) -> service (logic, no Steam JSON leaks) -> client. Constructor injection only. Public DTOs are records with `from(...)` factories (S#13, S#14).
- Every package `@NullMarked`; `@Nullable` only on real exceptions (S#10).
- No `System.out` in `src/main` (guarded by test). Jackson annotations `com.fasterxml.jackson.annotation`; databind/core `tools.jackson` (#10).
- Tests: `method_condition_expected` naming, Given/When/Then; unit (Mockito) > client (WireMock) > one E2E IT (S#17).
- Small conventional commits, `./mvnw verify` green before each. Searches and `verify` runs delegated to Haiku `search`/`grep`/`audit` subagents per CLAUDE.md.
- Before each phase, read the skills it names.

## P0, P1 — done

## P1R — Skill alignment of existing sources (skills: configuration-properties, mcp-server, null-safety, testing-pyramid, resilience-retry, http-interface-clients)
Low risk first; spikes last, each with a go/no-go and an ADR.
1. `fix: redact API key in SteamProperties toString` (S#1) + test. **Security, first.**
2. `refactor: validate SteamProperties with Jakarta constraints` (S#2): `@Validated`, `@Min/@Max`, `@DurationMin/@DurationMax`; keep cross-field base-URL invariant; add env-var binding test (`STEAM_API_KEY`, `STEAM_ID`) (S#4); quote `banner-mode: "off"` (S#5). Record S#3 keep in decisions.
3. `refactor: mark packages @NullMarked` (S#10): jspecify dependency, `package-info.java`, `@Nullable` on DTO components, `Outcome.body`, `retryAfter()`. NullAway: try with Error Prone on JDK 25; if build breaks, drop and record.
4. `test: align test names and add Mockito agent` (S#17, S#18).
5. `feat: log tool name, outcome and duration` (S#6): `ToolResults.run(toolName, action)`, one INFO line, no arguments or data.
6. **Spike A — resilience** (S#11, S#8): prove on a branch whether `@Retryable` (+ `timeout` as overall deadline, `delayString` placeholders from `steam.api.*`) can replace the loop while keeping: retry only I/O/5xx/429, exact retry counts, `Retry-After` honored (seconds + date, cap 10 s), no real sleeping in tests. Go => replace loop, adopt `@EnableResilientMethods`; No-go => keep loop, add overall deadline manually, ADR D11. `@ConcurrencyLimit(4)` on the client either way (moves from P4).
7. **Spike B — declarative client** (S#12): `@HttpExchange` `SteamWebApi` with `ResponseEntity` returns, `@ImportHttpServices(group="steam")`, permissive default status handler via group configurer, redirects off, allowlist enforced on resolved base URL, keep all 23 WireMock tests green through the public `SteamApiClient`. Go => commit; No-go => keep manual client, ADR D12.
Gate: `verify` green, 23 client tests unchanged in behavior.

## P2 — Core tools (skills: mcp-server, layered-architecture, testing-pyramid)
1. `feat: add LibraryService` (filter, sort, paginate, `visibilityStatus=UNKNOWN` on empty; model records with `from(...)`); Mockito unit tests for filter/sort/pagination edges.
2. `feat: add get_library tool` (hints, `required` arrays asserted in a tool test).
3. `feat: add get_playtime tool`.
4. `feat: add get_recent_games tool`.
Invalid values `0`, `-1`, `4294967296`, non-numeric -> `INVALID_ARGUMENT`.

## P3 — Rich tools (skills: layered-architecture, mcp-server, testing-pyramid; decisions fixed #13)
1. `feat: add get_achievements` (`AchievementService` + `AchievementMapper` schema join; `success:false`/400 -> `ACHIEVEMENTS_UNAVAILABLE`/`DATA_NOT_ACCESSIBLE`; enrichment failure -> `partial`).
2. `feat: add get_game_news` (`NewsService`, `NewsSanitizer` in `support/`, https-only url, epoch->ISO, `maxContentChars=0` omits body and `maxlength`).
3. `feat: add get_game_details` (`GameDetailsService`; unowned -> partial object, `meta.partial=true`).
Language: allowlist miss -> `INVALID_ARGUMENT`.

## P4 — Hardening (skills: mcp-server, resilience-retry, testing-pyramid)
1. `feat: add caching decorator` Caffeine direct, per-endpoint TTL, success-only, `cached` flag, no key in cache key (#12). Disabled in tests.
2. `test: add secrets and stdout hygiene tests` incl. `System.out` ban, `toString` and log leak checks.
3. `test: add STDIO end-to-end IT` failsafe, jar via system property, SDK `McpClient` + `StdioClientTransport`, `-XX:+DisplayVMOutputToStderr`, WireMock in IT with insecure-base-url override; assertions per #7 plus invalid-arguments call and stdin-close clean shutdown (S#9).
(Concurrency limit and Mockito agent moved to P1R.)

## P5 — Release
README (key, SteamID64, privacy, host config with `-XX:+DisplayVMOutputToStderr`, limits, test cmds, **table of public property keys and env vars** S#4), `docs/api-mapping.md`, `docs/test-plan.md`, `finalName` `steam-mcp-server-${project.version}`, `live` test tag (off by default), GitHub Actions (JDK 25, `java -version`), LICENSE choice, acceptance checklist pass.
