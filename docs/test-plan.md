# Test plan

`./mvnw -B clean verify` runs everything below on JDK 25 without a Steam key or network access.
Result at release candidate: **264 unit/Spring tests (surefire) + 14 end-to-end tests (failsafe)**, 4 live tests skipped.

| Layer | Where | What it proves | Tools |
|---|---|---|---|
| Unit | `support/`, `service/`, `tool/ToolResultsTest`, `tool/SteamToolsTest`, `config/SteamPropertiesTest` | Validation bounds (appid 0, -1, 4294967296; limits; offsets; language allowlist), filter/sort/paging, news sanitizing, achievement join, error envelopes, config binding and base-URL allowlist, key redaction in `toString` | JUnit 5, AssertJ, Mockito |
| Client | `client/SteamApiClientTest`, `CachedSteamApiClientTest`, `SteamApiClientConcurrencyTest`, `SteamApiClientAdviceOrderTest` | Paths, API versions, query parameters; empty/missing/malformed bodies; 400/401/403/429/5xx; exact retry counts; timeouts, resets, redirects; total deadline; concurrency limit below the retry; cache TTLs, success-only caching | WireMock standalone, Spring proxies, fake ticker |
| Spring | `client/SteamClientBootWiringTest`, `tool/SteamToolsContractTest`, `tool/SecretsLeakTest`, `tool/ToolCachedMetaTest`, `YasmcpApplicationTests` | Boot does not override transport settings, `tools/list` schemas and read-only hints, six exact tools, no key/body/URL in any result or log under ten failure modes, `meta.cached`, startup without credentials | `@McpSpringBootTest` |
| Source | `SourceHygieneTest` | No `System.out`/`System.err`/`printStackTrace` in production code, logback to stderr, banner off, no hard-coded credentials | file scan |
| End to end | `e2e/SteamMcpServerIT`, `e2e/StdioHygieneIT` | The built jar over real STDIO: handshake, six tools, cache, failure isolation, retry count, argument validation, stdout holds only JSON-RPC, no secrets in stdout/stderr, offline start, clean exit on stdin close, pipelined bursts answered | MCP SDK client, raw child process, failsafe |
| Live (opt-in) | `LiveSteamSmokeTest`, tag `live` | Real Steam answers parse; assertions use counts, flags and codes only | `STEAM_API_KEY=... STEAM_ID=... ./mvnw -Plive test` |

## Commands

| Goal | Command |
|---|---|
| Everything (CI) | `./mvnw -B clean verify` |
| Unit and Spring tests only | `./mvnw test` |
| One class | `./mvnw -Dtest=LibraryServiceTest test` |
| One integration test (needs the jar) | `./mvnw verify -Dtest=NoSuch -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=StdioHygieneIT` |
| Live smoke test | `STEAM_API_KEY=... STEAM_ID=... ./mvnw -Plive test` |

## Conventions

Test names `method_condition_expected`, Given/When/Then comments, AssertJ, NullAway checks tests too.
Spring tests use `@McpSpringBootTest` (quiet STDIO transport, D16). Fixtures are synthetic; never commit real
responses or credentials.

## Known gaps

- Real Steam behavior is covered only by the opt-in live test, which needs the owner's key.
- Windows/Linux hosts are exercised only by CI (Ubuntu); local development was on macOS.
- MCP Inspector was not run; protocol conformance is checked with the official Java SDK client.
