# Decisions

## D1 — Keep existing package/coordinates (2026-10-19)
Spec suggests `dev.steam.mcp` / `dev.steam:steam-mcp-server`. Repo already scaffolded as `io.dmitriy.yasmcp` / `io.dmitriy:yasmcp`. Kept the repo's. MCP server name stays `steam-mcp-server`.

## D2 — Spring AI 2.0.1 annotation package (2026-10-19)
`@McpTool` / `@McpToolParam` live in `org.springframework.ai.mcp.annotation` (artifact `spring-ai-mcp-annotations` 2.0.1), not `org.springaicommunity.mcp.annotation`.

## D3 — Verified versions (2026-10-19)
Boot 4.1.1 + spring-ai-bom 2.0.1 resolve and `./mvnw verify` passes on JDK 25. Context loads, 1 tool registered, STDIO handshake + tools/list + tools/call OK, stdout clean.

## Open
- Mockito self-attach warning on JDK 25; add Mockito agent to surefire argLine (P4).

## D4 — Explicit tool hints (2026-10-19)
Spring AI defaults to `readOnlyHint=false, destructiveHint=true`. Every tool sets `@McpTool.McpAnnotations(readOnlyHint=true, destructiveHint=false, idempotentHint=true, openWorldHint=true)`; verified in `tools/list`.

## D5 — Plan v2 (2026-10-19)
Pre-mortem found 7 critical issues; see `docs/pre-mortem.md`. Plan rewritten with commit-sized chunks.

## D6 — Config error policy (2026-10-19)
Server always starts offline. Missing/invalid key or SteamID surface at tool-call time as `CONFIGURATION_MISSING` (invalid ID uses same code, message states which setting). Open for review before P1.3.

## D7 — Output schema (2026-10-19)
`generateOutputSchema=false`; text JSON envelope. Revisit with concrete per-tool records.

## D8 — Jar name (2026-10-19)
`finalName` set to `steam-mcp-server-${project.version}` in P5 to match checklist; artifactId stays `yasmcp`.

## D9 — Shared failure type and INTERNAL_ERROR (2026-10-19)
One `SteamException(ErrorCode, fixedMessage)` in `model/` (no cause, no stack trace) is used by validators, config accessors and the client, instead of a client-only `SteamClientException`. `INTERNAL_ERROR` added for the tool catch-all; spec table lacks it.

## D10 — Client status handling (2026-10-19)
HTTP 400 with a parseable envelope is returned to the caller (Steam reports "no stats"/private data this way for `GetPlayerAchievements`); service maps it. Other 4xx, 3xx, empty/oversized/malformed bodies and missing envelope -> `UPSTREAM_BAD_RESPONSE`. Redirects are never followed. 401/403 -> `UPSTREAM_AUTH_FAILED`, no retry.

## D11 — Retry mechanism: Framework 7 `@Retryable` (2026-10-19, supersedes first spike result)
First spike kept a custom loop because `@Retryable` cannot use `Retry-After` (`BackOffExecution.nextBackOff()` and `MethodRetryPredicate` never see the exception) and the spec required it. The spec was then relaxed (section 8, v1.3): retry only I/O errors and 5xx; **429 fails fast** with `UPSTREAM_RATE_LIMITED` (`retryable=true`), `Retry-After` ignored. Adopted class-level `@Retryable(includes=TransientUpstreamException.class, maxRetriesString=${steam.api.retries}, delayString=${steam.api.retry-delay}, multiplier=2, jitter=50ms, maxDelay=2s, timeoutString=${steam.api.total-timeout})` on `SteamApiClientImpl`. `TransientUpstreamException` (UPSTREAM_UNAVAILABLE) is the only retried type. Verified: exhausted retries surface the last exception unwrapped (same `SteamException` code, no cause); 3 requests on persistent 5xx, 1 on 401/429.
Consequences: client tests that exercise retries run through a small Spring context (`ResilientClients` test helper) because the annotation needs a proxy; exact sleep/jitter assertions are gone, replaced by request-count assertions with `steam.api.retry-delay=1ms`. Custom loop, `Sleeper`, `Clock` seam and `Retry-After` parsing were deleted.

