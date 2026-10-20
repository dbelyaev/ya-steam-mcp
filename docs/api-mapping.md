# Steam Web API mapping

All calls go to `https://api.steampowered.com` (public host, regular user key). `partner.steam-api.com`
is never used; the base URL is allowlisted at startup. Transport is the declarative
`io.dmitriy.yasmcp.client.SteamWebApi`; parsing, retry and error mapping live in `SteamApiClientImpl`.

| MCP tool | Steam endpoint | Query parameters sent | Cache TTL |
|---|---|---|---|
| `get_library`, `get_playtime`, owned part of `get_game_details` | `GET /IPlayerService/GetOwnedGames/v1/` | `key`, `steamid`, `include_appinfo=true`, `include_played_free_games` | 5 min |
| `get_recent_games` | `GET /IPlayerService/GetRecentlyPlayedGames/v1/` | `key`, `steamid`, `count` | 2 min |
| `get_achievements` | `GET /ISteamUserStats/GetPlayerAchievements/v1/` | `key`, `steamid`, `appid`, `l` | 10 min |
| `get_achievements` (enrichment), `get_game_details` | `GET /ISteamUserStats/GetSchemaForGame/v2/` | `key`, `appid`, `l` | 24 h |
| `get_game_news` | `GET /ISteamNews/GetNewsForApp/v2/` | `appid`, `count`, `maxlength` (no key) | 10 min |

Not used on purpose: `GetSingleGamePlaytime` (partner host), `ResolveVanityURL` (not needed for the MVP),
Store `appdetails` (undocumented; enrichment stays disabled).

## Upstream status to public error code

| Upstream | Public code | Retryable | Notes |
|---|---|---|---|
| 2xx with expected envelope | success | | Empty `games` is data, not proof of an empty library |
| 400 with a parseable envelope | passed to the service | | Steam answers "no stats" this way for achievements |
| 400 without envelope, other 4xx, 3xx, empty/oversized/malformed body, missing envelope | `UPSTREAM_BAD_RESPONSE` | no | Redirects are never followed |
| 401, 403 | `UPSTREAM_AUTH_FAILED` | no | Steam uses 403 for a bad key |
| 429 | `UPSTREAM_RATE_LIMITED` | yes | Not retried by the server; `Retry-After` ignored (D11) |
| 5xx, I/O error, timeout | `UPSTREAM_UNAVAILABLE` | yes | Retried up to 2 times with backoff and jitter |
| Player stats `success:false`, error text mentions private/not public/profile | `DATA_NOT_ACCESSIBLE` | no | Steam's text is never echoed |
| Player stats `success:false` otherwise, or no envelope | `ACHIEVEMENTS_UNAVAILABLE` | no | |
| Game absent from owned games | `GAME_NOT_VISIBLE_OR_NOT_OWNED` | no | Does not claim the user does not own it |
| Neither library nor schema knows the game | `GAME_DETAILS_UNAVAILABLE` | no | |

Other codes: `INVALID_ARGUMENT` (validated in services), `CONFIGURATION_MISSING` (key or SteamID absent or
malformed, reported at call time), `INTERNAL_ERROR` (catch-all with a fixed message).

## Response fields that are intentionally null

- `iconUrl`: Steam returns only an icon hash.
- `description`, `genres`, `releaseDate`: need the Store catalog (disabled).
- `playtime*Minutes` per platform: only when Steam sends them.
- `hidden`, texts of an achievement: when the schema is unavailable (response is then `meta.partial=true`).
