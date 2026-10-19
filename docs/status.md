# Status
- Done: P0 bootstrap; P1 Steam client. Skill review written (docs/skill-review.md), plan v3 adds P1R.
- Next: P1R (skill alignment, starts with API-key toString fix). Waiting for go.
- Blockers: none.
- Notes: `SteamException(ErrorCode, msg)` in `model/` is the single public failure type (client, validators, config). `INTERNAL_ERROR` added as catch-all code (not in spec table).
