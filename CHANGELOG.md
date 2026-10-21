# Changelog

## [Unreleased]

### Changed
- Project renamed from `yasmcp` to `ya-steam-mcp`: artifact, jar (`ya-steam-mcp-<version>.jar`), MCP server name, Java package `io.dmitriy.yasteammcp`. Update host configurations that hard-code the old jar path.

## [0.1.0] - 2026-10-20 (release candidate)

### Added
- Six read-only MCP tools over STDIO: `get_library`, `get_playtime`, `get_recent_games`, `get_achievements`,
  `get_game_news`, `get_game_details`.
- Steam Web API client with allowlisted base URL, explicit timeouts, no redirects, retry for I/O errors and 5xx,
  overall deadline, concurrency limit and per-endpoint response cache.
- Structured responses with provenance (`meta`), stable error codes and `isError` on failures.
- News sanitizer (plain text, https links only) and a language allowlist.
- Unit, WireMock, Spring and end-to-end tests, an opt-in live smoke test, GitHub Actions on JDK 25.

### Notes
- Behavior differs from the original specification in documented ways; see `docs/decisions.md`
  (retry policy and 429, serial tool execution, visibility status values, game-details policy).
