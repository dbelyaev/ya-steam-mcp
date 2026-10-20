# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Spring Boot 4 Project Guidance

## Stack

- Spring Boot 4.1.1, Spring Framework 7, Java 25 (`java.version` in `pom.xml`)
- Jakarta EE 11, Jackson 3, and Boot 4 modular starters
- Build with the checked-in Maven wrapper

## Current State

- Release candidate 0.1.0: a read-only Steam MCP server over STDIO (package `io.dmitriy.yasmcp`, jar `target/steam-mcp-server-<version>.jar`). Six tools in `tool/SteamTools`, services in `service/`, Steam access in `client/`, specification in `.tasks/steam-mcp-server-spec.md` (v1.3), decisions and deviations in `docs/decisions.md`.
- Stack beyond the starter: Spring AI MCP server starter (annotations), `RestClient` with a declarative `SteamWebApi`, Caffeine, Jakarta validation, JSpecify + NullAway (main and tests), WireMock, failsafe end-to-end tests.
- Hard rules: stdout carries MCP frames only (no `System.out`, logs to stderr); the Steam API key must never reach logs, results, `toString` or exceptions; no web starter on the classpath (it changes MCP transport selection).

## Commands

- Build and verify (unit + end-to-end, needs JDK 25, no Steam key): `./mvnw -B clean verify`
- Unit and Spring tests: `./mvnw test`
- Single test: `./mvnw -Dtest=ClassName test`
- Live smoke test with your own key: `STEAM_API_KEY=... STEAM_ID=... ./mvnw -Plive test`

## Conventions

- Read neighboring classes and the dependency tree before generating code.
- Keep controllers thin, business rules in services, and persistence behind repositories.
- Use DTOs at HTTP and messaging boundaries.
- Use Boot 4 dedicated main and test starters and Jackson 3 `tools.jackson` APIs.
- Use `@MockitoBean`/`@MockitoSpyBean`; configure MockMvc or RestTestClient explicitly.
- Preserve existing error, security, pagination, nullability, and observability conventions.

## Skills

Use matching folders under `.claude/skills/`. Combine only the narrow skills required by the
change, including `spring-boot-migration` when upgrading an existing Boot 3 application.

## Completion

Run focused tests followed by `./mvnw verify`. Test the native executable when AOT behavior changes.

## Delegation

Delegate all repository searches and test runs to Haiku 5.5 subagents (`search`, `grep`, `audit`) to keep main context under 10k tokens.
