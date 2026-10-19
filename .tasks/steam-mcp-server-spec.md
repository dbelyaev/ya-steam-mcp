# Steam MCP Server — Implementation Specification

> **Status:** Implementation-ready draft · **Document version:** 1.3 (English edition) · **Date:** 2026-10-19  
> **Project:** `steam-mcp-server` · **Language:** Java · **Framework:** Spring Boot  
> **Purpose:** Provide autonomous coding agents with a single, authoritative source of implementation requirements.  
> **Change from v1.2:** Section 8 retry policy now uses Framework 7 `@Retryable`; 429 is not retried and `Retry-After` is not honored (decision D11).  
> **Change from v1.1:** Documentation language changed to English; JDK 25 LTS and all functional requirements remain unchanged.

## 0. Instructions for Implementing Agents (Read First)

Implement a **local, read-only Steam MCP server** using Java and Spring Boot. Follow the specification below. Do not authenticate through the Steam desktop client, access cookies or passwords, or control the user's Steam account. The only source of user-specific data is the supported public Steam Web API, accessed using the user's **own Steam Web API key** and a configured `SteamID64`.

**Implementation order:**
1. First, verify the selected dependency versions and Spring AI MCP / Steam Web API contracts against the official references at the end of this document. Record incompatibilities in `docs/decisions.md`; do not guess API behavior.
2. Build a minimal working `get_library` tool; validate the MCP handshake and `tools/list` with a real MCP client. A diagnostic `ping` is allowed only as an internal test, **not as a seventh public MCP tool**.
3. Implement the remaining tools, each covered by unit tests, HTTP-stub tests, and MCP integration tests.
4. Before delivery, run the tests, confirm that secrets do not leak to stdout/stderr, and provide a short report listing any limitations.
5. **Stay within MVP scope** unless a separate decision authorizes an extension. In particular, do not implement item trading, game purchases, launching games, modifying statistics, Steam login, or Steam Community page scraping.

**Definition of Done:** A built `jar` that runs over STDIO, discoverable MCP tools, tests that pass without a real Steam API key, and a README covering client configuration and example tool calls.

---

## 1. Goals and Scope

### 1.1. Goal

Give an AI client (for example, Codex Desktop or another MCP-compatible host) safe, programmatic **read access** to the user's Steam library, playtime, recently played games, achievements, and game-specific news. Responses must be structured, size-bounded, and include data provenance.

### 1.2. Included in the MVP

- `get_library`: retrieve visible games using `GetOwnedGames`; apply filtering, sorting, and pagination locally.
- `get_game_details`: return available game metadata from official sources, **without promising** that genres, descriptions, or release dates are available.
- `get_playtime`: return playtime for one game using `GetOwnedGames`.
- `get_achievements`: retrieve achievements for a selected game; optionally enrich them using `GetSchemaForGame`.
- `get_recent_games`: retrieve recently played games (the time window is defined by Steam, usually about two weeks).
- `get_game_news`: retrieve news through `ISteamNews/GetNewsForApp`.
- Normalize `SteamID64` (string), `appid` (uint32), errors, and missing data.
- Cache Steam responses briefly in memory and guard against excessive outbound calls.
- Provide a local `STDIO` transport, configured using environment variables.

### 1.3. Explicitly Excluded from the MVP

- Steam OpenID / OAuth, multi-user login, and SaaS deployments.
- Any changes to the Steam account; trading, purchases, messages, friends, and inventory management.
- Reading local Steam client files or databases, or bypassing Steam privacy settings.
- Preference prediction or AI-driven game ranking inside the MCP server; these belong to the calling agent.
- Persisting the user's library in a database or building a web UI.
- Making undocumented Steam Store endpoints a mandatory source of data.

### 1.4. Expected Steam Limitations

1. **Privacy settings matter:** `GetOwnedGames` provides information only when the library/game details are visible to the API caller. An empty `200 OK` response does not necessarily mean the user owns no games; privacy status may be unknown.
2. `GetRecentlyPlayedGames` reports recent activity, not arbitrary historical periods.
3. Not every game has accessible achievements. Missing achievement data is **not** equivalent to zero achievements.
4. With `include_appinfo=true`, `GetOwnedGames` can return the game name and some icon information, but **not a full store catalog** (genres, system requirements, descriptions, etc.).
5. A regular Steam Web API key is not a Steamworks publisher key. **Do not use** `partner.steam-api.com` for this personal project.

---

## 2. Technology Stack and Compatibility

