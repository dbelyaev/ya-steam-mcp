# Status
- Done: P0-P5. Release candidate 0.1.0: six tools, cache, retry, STDIO end-to-end tests, CI, Apache-2.0 license, README, acceptance checklist.
- Verified: `./mvnw -B clean verify` on JDK 25 (264 surefire + 14 failsafe tests, 4 live tests skipped), jar `target/ya-steam-mcp-0.1.0-SNAPSHOT.jar`.
- Open for the owner: run `./mvnw -Plive test` once with your own key and SteamID; push to GitHub to see CI run; optional MCP Inspector check (see docs/acceptance-checklist.md).
- Blockers: none.
