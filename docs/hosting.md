# Hosting over HTTPS

**Status: not supported yet. The server speaks STDIO only. This note records the target setup and what must exist before going public.**

## 1. Setup
MCP client -> HTTPS -> reverse proxy or managed platform (TLS, HSTS, request size and time limits) -> one app instance on loopback or a private network. The cache and the concurrency limit live in the process, so run a single instance (no load balancing).

## 2. Transport
MCP Streamable HTTP through the Spring AI MCP WebMVC server starter, in a separate Maven profile. The default jar stays STDIO-only, so the "no web starter on the classpath" rule still holds for it. Check exact starter and property names against the Spring AI 2.0 docs when implementing.

## 3. Auth (must exist before going public)
Without auth, anyone who can reach the server reads the owner's library through the owner's Steam key. The MCP authorization spec uses OAuth 2.1 with protected resource metadata, which remote MCP clients such as claude.ai connectors expect. A static bearer token is simpler but only works with clients that let you set headers. Never accept tokens in query strings.

## 4. Who it serves
One owner. `STEAM_API_KEY` and `STEAM_ID` come from platform secrets, never from clients and never baked into the image. Serving several users is a separate design (per-user SteamID, `SteamIdValidator.requireValid`; cache keys already include the SteamID).

## 5. Concurrency
Over HTTP, requests run in parallel on server threads (D18's serial handling applies to STDIO only). `@ConcurrencyLimit` caps parallel Steam calls on the shared key. Concurrent cache misses for the same data may each call Steam; acceptable behind the limit. Add rate limiting per client at the proxy.

## 6. Hardening
- Validate the `Origin` header (required by the MCP spec against DNS rebinding).
- Keep the Steam base-URL allowlist.
- Keep Actuator off or private.
- Run as a non-root container.
- Never log `Authorization` headers or query strings at the proxy.

## 7. Open decisions
Auth option and hosting platform.