| Component | Choice | Notes |
|---|---|---|
| Java / JDK | **25 LTS (mandatory)** | Build, test, and run with JDK 25; do not lower target/release to 21 |
| Spring Boot | **4.1.1** for initial implementation | Recheck compatibility before upgrading |
| Spring AI | **2.0.1** | Stable release selected when this specification was prepared; 2.1.0-M1 is a milestone, not the default choice |
| MCP server | `org.springframework.ai:spring-ai-starter-mcp-server` | STDIO, synchronous tools, annotations |
| MCP tools | Spring AI `@McpTool`, `@McpToolParam` | Do not copy outdated or incompatible Spring AI 1.x packages |
| HTTP | Spring `RestClient` | Use `spring-boot-starter-restclient`; timeouts are mandatory |
| JSON | Jackson as managed by Boot 4 | Account for the Boot 4 / Spring AI 2 transition to Jackson 3 |
| Cache | Caffeine (optional but recommended) | In-memory cache with endpoint-specific TTL |
| Tests | JUnit 5, AssertJ, WireMock 3.x | Stub Steam requests; no real network dependency in default tests |
| Build | Maven Wrapper | Reproducible Maven Central-based builds |

**Version compatibility decision:** Spring AI 2.0.x supports Spring Boot **4.0.x / 4.1.x**. Do not combine it with Spring Boot 3.4.x. If Boot 3.x becomes a strict requirement, document a separate ADR and use a compatible Spring AI 1.1.x line rather than mixing versions.

**JDK requirement:** Use **JDK 25 LTS** locally, in CI, and at runtime (including any future Docker image). Maven must compile with `--release 25` using `<java.version>25</java.version>` and/or a compiler-plugin configuration. Do not enable Java preview features without a separate ADR. In GitHub Actions, configure `actions/setup-java` with `java-version: '25'` and verify the JVM version before building. Pin a Maven / Maven Wrapper version compatible with JDK 25.

