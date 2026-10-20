# steam-mcp-server

A local, **read-only** [Model Context Protocol](https://modelcontextprotocol.io) server that lets an AI client
look at your own Steam library, playtime, recent games, achievements and game news. It talks to the public
Steam Web API with **your own API key** and one configured SteamID64, over STDIO. It does not log in to Steam,
read cookies or passwords, touch the Steam client, or change anything on your account.

Java 25, Spring Boot 4.1, Spring AI 2.0 (MCP server starter). Licensed under Apache 2.0.
Not affiliated with Valve.

## Tools

All six tools are read-only (`readOnlyHint=true`). Responses are JSON text in the shape
`{"data": ..., "meta": {"source", "fetchedAt", "cached", "partial", "warnings"}}`; failures set `isError=true`
and return `{"error": {"code", "message", "retryable"}}`. Playtime is always in **minutes**, timestamps are ISO-8601 UTC,
missing values are `null`.

| Tool | Arguments (defaults) | Returns |
|---|---|---|
| `get_library` | `query` (name search, max 100 chars), `sortBy` (`NAME_ASC`, `NAME_DESC`, `PLAYTIME_ASC`, `PLAYTIME_DESC`; default `PLAYTIME_DESC`), `offset` (0), `limit` (20, max 100), `includeNeverPlayed` (true) | Page of owned games, `total` after filtering, `visibilityStatus` |
| `get_playtime` | `appid` (required) | Playtime for one owned game |
| `get_recent_games` | `limit` (20, max 100) | Games played in Steam's recent window (about two weeks) |
| `get_achievements` | `appid` (required), `language` (`english`), `includeLocked` (true), `limit` (100, max 250) | Unlocked/total counts and achievements with names, texts, hidden flag, unlock time |
| `get_game_news` | `appid` (required), `limit` (5, max 20), `maxContentChars` (1200, 0-4000; 0 omits the body) | News items as sanitized plain text |
| `get_game_details` | `appid` (required), `language` (`english`) | Name, store URL, ownership, playtime, achievement count |

Example call and result (values are illustrative):

```json
{"name": "get_library", "arguments": {"query": "portal", "limit": 5}}
```

```json
{
  "data": {
    "steamId": "7656119...",
    "total": 1, "offset": 0, "limit": 5,
    "games": [{"appid": 620, "name": "Portal 2", "playtimeForeverMinutes": 740,
               "playtimeWindowsForeverMinutes": null, "playtimeMacForeverMinutes": null,
               "playtimeLinuxForeverMinutes": null, "iconUrl": null,
               "storeUrl": "https://store.steampowered.com/app/620/"}],
    "visibilityStatus": "VISIBLE"
  },
  "meta": {"source": "Steam Web API", "fetchedAt": "2026-10-20T18:00:00Z", "cached": false, "partial": false, "warnings": []}
}
```

Error codes: `INVALID_ARGUMENT`, `CONFIGURATION_MISSING`, `UPSTREAM_AUTH_FAILED`, `UPSTREAM_RATE_LIMITED` (retryable),
`UPSTREAM_UNAVAILABLE` (retryable), `UPSTREAM_BAD_RESPONSE`, `DATA_NOT_ACCESSIBLE`, `GAME_NOT_VISIBLE_OR_NOT_OWNED`,
`GAME_DETAILS_UNAVAILABLE`, `ACHIEVEMENTS_UNAVAILABLE`, `INTERNAL_ERROR`. Arguments of the wrong type or missing a
required value are rejected by the MCP framework itself with `isError=true` and an "input validation failed" message.

## Setup

### 1. Get a Steam Web API key

Sign in at <https://steamcommunity.com/dev/apikey>, register a key (any domain name works for personal use) and
copy it. Treat it like a password: the server only reads it from the environment, never from tool arguments, and
never logs it.

### 2. Find your SteamID64

It is the 17-digit number that starts with `7656119`. Open your Steam profile: if the URL looks like
`steamcommunity.com/profiles/7656119...` that is it. With a custom URL (`/id/name`) paste the profile link into a
lookup site such as <https://steamid.io>. It is handled as a **string** everywhere and is never rounded.

### 3. Set your privacy options

Steam only returns what your privacy settings allow an API caller to see. In Steam: *Profile, Edit Profile,
Privacy Settings*, set **My profile** and **Game details** to *Public* (you can switch back after use). Otherwise:

- `get_library` can come back empty with `visibilityStatus: "UNKNOWN"`. That means "private, hidden or empty,
  cannot tell", not "you own nothing".
- `get_achievements` reports `DATA_NOT_ACCESSIBLE` or `ACHIEVEMENTS_UNAVAILABLE` instead of "0 of 0".

### 4. Build

JDK 25 is required (build, tests, CI and runtime).

```bash
java -version          # must say 25
./mvnw -B clean verify # unit, client, Spring and end-to-end tests; no Steam key needed
# result: target/steam-mcp-server-0.1.0-SNAPSHOT.jar
```

### 5. Register it with your MCP host

STDIO command, generic form (adapt the file format to your host):

```json
{
  "mcpServers": {
    "steam": {
      "command": "java",
      "args": ["-XX:+DisplayVMOutputToStderr", "-jar", "/absolute/path/steam-mcp-server-0.1.0-SNAPSHOT.jar"],
      "env": {
        "STEAM_API_KEY": "<YOUR_KEY>",
        "STEAM_ID": "<YOUR_STEAM_ID64>"
      }
    }
  }
}
```

`-XX:+DisplayVMOutputToStderr` keeps JVM warnings off stdout, which must carry JSON-RPC frames only.
Claude Code: `claude mcp add steam -e STEAM_API_KEY=... -e STEAM_ID=... -- java -XX:+DisplayVMOutputToStderr -jar /absolute/path/steam-mcp-server-0.1.0-SNAPSHOT.jar`.
Never commit a configuration that contains a real key.

The server starts without a key or SteamID and without network access; tools then answer with
`CONFIGURATION_MISSING` until you configure them.

## Configuration

Every property can be set as an environment variable (Spring relaxed binding: `steam.api.read-timeout` is
`STEAM_API_READ_TIMEOUT`). `STEAM_API_KEY` and `STEAM_ID` are the two you normally set.

| Property | Environment variable | Default | Meaning |
|---|---|---|---|
| `steam.api.key` | `STEAM_API_KEY` | none | Your Steam Web API key |
| `steam.api.steam-id` | `STEAM_ID` | none | Your SteamID64 (17 digits) |
| `steam.api.base-url` | `STEAM_API_BASE_URL` | `https://api.steampowered.com` | Only this origin is accepted |
| `steam.api.allow-insecure-test-base-url` | | `false` | Tests only: also accept `http(s)://localhost` or `127.0.0.1` |
| `steam.api.connect-timeout` / `read-timeout` | | `3s` / `10s` | HTTP timeouts |
| `steam.api.retries` | | `2` | Retries for I/O errors and 5xx (not for 429) |
| `steam.api.retry-delay` | | `200ms` | First backoff step (doubles, with jitter) |
| `steam.api.total-timeout` | | `30s` | Deadline for one upstream call including retries |
| `steam.api.max-concurrent-requests` | | `4` | Outbound requests in flight |
| `steam.api.default-language` | | `english` | Language when a tool call omits it |
| `steam.library.include-played-free-games` | | `true` | Passed to `GetOwnedGames` |
| `steam.cache.enabled` | | `true` | In-memory response cache |
| `steam.cache.owned-games-ttl` / `recent-games-ttl` / `achievements-ttl` / `schema-ttl` / `news-ttl` | | `5m` / `2m` / `10m` / `24h` / `10m` | Per-endpoint cache lifetime |
| `steam.store.enrichment-enabled` | | `false` | Reserved; Store enrichment is not implemented |

## Limitations

- **Privacy decides what you see.** An empty library response is never treated as proof of an empty library.
- **Recent games** use Steam's own window (about two weeks); there is no date range.
- **Achievements** do not exist or are hidden for some games; that is reported as unavailable, not as zero.
- **Game details** come only from your library and the game schema: no description, genres or release date
  (those need the unofficial Store API, which is not used). `iconUrl` is always `null`.
- **News is untrusted.** Text is reduced to plain text, only `https` links are kept, nothing is followed.
- **429 from Steam** is returned to the caller as a retryable error; the server does not wait and retry.
- **Tool calls run one at a time** (works around a response-dropping bug in the MCP SDK's STDIO transport, see
  `docs/decisions.md` D18).
- Single user, STDIO only. There is no HTTP transport and no multi-account support.
- The publisher host `partner.steam-api.com` and publisher keys are not used.

## Security notes

Read-only by design. The key and SteamID come from the environment only; the key is sent to Steam in the query
string (as Steam requires) and is kept out of logs, errors, `toString` and tool results (tests assert this for ten
upstream failure modes). Logs go to stderr; stdout carries MCP frames only. Redirects are not followed and only
the allowlisted Steam origin is contacted.

## Development

| Task | Command |
|---|---|
| Full build and all tests | `./mvnw -B clean verify` |
| Unit and Spring tests | `./mvnw test` |
| One test class | `./mvnw -Dtest=LibraryServiceTest test` |
| Optional live smoke test (your key) | `STEAM_API_KEY=... STEAM_ID=... ./mvnw -Plive test` |

Layers: `tool` (MCP adapters, no logic) -> `service` (validation, defaults, filtering, joins) ->
`client` (cache, retry, concurrency limit, declarative `SteamWebApi`) -> Steam. Public DTOs are in `model`,
upstream shapes in `steamdto`.

Documentation: [`docs/decisions.md`](docs/decisions.md) (all decisions and deviations from the spec),
[`docs/api-mapping.md`](docs/api-mapping.md), [`docs/test-plan.md`](docs/test-plan.md),
[`docs/acceptance-checklist.md`](docs/acceptance-checklist.md), [`docs/pre-mortem.md`](docs/pre-mortem.md),
[`docs/skill-review.md`](docs/skill-review.md), [`docs/dependency-licenses.md`](docs/dependency-licenses.md),
[`CHANGELOG.md`](CHANGELOG.md), and the original specification in `.tasks/`.

## Upgrade and rollback

Releases are single jars. To roll back, point the host configuration at the previous jar and restart the host.
There is no state or migration to undo.
