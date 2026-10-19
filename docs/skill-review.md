# Skill review of P0–P1 sources (2026-10-19)

Skills applied: `mcp-server`, `layered-architecture`, `configuration-properties`, `testing-pyramid`, `null-safety`, `resilience-retry`, `http-interface-clients`. Not applicable: JPA/Redis/security/web/observability skills (no DB, no HTTP endpoint, STDIO only).

Legend: **FIX** = defect vs skill, **ALIGN** = adopt skill pattern, **KEEP** = deliberate deviation (recorded in decisions.md).

| # | Area | Finding | Source file | Verdict |
|---|---|---|---|---|
| 1 | configuration-properties | `SteamProperties.Api` record has `key` component; generated `toString` prints the API key. Skill: never make a full token a plain record component. Any log/exception that stringifies the bean leaks it. | `config/SteamProperties.java` | **FIX** (security): redacting `toString` or a redacting `Secret` value type; test asserts key absent from `toString` |
| 2 | configuration-properties | Range checks are hand-written in a compact constructor. Skill: `@Validated` + Jakarta constraints (`@Min/@Max`, `@DurationMin/@DurationMax`), starter-validation already present. Keep a constructor invariant only for the cross-field base-URL/override rule. | same | **ALIGN** |
| 3 | configuration-properties | Skill: "missing credentials should fail startup". Spec 4.3 requires offline start and call-time `CONFIGURATION_MISSING`. | same | **KEEP** (spec wins, D6; record reason) |
| 4 | configuration-properties | No test of real env-var binding (`STEAM_API_KEY`, `STEAM_ID` via `application.yml` placeholders); public keys undocumented. | tests, README | **ALIGN**: add binding test, document keys in README |
| 5 | mcp-server | YAML `banner-mode: off` is a YAML 1.1 boolean; skill quotes it. | `application.yml` | **FIX**: quote `"off"` |
| 6 | mcp-server | Skill: log tool name, outcome, duration (no secrets). `ToolResults.run` logs only on unexpected failure and without tool name. | `tool/ToolResults.java` | **ALIGN**: `run(toolName, action)`, INFO line to stderr |
| 7 | mcp-server | Skill example uses `generateOutputSchema=true` with DTO returns. We return `CallToolResult` (needed for `isError=true`, which a DTO return cannot express). | `tool/*` | **KEEP** (D7): schema off; envelope documented |
| 8 | mcp-server | Skill: bound execution time, result size, fan-out. Worst case today: 3 attempts × 10 s read + 2 × 10 s Retry-After ≈ 50 s per call, unbounded overall. | client | **ALIGN**: overall deadline per call (see #11) |
| 9 | mcp-server | Skill: test invalid arguments, shutdown with a real MCP client. | planned E2E | **ALIGN**: add invalid-args and stdin-close shutdown cases |
| 10 | null-safety | No `@NullMarked`; nullable fields (DTO components, `retryAfter()`, `Outcome.body`) are implicit. Spec: "missing fields must be null". | all packages | **ALIGN**: `package-info.java` with `@NullMarked`; `@Nullable` on genuinely nullable components/returns; evaluate NullAway (JDK 25 + Error Prone compat) as a spike, IDE-only if it fails |
| 11 | resilience-retry | Hand-written retry loop. Skill: Framework 7 `@Retryable` (`includes`, `maxRetries`, `delay`, `multiplier`, `jitter`, `maxDelay`, `timeout`) and `@ConcurrencyLimit` with `@EnableResilientMethods`; verified present in spring-context 7.0.9. Gaps: no `Retry-After` support, proxy-only, tests must not sleep. | `client/SteamApiClientImpl.java` | **SPIKE** with go/no-go: adopt annotations if `Retry-After` can be honored or a recorded deviation is accepted; otherwise **KEEP** loop (D11). `@ConcurrencyLimit(4)` adopted regardless (replaces planned hand-made semaphore) |
| 12 | http-interface-clients | Manual `RestClient.get().uri(...).exchange(...)`. Skill: `@HttpExchange` interface + `@ImportHttpServices` group, transport props under `spring.http.clients.*` / `spring.http.serviceclient.steam.*`. | `client/`, `config/SteamHttpConfig.java` | **SPIKE** with go/no-go: need no-throw status handling (group configurer with permissive default status handler), `ResponseEntity` returns for status + `Retry-After`, redirects off, and the base-URL allowlist still enforced on the resolved URL. Keep manual client if any of these fails (D12) |
| 13 | layered-architecture | Plan had `SteamService` + `SteamServiceImpl` (one impl, god-class risk across 6 tools). Skill: one service per aggregate/capability, no needless interface. | plan | **ALIGN**: `LibraryService`, `AchievementService`, `NewsService`, `GameDetailsService`; keep interface only on `SteamApiClient` (proxy target + test seam) |
| 14 | layered-architecture | Mapping: skill wants static `from` factories / dedicated mapper, no loops. | plan | **ALIGN**: `from(...)` on public model records; `AchievementMapper` for schema join; `NewsSanitizer` in `support/` |
| 15 | layered-architecture | Config package: only infra wiring, no service imports. `SteamHttpConfig` wires client only. | config/ | OK |
| 16 | layered-architecture | Skill suggests `@Slf4j`; project has no Lombok. | all | **KEEP**: explicit SLF4J loggers, no Lombok dependency |
| 17 | testing-pyramid | Test names do not follow `method_condition_expected`; no Given/When/Then; no Mockito unit tests yet (all P1 tests are WireMock). | tests | **ALIGN**: rename P1 tests; new service tests use `@ExtendWith(MockitoExtension.class)` over a mocked `SteamApiClient` (70% unit), WireMock for client (integration), one failsafe IT (E2E) |
| 18 | testing-pyramid | Mockito self-attach warning on JDK 25. | `pom.xml` | **ALIGN**: Mockito agent `argLine` now (was P4) |
| 19 | testing-pyramid | `@MockitoBean`, `@WebMvcTest`, Testcontainers, `@DataJpaTest`: no web/DB. | – | N/A |

## Follow-up (2026-10-19)
- S#10 extended: NullAway also checks test sources (`HandleTestAssertionLibraries=true`). Nullable DTO accessors are unwrapped with `Objects.requireNonNull` in tests; `AppIdValidator`/`SteamIdValidator` parameters are `@Nullable` because null is a handled input.
- S#17 completed: every test uses Given/When/Then comments.
