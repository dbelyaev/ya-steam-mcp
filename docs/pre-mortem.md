# Pre-mortem (2026-10-19)

Verdict on v1 plan: NEEDS_REVISION. Findings verified against Spring AI 2.0.1 / MCP SDK 2.0.0 jars. All items folded into `implementation-plan.md` (v2). Phase tags in brackets.

## Critical
1. [P1] Base-URL allowlist (spec 4.3) blocks WireMock `http://localhost`. Mitigation: `steam.api.allow-insecure-test-base-url` (default false) permits `http://localhost|127.0.0.1` only; E2E child gets it via env. Tests: prod config rejects `http://`, other hosts, `partner.steam-api.com`.
2. [P1/P2] Uncaught exception message + root cause is copied into tool result; Spring `ResourceAccessException` embeds URI with `key`, `HttpClientErrorException` embeds upstream body. Mitigation: client wraps all `RestClientException`/`JacksonException` into `SteamClientException(code, retryable)` with fixed generic message and no URI-bearing cause; every tool catches `Throwable`. Test: reset/timeout/401-with-body leaks neither key nor body to result or stderr.
3. [P2] Envelope `error` returned as POJO gives `isError=false`. Mitigation: all tools return `CallToolResult` via one helper (success `isError=false`, failure `isError=true`), serialized with Boot `JsonMapper`. Stub `getLibrary` migrated first.
4. [P2] `@McpToolParam.required` defaults true. Mitigation: `required=false` on optionals, boxed types, service applies defaults. `tools/list` test asserts each `required` array.
5. [P2] Conversion failures (enum/long) bypass `INVALID_ARGUMENT`. Mitigation: `sortBy`/`language` as `String` validated in `support/`; `appid` as `Long`. Tests 0, -1, 4294967296, non-numeric.
6. [P1] WireMock 3 (Jetty 11) vs Boot-managed Jetty 12.1. Mitigation: `org.wiremock:wiremock-standalone` test scope, explicit 3.x; verify green before writing stub tests; no `wiremock-spring-boot` unless verified.
7. [P4] E2E cannot run in surefire (no jar before `package`). Mitigation: failsafe + `*IT`, jar path via system property, SDK `McpClient.sync(StdioClientTransport)`, raw stdout lines all parse as JSON-RPC, sorted tool set == 6 names, error call then good call, key absent from stdout/stderr.

## Improvements
8. [P0/P1/P4] Other stdout writers: logback status (`NopStatusListener`), HotSpot warnings (`-XX:+DisplayVMOutputToStderr` in README and E2E), ban `System.out` in `src/main` by test.
9. [P1] Empty key/ID: server still starts, tools return `CONFIGURATION_MISSING`. Malformed `STEAM_ID`: server starts, tools return `CONFIGURATION_INVALID`-class error at call time (documented as `CONFIGURATION_MISSING` family; see decisions D6). `contextLoads` passes with no env.
10. [P1] RestClient from Boot `RestClient.Builder`; explicit 3s/10s via `ClientHttpRequestFactoryBuilder`/`HttpClientSettings`; `UriBuilder` + path constants; Jackson annotations stay `com.fasterxml.jackson.annotation`, only databind/core are `tools.jackson`; keep `org.springframework.web.client` at INFO+, no `ObservationRegistry`.
11. [P1] Retry testable: injected `Sleeper`/`Clock`; `Retry-After` seconds + HTTP-date, capped 10s; retry only IO/5xx/429; counts exactly 3 on persistent 5xx, 1 on 401; 401 and 403 both `UPSTREAM_AUTH_FAILED`.
12. [P4] Cache: `spring-boot-starter-cache` + Caffeine; decorator `CachedSteamApiClient` using Caffeine directly (gives `cached` flag), per-endpoint TTL, success DTOs only, keys without API key; semaphore(4) around HTTP call only; disabled in tests.
13. [P3] Settle before coding: details-for-unowned = partial object (`meta.partial=true`); language = `INVALID_ARGUMENT` on allowlist miss; achievements `success:false`/400 -> `ACHIEVEMENTS_UNAVAILABLE`/`DATA_NOT_ACCESSIBLE`, never 0/0; news `maxContentChars=0` omits body and omits Steam `maxlength`, `date` epoch->ISO UTC, non-https url -> null.
14. [P2/P3] `generateOutputSchema=false`; envelope is text JSON (+structuredContent optional). Revisit only with concrete per-tool records.

## Minor
- [P5] jar name: set `finalName` `steam-mcp-server-${project.version}`.
- [P5] add `docs/api-mapping.md`, `docs/test-plan.md`, `live` test tag (off by default), CI `java -version`.
- [P4] Mockito agent `argLine`.
