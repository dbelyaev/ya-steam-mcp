# Decisions

## D1 — Keep existing package/coordinates (2026-10-19)
Spec suggests `dev.steam.mcp` / `dev.steam:steam-mcp-server`. Repo already scaffolded as `io.dmitriy.yasmcp` / `io.dmitriy:yasmcp`. Kept the repo's. MCP server name stays `steam-mcp-server`.

## D2 — Spring AI 2.0.1 annotation package (2026-10-19)
`@McpTool` / `@McpToolParam` live in `org.springframework.ai.mcp.annotation` (artifact `spring-ai-mcp-annotations` 2.0.1), not `org.springaicommunity.mcp.annotation`.

## D3 — Verified versions (2026-10-19)
Boot 4.1.1 + spring-ai-bom 2.0.1 resolve and `./mvnw verify` passes on JDK 25. Context loads, 1 tool registered, STDIO handshake + tools/list + tools/call OK, stdout clean.

## Open
- Mockito self-attach warning on JDK 25; add Mockito agent to surefire argLine (P4).

## D4 — Explicit tool hints (2026-10-19)
Spring AI defaults to `readOnlyHint=false, destructiveHint=true`. Every tool sets `@McpTool.McpAnnotations(readOnlyHint=true, destructiveHint=false, idempotentHint=true, openWorldHint=true)`; verified in `tools/list`.
