# Status
- Done: P0; P1; P1R; P2; P3 (AchievementService, NewsService, GameDetailsService, NewsSanitizer, SteamLanguage; get_achievements, get_game_news, get_game_details). 234 tests. All six tools smoke-tested through the packaged jar against a fake Steam server.
- Next: P4 hardening (cache decorator, secrets/stdout hygiene tests, STDIO end-to-end IT with failsafe). Waiting for go.
- Blockers: none.
- Notes: D17 records P3 contract choices. Steam config for the jar smoke test can be given by env: STEAM_API_BASE_URL + STEAM_API_ALLOW_INSECURE_TEST_BASE_URL (relaxed binding).