### 2.1. Starting Maven `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.1</version>
    <relativePath/>
  </parent>
  <groupId>dev.steam</groupId>
  <artifactId>steam-mcp-server</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <properties>
    <java.version>25</java.version>
    <spring-ai.version>2.0.1</spring-ai.version>
  </properties>
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-bom</artifactId>
        <version>${spring-ai.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-starter-mcp-server</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-restclient</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <!-- Add a current, compatible WireMock 3.x version as a test dependency. -->
    <!-- Add Caffeine / Boot cache starter when implementing caching. -->
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

> This POM is a **starting skeleton**, not a claim that the build has been verified. The first implementation agent must run `./mvnw -B clean verify`, correct dependencies if necessary, and record the reasons in an ADR. Do not add model-provider starters, LLM API keys, or `spring-ai-starter-model-*`: this MCP server does not perform inference.

---

## 3. Architecture

```text
MCP host (Codex / MCP Inspector / other client)
                  │
                  │ JSON-RPC over STDIO
                  ▼
          Spring AI MCP Server
                  │
       @McpTool adapters (thin)
                  │
            SteamService
         ┌────────┼────────┐
         │        │        │
    Validation   Cache  ErrorMapper
         └────────┼────────┘
                  │
          SteamApiClient
                  │ HTTPS / TLS
                  ▼
       api.steampowered.com
```

**Layer boundaries:**
- Tool layer: input validation, service invocation, and compact response construction only. No HTTP details.
- Service layer: business logic, lookup/filter/sort/pagination, merging data from multiple endpoints, and caching.
- Adapter layer: Steam requests, retry/backoff, upstream DTO deserialization, and translating upstream status/errors.
- Tool names and contract DTOs form a stable public API; do not leak raw Steam JSON into public responses.
- Do not add JPA/Hibernate, Flyway, SQL, messaging/queues, or a multi-module build without a clear need.

### 3.1. Suggested Package Layout

```text
src/main/java/dev/steam/mcp/
  SteamMcpApplication.java
  config/
    SteamProperties.java
    SteamHttpConfig.java
    McpLoggingConfig.java
  tool/
    SteamTools.java
    ToolResponse.java
    ToolError.java
  service/
    SteamService.java
    SteamServiceImpl.java
  client/
    SteamApiClient.java
    SteamApiClientImpl.java
    SteamClientException.java
  model/
    GameSummary.java
    GameDetails.java
    Playtime.java
    Achievement.java
    GameNews.java
    PaginatedResult.java
  steamdto/
    OwnedGamesResponse.java
    RecentlyPlayedResponse.java
    PlayerAchievementsResponse.java
    GameSchemaResponse.java
    NewsResponse.java
  support/
    SteamIdValidator.java
    AppIdValidator.java
    ResponseMapper.java
    CacheConfig.java
src/main/resources/
  application.yml
  logback-spring.xml
src/test/java/dev/steam/mcp/
  ...
docs/
  decisions.md
  api-mapping.md
  test-plan.md
README.md
```

---

## 4. Configuration and Startup

### 4.1. `application.yml` (STDIO)

```yaml
spring:
  application:
    name: steam-mcp-server
  main:
    web-application-type: none
    banner-mode: off
  ai:
    mcp:
      server:
        enabled: true
        name: steam-mcp-server
        version: 0.1.0
        type: SYNC
        stdio: true
        annotation-scanner:
          enabled: true
        capabilities:
          tool: true
          resource: false
          prompt: false
          completion: false

steam:
  api:
    base-url: https://api.steampowered.com
    key: ${STEAM_API_KEY:}
    steam-id: ${STEAM_ID:}
    connect-timeout: 3s
    read-timeout: 10s
    retries: 2
    default-language: english
  library:
    include-played-free-games: true
  store:
    enrichment-enabled: false
```

**Mandatory STDIO rule:** `stdout` is reserved exclusively for MCP JSON-RPC messages. Spring banners, `System.out.println`, console logging, progress bars, and all accidental text on `stdout` are **prohibited**. Configure `logback-spring.xml` to write to `System.err` or a file. Verify this requirement with a dedicated integration test. Never print `STEAM_API_KEY` or `STEAM_ID`.

`STEAM_ID` is a decimal `SteamID64` value represented as a **string** (not a JSON `long` or JavaScript number). The MVP serves a single preconfigured account. Do not accept an arbitrary `steamid` argument in each tool. This simplifies access control and reduces the risk of exposing another user's data.

### 4.2. Example MCP Host Configuration

The precise configuration format depends on the client. The following is a **generic STDIO example**; adapt it to the actual host:

```json
{
  "mcpServers": {
    "steam": {
      "command": "java",
      "args": ["-jar", "/absolute/path/steam-mcp-server.jar"],
      "env": {
        "STEAM_API_KEY": "<YOUR_KEY>",
        "STEAM_ID": "<YOUR_STEAM_ID64>"
      }
    }
  }
}
```

Never commit a working configuration containing an API key. Alternatively, supply secrets through the host's environment or secret manager.

### 4.3. Startup Validation and Graceful Degradation

- At startup, validate the URL (allow only the official `https://api.steampowered.com`), the `SteamID64` syntax, and timeout settings.
- A missing `STEAM_API_KEY` must produce a **clear configuration error** when invoking tools that require a key. A news-only mode could be added as an explicit separate decision.
- Do not ping Steam during startup: the server must be able to start offline and respond to MCP protocol messages.
- If Steam is unavailable, tools should return structured errors while the server process remains operational.

---

## 5. Steam Web API Endpoint Map

**Base URL for ordinary Steam Web API keys:** `https://api.steampowered.com`. Do not use `partner.steam-api.com`, which is designed for publisher keys. Some public endpoints accept `key` as a query parameter: never log the full URI or sensitive HTTP headers.

| Data | HTTP endpoint | Parameters | Used by |
|---|---|---|---|
| Owned library | `GET /IPlayerService/GetOwnedGames/v1/` | `key`, `steamid`, `include_appinfo=true`, `include_played_free_games=true` | `get_library`, `get_playtime`, part of `get_game_details` |
| Recent activity | `GET /IPlayerService/GetRecentlyPlayedGames/v1/` | `key`, `steamid`, `count` | `get_recent_games` |
| Achievements | `GET /ISteamUserStats/GetPlayerAchievements/v1/` | `key`, `steamid`, `appid`, `l` | `get_achievements` |
| Achievement schema | `GET /ISteamUserStats/GetSchemaForGame/v2/` | `key`, `appid`, `l` | `get_achievements`, part of `get_game_details` |
| News | `GET /ISteamNews/GetNewsForApp/v2/` | `appid`, `count`, `maxlength`, optional `enddate` | `get_game_news`; no API key needed |

Cover every path and API version with tests. Steamworks documentation sometimes shows a partner hostname even for interfaces also available on the public host; **validate public API calls in an optional smoke test using your own key**. Do not substitute publisher-only credentials.

### 5.1. Optional External Data

- `ISteamUser/ResolveVanityURL/v1/` — useful for a **separate SteamID setup utility**, but unnecessary for the MVP tools.
- `https://store.steampowered.com/api/appdetails?appids=...` — a known Steam Store endpoint, **not documented as a stable Steamworks Web API contract**. It is **disabled by default**. It may later be used for best-effort enrichment behind a feature flag after checking terms of use, error handling, localization, and rate limits. Its failure must never break `get_game_details`.
- Do not use `GetSingleGamePlaytime` on the partner host: its documented key requirements are tied to an AppID; `GetOwnedGames` is sufficient for this personal application.

---

## 6. Public MCP Tools (Required Contracts)

**Conventions:**
- Tool names use `snake_case` and must remain stable after release.
- All tools are **read-only**. Set `readOnlyHint=true`, `destructiveHint=false`, `idempotentHint=true`, and `openWorldHint=true` in tool annotations (these hints are advisory metadata, not security controls).
- Defaults are enforced by the service; do not trust client-provided defaults. Fields marked `required` must be supplied.
- All `appid` values are positive integers in the uint32 range; `steamId` is always a string in responses.
- `playtime*Minutes` fields represent **minutes**; never convert them to hours in API DTOs.
- External timestamps must use **ISO-8601 UTC**; retain raw Unix timestamps only in internal DTOs if needed.
- Missing fields must be `null` or explicitly `available=false`. Do not invent values.
- Return JSON-compatible DTOs, not prose paragraphs or unbounded raw Steam responses.
- If `generateOutputSchema=true` is used, validate the generated schema and serialized DTO with MCP Inspector.

### 6.1. Shared Response Envelope

Successful response:

```json
{
  "data": {},
  "meta": {
    "source": "Steam Web API",
    "fetchedAt": "2026-10-19T18:00:00Z",
    "cached": false,
    "partial": false,
    "warnings": []
  }
}
```

Error response contract (see Section 8):

```json
{
  "error": {
    "code": "UPSTREAM_UNAVAILABLE",
    "message": "Steam API is temporarily unavailable",
    "retryable": true
  }
}
```

When supported by the SDK, expected tool failures should return MCP tool results with `isError=true`. Do not confuse tool failures with JSON-RPC protocol errors. Never expose URLs containing a `key` or the body of an unvalidated upstream response.

### 6.2. `get_library`

**Agent-facing description:** `List visible Steam games owned by the configured Steam user; filter and sort locally.`

**Input:**

```json
{
  "query": "portal",
  "sortBy": "PLAYTIME_DESC",
  "offset": 0,
  "limit": 20,
  "includeNeverPlayed": true
}
```

| Field | Type | Required | Default / Allowed values |
|---|---|---|---|
| `query` | string | no | `null`; case-insensitive name search; max 100 characters |
| `sortBy` | enum | no | `PLAYTIME_DESC`; `NAME_ASC`, `NAME_DESC`, `PLAYTIME_ASC`, `PLAYTIME_DESC` |
| `offset` | integer | no | 0; range 0–100000 |
| `limit` | integer | no | 20; range **1–100** |
| `includeNeverPlayed` | boolean | no | `true` |

**Output `data`:**

```json
{
  "steamId": "7656119...",
  "total": 234,
  "offset": 0,
  "limit": 20,
  "games": [
    {
      "appid": 620,
      "name": "Portal 2",
      "playtimeForeverMinutes": 740,
      "playtimeWindowsForeverMinutes": null,
      "playtimeMacForeverMinutes": null,
      "playtimeLinuxForeverMinutes": null,
      "iconUrl": null,
      "storeUrl": "https://store.steampowered.com/app/620/"
    }
  ],
  "visibilityStatus": "UNKNOWN"
}
```

Numbers in these examples are **illustrative contract values**, not data from a real user's library. `total` is the number of items **after filtering, before pagination**. For an empty API response, return `visibilityStatus=UNKNOWN` (not `PUBLIC` or `PRIVATE` without evidence). An empty `games` array is valid. Filtering and pagination happen after fetching the API data, **not by making a Steam request for every page**.

### 6.3. `get_playtime`

**Description:** `Get total Steam playtime for a specific AppID from the configured user's visible library.`

Input: `{"appid":620}`.

Output `data`:

```json
{
  "appid": 620,
  "name": "Portal 2",
  "playtimeForeverMinutes": 740,
  "playtimeWindowsForeverMinutes": null,
  "playtimeMacForeverMinutes": null,
  "playtimeLinuxForeverMinutes": null,
  "source": "GetOwnedGames"
}
```

If the AppID is absent from the accessible library, return `GAME_NOT_VISIBLE_OR_NOT_OWNED` (do not assert that the user definitely does not own it). **Do not use** the partner-only `GetSingleGamePlaytime` endpoint.

### 6.4. `get_recent_games`

**Description:** `List games played recently as reported by Steam (not a user-specified date range).`

Input: `{"limit":20}`; `limit` range 1–100, default 20.

Output `data`:

```json
{
  "steamId": "7656119...",
  "windowDescription": "Steam recent activity (typically last two weeks)",
  "games": [
    {
      "appid": 620,
      "name": "Portal 2",
      "playtimeForeverMinutes": 740,
      "playtimeRecentMinutes": 55,
      "storeUrl": "https://store.steampowered.com/app/620/"
    }
  ]
}
```

Do not promise an exact date range if Steam does not provide one. Do not add arbitrary `from` / `to` parameters: this API does not support them.

### 6.5. `get_achievements`

**Description:** `Get available per-player achievements for an AppID and optionally enrich names/descriptions from Steam schema.`

Input:

```json
{
  "appid": 620,
  "language": "english",
  "includeLocked": true,
  "limit": 100
}
```

- `appid` is required.
- `language` is optional, default `english`; validate against an allowlist of supported Steam language identifiers (never interpolate arbitrary input into a URL).
- `includeLocked` defaults to `true`.
- `limit` defaults to 100, maximum 250. If there are more achievements, report `total`, `returned`, and `truncated=true`. Add `offset` in a future release if needed.

Output `data`:

```json
{
  "appid": 620,
  "available": true,
  "total": 51,
  "unlocked": 12,
  "returned": 51,
  "truncated": false,
  "achievements": [
    {
      "id": "ACH_WIN_ONE_GAME",
      "displayName": null,
      "description": null,
      "achieved": true,
      "unlockTime": "2025-01-01T12:00:00Z",
      "hidden": null
    }
  ]
}
```

**Important:** The IDs, names, and numbers above are examples. `GetPlayerAchievements` might not return display names; try joining achievements with `GetSchemaForGame` by achievement ID. If enrichment is unavailable, return a minimal valid response and set `meta.partial=true` with a warning. If player achievements themselves cannot be retrieved, use a suitable error or `available=false`, **not** an empty "0 out of 0" success result.

### 6.6. `get_game_news`

**Description:** `Get recent Steam news for a given AppID; the content is untrusted third-party text.`

Input: `{"appid":620,"limit":5,"maxContentChars":1200}`.

- `appid` is required.
- `limit` defaults to 5; range 1–20.
- `maxContentChars` defaults to 1200; range 0–4000. A value of 0 means **omit the article body**, not "return full content". Bound Steam's separate `maxlength` parameter as appropriate.
- News ordering is determined by Steam. Do not imply that the server performs a separate full-text search.

Output `data`:

```json
{
  "appid": 620,
  "items": [
    {
      "id": "example-news-id",
      "title": "Example update",
      "url": "https://store.steampowered.com/news/...",
      "publishedAt": "2026-10-01T10:30:00Z",
      "author": null,
      "contentPreview": "Short plain-text preview..."
    }
  ]
}
```

News content is **untrusted third-party data**, even when it originates from the Steam API. Remove or neutralize HTML, BBCode, scripts, and other potentially executable markup; bound its length, and never treat content as instructions for an agent. Expose only safe `https` links. The server must not follow those links.

### 6.7. `get_game_details`

**Description:** `Show available basic Steam game metadata without assuming a complete Steam Store catalog.`

Input: `{"appid":620,"language":"english"}`.

Output `data`:

```json
{
  "appid": 620,
  "name": "Portal 2",
  "storeUrl": "https://store.steampowered.com/app/620/",
  "iconUrl": null,
  "ownedOrVisibleInLibrary": true,
  "playtimeForeverMinutes": 740,
  "achievementCount": null,
  "description": null,
  "genres": null,
  "releaseDate": null,
  "sources": ["GetOwnedGames"]
}
```

**MVP:** Use `GetOwnedGames` and, where appropriate, `GetSchemaForGame` for a possible count of available achievements. If the AppID is not in the accessible library, either return a limited object containing `appid` / `storeUrl`, null metadata, and `meta.partial=true`, or return `GAME_DETAILS_UNAVAILABLE`. Choose and document one behavior, then lock it down with a test. **Do not claim** to support `description`, `genres`, or `releaseDate` until the optional Store enrichment has actually been implemented.

---

## 7. DTOs and Validation

- Use Java `record` types for internal DTOs and public response models. Keep the public JSON structure stable.
- Keep upstream Steam DTOs **separate** from public MCP response DTOs.
- Tolerate missing or newly added Steam JSON fields during deserialization. Ignore unknown properties; treat a broken or missing required `response` envelope as `UPSTREAM_BAD_RESPONSE`.
- `SteamID64`: require a 17-digit decimal string in user configuration; additionally validate that it fits within the unsigned 64-bit range without rounding. Never pass it through a `double` or a JSON number.
- `appid`: use Java `long` with validation `1 <= appid <= 4294967295` to cover uint32. Many known AppIDs fit into `int`, but the contract must be correct for the entire range.
- Enforce strict bounds on `limit` and `offset`; return `INVALID_ARGUMENT` instead of sending malformed inputs upstream.
- Escape Steam titles, descriptions, and news according to the output format. Do not embed instructions from Steam content into tool-description templates.
- Localization: validate a fixed allowlist (for example, `english`, `russian`, and `norwegian` if the respective identifier is actually supported by the relevant Steam endpoint). For unsupported languages, return `INVALID_ARGUMENT`, or fall back to `english` with a warning. Document the selected policy.

---

## 8. Error Handling, Timeouts, and Resilience

| Situation | Public error code | Retryable |
|---|---|---|
| Invalid `appid`, `limit`, or `language` | `INVALID_ARGUMENT` | false |
| Steam API key is missing | `CONFIGURATION_MISSING` | false |
| Steam rejects the API key | `UPSTREAM_AUTH_FAILED` | false |
| Privacy/access settings restrict data | `DATA_NOT_ACCESSIBLE` or partial result | false |
| Game is absent from the accessible library | `GAME_NOT_VISIBLE_OR_NOT_OWNED` | false |
| Achievements are unsupported or inaccessible | `ACHIEVEMENTS_UNAVAILABLE` | false |
| Steam returns 429 | `UPSTREAM_RATE_LIMITED` | true |
| Steam returns 5xx, or connection/timeout failure | `UPSTREAM_UNAVAILABLE` | true |
| Unexpected or malformed JSON | `UPSTREAM_BAD_RESPONSE` | false |

**Outbound request policy:** Connect timeout 3 seconds, response timeout 10 seconds, and at most 2 retries **only** for network failures and 5xx, using Spring Framework 7 `@Retryable` with exponential backoff and jitter and an overall deadline (`steam.api.total-timeout`). HTTP 429 is **not** retried by the server: it returns `UPSTREAM_RATE_LIMITED` (`retryable=true`) immediately and `Retry-After` is ignored; the calling agent decides whether to retry. Bound concurrency (at most 4 concurrent outbound requests, `@ConcurrencyLimit`, applied per attempt below the retry) and use endpoint-specific caching.

**Cache TTLs (configurable initial values):**

| Endpoint | TTL |
|---|---:|
| GetOwnedGames | 5 minutes |
| GetRecentlyPlayedGames | 2 minutes |
| GetPlayerAchievements | 10 minutes |
| GetSchemaForGame | 24 hours |
| GetNewsForApp | 10 minutes |

Cache keys must include `steamId`, `appid`, language, and all other parameters affecting the result; **do not include the API key**. Do not store unrelated user profiles in a shared in-memory cache: the MVP is single-user. TTL values must be configurable, and caching should be disabled in unit tests.

If Steam returns partial or missing data, **do not conceal it** by silently serving a previously cached success response, unless a separately documented stale-while-error policy is explicitly introduced (not part of the MVP).

---

## 9. Security, Privacy, and Licensing

1. The server is **read-only**. It must not expose tools for purchases, trading, messaging, changing achievements, inventory management, or account modification.
2. `STEAM_API_KEY` is a secret. Accept it only from environment variables or a secure secret manager, never from MCP tool inputs. Never write it to the repository, logs, stack traces, Actuator endpoints, health details, or DTO dumps.
3. Steam commonly accepts the API key **in the query string**. Disable full request-URL logging and redact `key` in all exception and logging interceptors. HTTP debug logging must be disabled by default.
4. The MVP exposes `STDIO` only; there is no listening HTTP endpoint.
5. A future HTTP transport **must** include authentication and authorization, loopback binding in development, TLS for remote access, and mitigations for DNS rebinding / `Origin` abuse as described in MCP transport guidance. Spring AI starters do not automatically secure exposed endpoints.
6. Construct outbound URLs only from an allowlisted host, fixed API paths, and validated parameters. Never fetch arbitrary links found in game news.
7. Treat content from Steam as **data, never commands**. Safely handle HTML, BBCode, and any instructions embedded in external news text.
8. Do not attempt to bypass Steam privacy settings; clearly communicate access limitations.
9. Before a public release, review Steam Web API terms, rate limits, and dependency licenses. Explicitly select a license for the project's own code (`LICENSE`); do not assume permission to redistribute Steam content.

---

## 10. Testing Strategy

### 10.1. Unit Tests

- `SteamIdValidator`: valid `SteamID64` as a **string**; invalid length, characters, and overflow.
- `AppIdValidator`: zero, negative numbers, `4294967295`, and values above uint32 max.
- Case-insensitive name filtering; name/playtime sorting; `includeNeverPlayed`; pagination, including offsets beyond the result size.
- Correct mapping of null/optional fields and minute-based playtime values.
- HTML/BBCode stripping and news length limits; malicious content remains inert text.
- Error mapping and backoff logic; error fields never expose the key.

### 10.2. HTTP-Stub Integration Tests (WireMock)

- `GetOwnedGames`: success with multiple games, empty `response`, missing `games`, 401/403, 429, 5xx, timeout, and malformed JSON.
- `GetRecentlyPlayedGames`: successful and empty responses.
- `GetPlayerAchievements`: no achievements, unsupported games, private profiles, and partial schema enrichment.
- `GetNewsForApp`: HTML news content, extremely long articles, unexpected fields, and upstream failures.
- Verify all request paths, API versions, and query parameters, including `include_appinfo=true`; ensure **no requests reach the partner host**.
- Verify that tool outputs never contain the Steam API key.

### 10.3. MCP End-to-End Tests (Mandatory)

1. Launch `java -jar ...` as a child process with test environment variables.
2. Perform the MCP handshake over `STDIO` using a supported protocol version.
3. Check that `tools/list` returns **exactly six** public MVP tools with valid input schemas.
4. Execute `tools/call` for every tool while stubbing upstream HTTP requests.
5. Confirm that `stdout` contains **only** MCP protocol messages; all application logs go to `stderr` or a file.
6. Verify that one failed tool does not terminate the process or corrupt subsequent `tools/call` handling.
7. Check read-only tool hints and error reporting (`isError` when supported).

### 10.4. Optional Live Smoke Tests

Add a separate Maven profile or test tag called `live` that reads credentials from environment variables. It must be **disabled by default** and must not run in CI for pull requests from forks. Use a personal account with suitable privacy settings and known public AppIDs. Never commit private account data or real API responses as fixtures.

---

## 11. Implementation Phases for AI Agents

| Phase | Owner / Role | Deliverables | Completion gate |
|---|---|---|---|
| P0 — Bootstrap | **Planner + Coder** | JDK 25 LTS, Maven, Boot 4, Spring AI, configuration, `STDIO` handshake, `tools/list` | Jar builds and starts on JDK 25; STDIO stays clean |
| P1 — Steam client | **Coder** | RestClient, API DTOs, timeouts, error mapping, WireMock fixtures | All HTTP-stub tests pass |
| P2 — Core tools | **Coder** | `get_library`, `get_playtime`, `get_recent_games` | Valid contracts, pagination, privacy behavior |
| P3 — Rich tools | **Coder** | `get_achievements`, `get_game_news`, `get_game_details` | Correct partial responses and missing-field behavior |
| P4 — Hardening | **Reviewer + Coder** | Caching, rate limiting, logging, secrets review, MCP E2E | No security blockers; `mvn verify` passes |
| P5 — Release | **Orchestrator + Reviewer** | README, examples, executable jar, GitHub Actions, license, version | Reproducible build and acceptance checklist satisfied |

### 11.1. Agent Collaboration Protocol

**Orchestrator** owns scope, splits the work into phases, tracks Definition of Done, and does not introduce additional features without an ADR.

**Planner** produces a concise `docs/implementation-plan.md` before P0: proposed classes, critical integrations, test acceptance criteria, and risky assumptions.

**Pre-mortem reviewer** investigates likely failures before P1: Spring AI / Boot version mismatch, logging contamination of STDIO, incorrect Steam API hostname, `SteamID64` precision, ambiguity around privacy, and undocumented game metadata. Record mitigations in `docs/pre-mortem.md`.

**Coder** implements each phase in small, verifiable changes, updates tests and documentation, and runs the verification command before handing work off.

**Reviewer** independently evaluates tool contracts, security, error quality, test coverage, and overengineering risk. Blocking defects go back to the Coder. A phase cannot be approved without a reproducible test result.

**Token-efficiency rule:** Give subagents **only this specification, relevant project files, and a short status summary for the current phase**, not the entire original discussion. Maintain a concise `docs/status.md` with completed / next / blockers and modified file paths.

### 11.2. Ready-to-Use Orchestrator Prompt

```text
You are the implementation orchestrator for a Java 25 LTS / Spring Boot Steam MCP Server.
Use steam-mcp-server-spec.md as the authoritative requirements document.

First, confirm Spring Boot 4.1.x and Spring AI 2.0.x compatibility against official docs.
Have a planner produce docs/implementation-plan.md and a pre-mortem reviewer
produce docs/pre-mortem.md. Implement in phases P0–P5 with small verified steps.

Important invariants:
- Read-only Steam Web API, public host api.steampowered.com, no publisher key.
- Six tools with stable names and bounded structured outputs.
- STDIO JSON-RPC stdout must remain pristine.
- STEAM_API_KEY must never leak to logs, reports or code.
- Privacy/non-availability are not equivalent to zero games or achievements.
- No undocumented Store API enrichment in MVP.
- JDK 25 LTS is mandatory for build, tests, CI and runtime.
- Unit, stub integration and real MCP protocol tests are mandatory.

Use a coder to implement and an independent reviewer to validate each phase.
Do not claim a phase completed without running its tests.
Record deviations and reasons in docs/decisions.md; preserve requirements.
At the end, report build/test commands, artifacts, known limitations and
all items from the acceptance checklist.
```

---

## 12. Acceptance Checklist (Release Gate)

- [ ] `./mvnw -B clean verify` succeeds with JDK 25 LTS; `java -version` and `mvn -version` confirm JDK 25.
- [ ] `java -jar target/steam-mcp-server-*.jar` starts in STDIO mode.
- [ ] MCP Inspector or a real MCP host successfully completes the handshake and `tools/list`.
- [ ] Exactly these tools are registered: `get_library`, `get_game_details`, `get_playtime`, `get_achievements`, `get_recent_games`, and `get_game_news`.
- [ ] All six tools expose correct input/output schemas, limits, and read-only hints.
- [ ] All Steam requests are stubbed in automated tests; CI does not require a real API key.
- [ ] Privacy restrictions, missing achievements, 429, 5xx, timeouts, and malformed JSON are handled correctly.
- [ ] An empty `GetOwnedGames` response is **not** interpreted as proof of an empty library.
- [ ] `SteamID64` is never rounded and always represented as a string.
- [ ] `offset` / `limit` are bounded; no unbounded MCP responses are returned.
- [ ] `stdout` contains no logs, banners, or stack traces.
- [ ] The API key never appears in logs, error responses, Git history, or CI artifacts.
- [ ] Only the public Steam API host is used.
- [ ] The README covers setup, obtaining an API key, `SteamID64`, privacy settings, STDIO configuration, limitations, and test commands.
- [ ] `docs/decisions.md` records all deviations from this specification.
- [ ] Optional Steam Store enrichment remains disabled.

---

## 13. Potential Post-MVP Extensions (Do Not Implement Yet)

1. **Streamable HTTP:** Introduce a separate deployment/profile or launcher using `spring-ai-starter-mcp-server-webmvc` and `spring.ai.mcp.server.protocol=STREAMABLE`. Establish mandatory authentication and an explicit security boundary before exposing it on a network. With the corresponding starter, the default HTTP endpoint is `/mcp`.
2. **Store enrichment:** Genres, descriptions, and release dates, with fallback logic, separate rate limits, and a clear warning about an unstable endpoint.
3. **Search by name:** Add a separate `search_games` tool to resolve AppIDs; distinguish local library search from searching the global Steam catalog.
4. **Smart recommendations:** Keep AI recommendations in the MCP client built on top of read-only data. The MCP server itself must not embed an LLM.
5. **Multi-profile support:** Explicit owner authentication, cache isolation, scoped secrets, and per-user authorization.
6. **Additional resources:** Consider `steam://library` and `steam://recent` MCP resources only after verifying a real consumer use case.
7. **Caching / metrics:** Micrometer and health endpoints without exposing keys or SteamIDs. Add a bounded disk cache only with explicit user consent.

---

## 14. Official References and Supporting Documentation

References consulted when preparing the original specification on **October 19, 2026**. Dependency releases and API behavior may change:

1. Spring AI Getting Started (Boot 4 compatibility): https://docs.spring.io/spring-ai/reference/getting-started.html
2. Spring AI MCP Server Boot Starter: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html
3. Spring AI MCP STDIO: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-stdio-sse-server-boot-starter-docs.html
4. Spring AI MCP Annotations: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-server.html
5. Spring AI Streamable HTTP: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-streamable-http-server-boot-starter-docs.html
6. Steamworks Web API Overview (public vs. publisher host): https://partner.steamgames.com/doc/webapi_overview
7. Steam `IPlayerService`: https://partner.steamgames.com/doc/webapi/IPlayerService
8. Steam `ISteamUserStats`: https://partner.steamgames.com/doc/webapi/ISteamUserStats
9. Steam `ISteamNews`: https://partner.steamgames.com/doc/webapi/ISteamNews
10. Steam `ISteamUser`: https://partner.steamgames.com/doc/webapi/ISteamUser
11. Steam API Key Registration: https://steamcommunity.com/dev/apikey
12. MCP Specification: https://modelcontextprotocol.io/specification

**Conflict resolution priority:** Current MCP specification and official library documentation → Steamworks documentation → validated integration tests → this document. If a particular Steam endpoint is restricted or behaves differently than documented, the implementation agent must explicitly record the finding and adjust the feature status. Never fabricate a successful result.
