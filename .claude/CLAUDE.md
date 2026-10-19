# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Spring Boot 4 Project Guidance

## Stack

- Spring Boot 4.1.1, Spring Framework 7, Java 25 (`java.version` in `pom.xml`)
- Jakarta EE 11, Jackson 3, and Boot 4 modular starters
- Build with the checked-in Maven wrapper

## Current State

- Fresh scaffold: single `YasmcpApplication` (package `io.dmitriy.yasmcp`), one context-load test, and only `spring-boot-starter` + `spring-boot-starter-test` dependencies. No web, MCP, or persistence layers exist yet.
- Add starters to `pom.xml` as features land; use the `mcp-server` skill when exposing MCP tools/resources/prompts.

## Commands

- Build and verify: `./mvnw verify`
- Unit tests: `./mvnw test`
- Single test: `./mvnw -Dtest=ClassName test`

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
