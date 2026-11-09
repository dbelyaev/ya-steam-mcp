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

## D8 — Jar name (2026-10-19, done in P4)
`finalName` is `steam-mcp-server-${project.version}` to match the checklist; artifactId stays `yasmcp`.

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
- Confirmed on the packaged jar: a missing or wrongly typed argument (`appid` absent, `"abc"`) is rejected by the MCP framework's JSON-schema validation with `isError=true` and a plain text message (`Tool (get_playtime) input validation failed: ... required property 'appid' not found`), before our code and our envelope run. Acceptable: still a tool-level error with a clear reason, no secrets. Range errors (0, -1, 4294967296, limit 0) do reach the service and return `INVALID_ARGUMENT`. The E2E test asserts both paths.

## D16 — Spring AI 2.0.1 always starts a STDIO transport (2026-10-19)
`spring.ai.mcp.server.stdio` has no effect for non-web applications: `stdioServerTransport` is registered whenever no other `McpServerTransportProviderBase` bean exists (verified with `ApplicationContextRunner` for true/false/unset). Fine for production (STDIO only), but in Spring tests it read System.in inside the surefire fork and hung JVM exit for 30 s. Tests use `@McpSpringBootTest`, which imports `QuietMcpTransport` (a stdio provider on null streams) so the auto-configured one backs off. Keep `stdio: true` in `application.yml` as documentation. Never add a web starter: it would change transport selection.

## D17 — P3 contract details (2026-10-19)
- **Language**: fixed allowlist of Steam identifiers (`SteamLanguage`); null/blank uses `steam.api.default-language`; anything else is `INVALID_ARGUMENT` (no silent fallback). The message never echoes the input.
- **Achievements**: Steam `success:false` or missing envelope -> `ACHIEVEMENTS_UNAVAILABLE`, or `DATA_NOT_ACCESSIBLE` when Steam's error text mentions a non-public/private profile; messages are fixed, Steam text is not echoed. `success:true` with no list -> `available=false`, zero counts, a warning, not partial: zero is not implied. Schema failure or empty schema -> `meta.partial=true` plus warning; texts fall back to the player's own `name`/`description`, `hidden` stays null. Schema text wins over player text (same language). `total`/`unlocked` count all achievements; `returned`/`truncated` describe the page after `includeLocked` and `limit`. Steam order is kept. Entries without `apiname` are skipped.
- **News**: all text is reduced to plain text (HTML/BBCode/Steam placeholders/script/style removed, `&lt;`/`&gt;` left encoded so no tag can be re-created, control and bidi characters stripped); only absolute `https` links without credentials survive; `date` -> ISO UTC. Requests to Steam use `maxlength = min(3 * maxContentChars, 12000)` because markup is longer than its text; the preview is cut again to `maxContentChars`. `maxContentChars=0` omits the body and asks Steam for `maxlength=1`, since Steam treats 0 (or an omitted value) as full content (improves pre-mortem item 13, which said to omit it).
- **Game details**: policy chosen and fixed: library hit and/or schema data -> object (unowned -> `ownedOrVisibleInLibrary=false`, `meta.partial=true`, warning). No library hit and no schema data -> `GAME_DETAILS_UNAVAILABLE`. No library hit and the schema call itself failed -> that (retryable) upstream error, not "unavailable". Schema failure with a library hit -> partial plus warning. `description`, `genres`, `releaseDate`, `iconUrl` stay null.
- The six MVP tools are asserted exactly in `SteamToolsContractTest`.

## D18 — SDK STDIO transport drops concurrent responses; handlers run serially (2026-10-19)
MCP Java SDK 2.0.0 `StdioServerTransportProvider` writes through `Sinks.many().unicast()`, which is not safe for concurrent emitters. With the default scheduling (handlers on Reactor `boundedElastic`) pipelined `tools/call` requests finish on different threads and some responses die with `Failed to enqueue message`; the client never gets them. Reproduced with the built jar: 15 of 41 pipelined requests answered. Fix: a `McpSyncServerCustomizer` sets `immediateExecution(true)` (Spring AI sets it only for servlet servers), so every handler runs on the single transport thread. Cost: tool calls are strictly serial, and a long call (bounded by `steam.api.total-timeout`) delays later requests, acceptable for a local single-user server. `StdioHygieneIT.pipelinedBurst_shouldAnswerEveryRequestExactlyOnce` pins it (fails with 15 of 41 without the fix). `@ConcurrencyLimit` stays: it is not reachable concurrently over STDIO, but it is kept for HTTP hosting (see D24). Revisit when the SDK fixes the sink or if HTTP transport is added.