## D11a — Advice order: concurrency limit below retry (2026-10-19)
With `@Retryable` and `@ConcurrencyLimit` on the same bean, the limit wrapped the whole retry (measured: two calls with limit 1 and a 400 ms retry delay took 1031 ms, i.e. serialized; sleep held the slot). Order between the two annotations cannot be controlled. Fix by structure: `ConcurrencyLimitedSteamWebApi` (class-level `@ConcurrencyLimit`, `@Primary`, wraps the registry-created `SteamWebApi`) so layering is retry -> limit -> HTTP. `SteamApiClientAdviceOrderTest` pins it (440 ms).

## D12 — Declarative HTTP client adopted (2026-10-19, spike B done)
Go. `SteamWebApi` (`@HttpExchange`, returns `ResponseEntity<byte[]>`) is registered as group `steam` via `@ImportHttpServices`; `SteamApiClientImpl` keeps parsing, retry and error mapping on top. Transport is set by a `RestClientHttpServiceGroupConfigurer` from `steam.api.*`: allowlisted base URL, explicit timeouts, redirects off, permissive default status handler (no status exceptions that embed URI/body). Verified in a full Boot context (`SteamClientBootWiringTest`): configured read timeout and no-redirect hold, and a foreign `spring.http.serviceclient.steam.base-url` cannot override the validated `steam.api.base-url`. All 24 WireMock client tests unchanged in behavior.
Known cost: the 16 MiB body cap is now applied after the body is read (the response is buffered as `byte[]`), not while streaming. Accepted: single trusted host, local single-user process.
The `spring.http.clients.*` / `spring.http.serviceclient.steam.*` properties are intentionally ignored for this group; `steam.api.*` is the only public transport configuration (documented in README at P5).

## D13 — Service split (2026-10-19)
No `SteamService`/`SteamServiceImpl`. Capability services (`LibraryService`, `AchievementService`, `NewsService`, `GameDetailsService`) without interfaces; interface kept on `SteamApiClient` only. No Lombok (explicit loggers).

## D14 — Config strictness vs skill (2026-10-19)
configuration-properties says missing credentials should fail startup; spec 4.3 demands offline start and call-time `CONFIGURATION_MISSING`. Spec wins (D6).

## D15 — P2 contract details (2026-10-19)
- `visibilityStatus` is `VISIBLE` (Steam returned at least one game, before filtering) or `UNKNOWN` (empty or missing games list). Spec example shows `UNKNOWN` next to a populated list but also demands no claim without evidence; `VISIBLE` only when evidence exists.
- `iconUrl` is always null: Steam returns only an icon hash and the image URL pattern is not part of the documented Web API.
- Missing `playtime_forever` stays null in output; for sorting and the `includeNeverPlayed=false` filter it counts as 0.
- `name` can be null; null names never match a `query` and sort as empty string. Ties break by name then appid so paging is stable.
- `limit` for `get_recent_games` is also applied locally, in case Steam returns more than `count`.
- Defaults and validation live in the services (`LibraryQuery`), tools forward raw nullable arguments.
- `get_playtime` is hosted by `LibraryService` (same data); `LibraryService.findOwned` is reused by `get_game_details` in P3.
- A missing required `appid` is rejected by the MCP framework before our code runs (generic framework error, not our envelope). To be confirmed and documented in the E2E test.

## D16 — Spring AI 2.0.1 always starts a STDIO transport (2026-10-19)
`spring.ai.mcp.server.stdio` has no effect for non-web applications: `stdioServerTransport` is registered whenever no other `McpServerTransportProviderBase` bean exists (verified with `ApplicationContextRunner` for true/false/unset). Fine for production (STDIO only), but in Spring tests it read System.in inside the surefire fork and hung JVM exit for 30 s. Tests use `@McpSpringBootTest`, which imports `QuietMcpTransport` (a stdio provider on null streams) so the auto-configured one backs off. Keep `stdio: true` in `application.yml` as documentation. Never add a web starter: it would change transport selection.
