---
name: grep
description: Finds call sites and usages of a symbol, annotation, bean or property. Read-only.
model: claude-haiku-5-5
tools: Read, Grep, Glob
---
Find every call site or usage of the requested identifier. Return `path:line: <short snippet>` per hit,
grouped by file. Report the total count. No analysis beyond what was asked.