## D19 — Cache (2026-10-19)
`CachedSteamApiClient` (Caffeine, per-endpoint TTL from `steam.cache.*`, spec defaults 5m/2m/10m/24h/10m) wraps the retrying client (`@Primary`; the retrying bean is `steamApiClientDelegate`). Only successful answers that look complete are stored: never errors, empty owned-games, `success:false` or empty achievements, empty schema. Keys: SteamID, AppID, language, counts, never the API key. `meta.cached` is true only when every upstream read of the call was a cache hit; the signal travels via a `ScopedValue` (`CacheTrace`) bound in `ToolResults.run`, so service signatures are unchanged. No stale-while-error. `steam.cache.enabled=false` disables it.

## D20 — Tests in the surefire fork and integration tests (2026-10-19)
`*IT` classes run with failsafe after `package` against `target/ya-steam-mcp-<version>.jar` (`e2e.jar` system property), with WireMock as Steam and `STEAM_API_BASE_URL` / `STEAM_API_ALLOW_INSECURE_TEST_BASE_URL` / `STEAM_API_RETRY_DELAY` as child environment (relaxed binding). Child JVMs use `-XX:+DisplayVMOutputToStderr`.

## D21 — Release packaging and license (2026-10-20)
Apache License 2.0 (`LICENSE`, `NOTICE`; owner decision). Version `0.1.0-SNAPSHOT`; the MCP `serverInfo.version` is filtered from the pom (`@project.version@`). Optional `live` tests are tagged and excluded through surefire `excludedGroups`, enabled by `-Plive`; a manual-only workflow (`live.yml`) can run them with repository secrets. CI (`ci.yml`) runs `clean verify` on JDK 25 with read-only permissions and no secrets. Runtime dependency licenses reviewed in `docs/dependency-licenses.md`.

## D22 — Deviations from the specification, summary (2026-10-20)
Intentional, each with details above: package/coordinates (D1), annotation package (D2), `@Retryable` and no server-side 429 retry or `Retry-After` handling, spec updated to v1.3 (D11, D11a), declarative HTTP client (D12), capability services instead of one `SteamService` (D13), no startup failure on missing credentials (D14, D6), `VISIBLE`/`UNKNOWN` visibility values and null `iconUrl` (D15), serial MCP handling because of the SDK STDIO sink (D18), no tool output schemas (D7), `maxlength=1` for a news body of 0 characters (D17). None changes a security invariant of the specification.

## D23 - Project renamed to ya-steam-mcp (2026-10-21)
The project and repository were renamed from `yasmcp` to `ya-steam-mcp` (owner decision). Superseded names in earlier entries (D1, D8, D20) are kept as history.
- Maven artifactId `ya-steam-mcp` (groupId unchanged, `io.dmitriy`); jar `ya-steam-mcp-<version>.jar`; Spring application name and MCP `serverInfo.name` `ya-steam-mcp`.
- Java package `io.dmitriy.yasteammcp` and application class `YaSteamMcpApplication` (a hyphen is not legal in a package name); files moved with `git mv`.
- `.tasks/steam-mcp-server-spec.md` keeps its name: it is the original requirements document.
- Host configurations only break if they hard-code the old jar path (`steam-mcp-server-*.jar`); the host-side server key (for example `steam`) is chosen by the user and is unaffected.
- Not changed by this PR: the GitHub repository name (renamed separately after merge; GitHub redirects the old URL), the SonarCloud project (its key was changed by SonarCloud itself to `dbelyaev_ya-steam-mcp` when the repository was renamed; verified 2026-10-21 through the public API), the local folder name.

## D24 - HTTPS hosting planned (2026-11-07)
The owner plans to host the server on the internet sometimes. Not implemented; prerequisites and target setup are in `docs/hosting.md`. `@ConcurrencyLimit` (D11a) and `SteamIdValidator.requireValid` are kept for it even though STDIO does not need them.

## D25 - Response size cap enforced while reading (2026-11-08)
The 16 MiB cap used to be checked after the whole body was in a `byte[]`, so four concurrent calls could hold 64 MiB+ from a hostile upstream. `BoundedResponseInterceptor` (a `RestClient` interceptor installed in `SteamHttpConfig.customize`) now rejects a declared `Content-Length` above the cap before reading, and otherwise wraps the body stream so it aborts at cap + 1 byte (chunked or lying upstream); at most one read chunk beyond the cap is ever buffered. The failure is an unchecked `ResponseTooLargeException` with a fixed message, not an `IOException`, so it maps to `UPSTREAM_BAD_RESPONSE` and is not retried (an I/O error would be treated as transient). A body of exactly 16 MiB is accepted. A lower per-endpoint cap was not added: the cap is one constant for all five endpoints and `GetOwnedGames`/`GetSchemaForGame` can legitimately be large.
Spring's `JdkClientHttpResponse.close()` drains the remaining body before closing (verified with `javap`: `StreamUtils.drain`), which would defeat the cap, so the raw body stream is closed first on both rejection paths (declared length, stream overflow).

