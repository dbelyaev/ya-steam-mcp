# Status
- Done: P0 bootstrap; P1 Steam client; P1R skill alignment (key redaction, Jakarta validation, @NullMarked + NullAway, test names, Mockito agent, tool logging, total-timeout + @ConcurrencyLimit, declarative SteamWebApi).
- Next: P2 core tools (LibraryService, get_library, get_playtime, get_recent_games). Waiting for go.
- Blockers: none.
- Notes: `SteamException(ErrorCode, msg)` in `model/` is the single public failure type. NullAway enforced on main sources (tests excluded). Decisions D11 (keep custom retry loop) and D12 (declarative client) recorded.
