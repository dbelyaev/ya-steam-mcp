# Status
- Done: P0; P1; P1R (all items incl. both spikes). Client now uses `@Retryable` (I/O + 5xx only, 429 fails fast) with concurrency limit below it (D11, D11a); declarative `SteamWebApi` (D12). Spec bumped to v1.3.
- Next: P2 core tools (LibraryService, get_library, get_playtime, get_recent_games). Waiting for go.
- Blockers: none.
- Notes: NullAway on main and tests. 88 tests.