## D26 - Sanitized Steam text beyond news (2026-11-08)
Game names, schema game names, achievement display names and descriptions are untrusted text that reaches the agent, so a crafted title could carry prompt injection or bidi spoofing. `support/TextSanitizer.plainText` is applied in the mapping layer (`GameSummary.from`, `RecentGame.from`, `AchievementMapper`, the schema name in `GameDetailsService`): it makes the text single-line, strips control, zero-width and bidi-override characters (the same primitives `NewsSanitizer` now reuses) and caps the length, with an ellipsis when cut. Caps: names 200 and descriptions 500 code points, far above real Steam values. Markup is deliberately not parsed (a title like `<Game>` stays intact; it is data, not instructions). A value that is empty after cleaning becomes null, so achievement text falls back to the player value as before. CJK, emoji and punctuation are preserved.

Follow-up to D26 (review): the hidden-character filter is category based instead of a range list. Control characters (except newline and tab, which `NewsSanitizer` needs) and every Unicode format character (Cf) are stripped: zero width space and non-joiner, bidi marks, embeddings, overrides and isolates (including U+061C), word joiner, invisible operators, soft hyphen, BOM, and the U+E0000 tag block (an invisible ASCII smuggling vector). U+200D (zero width joiner) is kept only between two extended pictographs, looking back over U+FE0F and skin tone modifiers, so emoji sequences such as a woman technologist or a family survive; a joiner anywhere else is stripped. Text that is only invisible characters is empty, so it becomes null and achievement text falls back to the player value. Trade-offs: the England, Scotland and Wales flags lose their tag characters and show as a plain black flag; the zero width non-joiner is stripped, which can alter correct rendering of some Persian and Indic words. `NewsSanitizer` shares this code, so news text is stricter for Cf characters and now also keeps ZWJ inside emoji sequences (previously stripped).

Second follow-up to D26 (review): `TextSanitizer.plainText` is a single streaming pass over code points (no `int[]` copy of the input) that stops once more than the cap of output code points exist, so memory and time depend on the cap, not on a response of up to 16 MiB; whitespace runs of any kind collapse to one space. A result needs at least one visible base character (not a mark, selector, separator, control, format, unassigned, private-use or surrogate), so text made only of U+034F, variation selectors or combining marks is null and the achievement fallback applies; marks and selectors attached to a base are kept. `NewsSanitizer` now strips hidden characters first, before the script, comment, tag and BBCode filters; each of those replaces what it removes with a space, so a later step cannot glue fragments into new markup (previously `<U+2060script>` became a live `<script>` after the filters ran).

Third follow-up to D26 (review): the output cap alone did not bound scanning (a long run of spaces or stripped characters adds no output), so `plainText` also has a scan budget of `clamp(8 * maxChars, 1024, 65536)` input code points (1600 for names, 4000 for descriptions), counting every code point read. When it is spent the text is treated as truncated: the cleaned text collected so far is returned with an ellipsis, or null if no visible base character was seen. Marks and selectors with no visible base before them (leading, or after only whitespace and stripped characters) are dropped without counting toward the cap, so 200 leading U+034F no longer hide the title; marks after a base are kept. Any whitespace or Unicode space separator (NBSP, figure space, narrow NBSP, ideographic space) counts as a space and runs collapse to one. `NewsSanitizer` is unaffected by the budget and marks rules (it uses only `stripHidden`).

Fourth follow-up to D26 (review: Hangul fillers counted as visible): "invisible" now has one standard definition instead of hand-picked categories. `support/InvisibleCodePoints` holds the Unicode `Default_Ignorable_Code_Point` table (UCD 16.0.0, `https://www.unicode.org/Public/16.0.0/ucd/DerivedCoreProperties.txt`, the Unicode version of the JDK 25 `Character` data; re-check the table whenever the JDK or Unicode version is upgraded) plus a best-effort blank-glyph list (currently U+2800 only). `TextSanitizer` strips control, format, default ignorable and blank-glyph code points through one shared path used by `plainText`, `stripHidden` and so `NewsSanitizer`, with the same two exceptions as before: ZWJ between extended pictographs, and variation selectors/combining marks directly after a visible base (orphaned or leading ones are dropped). `isVisibleBase` is the single "has visible content" predicate. The Hangul fillers (U+115F, U+1160, U+3164, U+FFA0) are default ignorable and therefore stripped everywhere, so a filler-only name is null and the achievement fallback applies; trade-off: Old Hangul jamo sequences that rely on fillers lose them, irrelevant for Steam names. U+034F and Khmer U+17B4/U+17B5 are now stripped even after a base. Non-goal: this is hidden-text hygiene, not a prompt-injection defence; visible injected text still passes, and the mitigations are the length caps, read-only tools, and clients treating tool output as untrusted. Stop rule: a future invisible-character finding is a table entry plus a test, not a new mechanism.
