# Dependency licenses

Runtime dependencies of `ya-steam-mcp` (the executable jar), resolved with
`./mvnw dependency:list -DincludeScope=runtime` and read from each artifact's POM
(2026-10-20, spec section 9.9). Test-only libraries (JUnit, AssertJ, Mockito, WireMock) are not shipped.

**Project license:** Apache License 2.0 (`LICENSE`, `NOTICE`).

**Review result:** no dependency imposes obligations beyond attribution for use as an unmodified library.
Entries to be aware of when redistributing the jar:

- `ch.qos.logback:*` is dual-licensed EPL-2.0 / LGPL-2.1: used unmodified as a library.
- `jakarta.annotation:jakarta.annotation-api` is EPL-2.0 with GPL-2.0 + Classpath Exception: no copyleft impact for users.
- `org.hdrhistogram:HdrHistogram` is CC0 / BSD-2-Clause.
- The MCP Java SDK (`io.modelcontextprotocol.sdk:*`) is MIT.

Steam content (names, news, achievements) is third-party data fetched on the user's behalf with their own key.
This project does not store or redistribute it; fixtures in tests are synthetic.

| Artifact | Version | License (from its POM) |
|---|---|---|
| `ch.qos.logback:logback-classic` | 1.5.38 | EPL-2.0; LGPL-2.1-only |
| `ch.qos.logback:logback-core` | 1.5.38 | EPL-2.0; LGPL-2.1-only |
| `com.ethlo.time:itu` | 1.14.0 | Apache License, Version 2.0 |
| `com.fasterxml:classmate` | 1.7.3 | Apache License, Version 2.0 |
| `com.fasterxml.jackson.core:jackson-annotations` | 2.21 | The Apache Software License, Version 2.0 |
| `com.github.ben-manes.caffeine:caffeine` | 3.2.4 | Apache License, Version 2.0 |
| `com.github.victools:jsonschema-generator` | 5.0.0 | The Apache License, Version 2.0 |
| `com.github.victools:jsonschema-module-jackson` | 5.0.0 | The Apache License, Version 2.0 |
| `com.github.victools:jsonschema-module-swagger-2` | 5.0.0 | The Apache License, Version 2.0 |
| `com.google.errorprone:error_prone_annotations` | 2.49.0 | Apache 2.0 |
| `com.knuddels:jtokkit` | 1.1.0 | MIT License |
| `com.networknt:json-schema-validator` | 3.0.0 | Apache License Version 2.0 |
| `commons-logging:commons-logging` | 1.3.6 | Apache-2.0 |
| `io.micrometer:context-propagation` | 1.2.1 | The Apache Software License, Version 2.0 |
| `io.micrometer:micrometer-commons` | 1.17.1 | The Apache Software License, Version 2.0 |
| `io.micrometer:micrometer-core` | 1.17.1 | The Apache Software License, Version 2.0 |
| `io.micrometer:micrometer-observation` | 1.17.1 | The Apache Software License, Version 2.0 |
| `io.modelcontextprotocol.sdk:mcp` | 2.0.0 | MIT License |
| `io.modelcontextprotocol.sdk:mcp-core` | 2.0.0 | MIT License |
| `io.modelcontextprotocol.sdk:mcp-json-jackson3` | 2.0.0 | MIT License |
| `io.projectreactor:reactor-core` | 3.8.7 | Apache License, Version 2.0 |
| `io.swagger.core.v3:swagger-annotations-jakarta` | 2.2.38 | Apache License 2.0 |
| `jakarta.annotation:jakarta.annotation-api` | 3.0.0 | EPL 2.0; GPL2 w/ CPE |
| `jakarta.validation:jakarta.validation-api` | 3.1.1 | Apache License 2.0 |
| `org.antlr:ST4` | 4.3.4 | The BSD License |
| `org.antlr:antlr-runtime` | 3.5.3 | BSD licence |
| `org.antlr:antlr4-runtime` | 4.13.1 | BSD-3-Clause |
| `org.apache.logging.log4j:log4j-api` | 2.25.5 | Apache-2.0 |
| `org.apache.logging.log4j:log4j-to-slf4j` | 2.25.5 | Apache-2.0 |
| `org.apache.tomcat.embed:tomcat-embed-el` | 11.0.24 | Apache License, Version 2.0 |
| `org.hdrhistogram:HdrHistogram` | 2.2.2 | Public Domain, per Creative Commons CC0; BSD-2-Clause |
| `org.hibernate.validator:hibernate-validator` | 9.1.3.Final | Apache License 2.0 |
| `org.jboss.logging:jboss-logging` | 3.6.3.Final | Apache License 2.0 |
| `org.jspecify:jspecify` | 1.0.1 | The Apache License, Version 2.0 |
| `org.reactivestreams:reactive-streams` | 1.0.4 | MIT-0 |
| `org.slf4j:jul-to-slf4j` | 2.0.18 | MIT |
| `org.slf4j:slf4j-api` | 2.0.18 | MIT |
| `org.snakeyaml:snakeyaml-engine` | 3.0.1 | Apache License, Version 2.0 |
| `org.springframework:spring-aop` | 7.0.9 | Apache License, Version 2.0 |
| `org.springframework:spring-beans` | 7.0.9 | Apache License, Version 2.0 |
| `org.springframework:spring-context` | 7.0.9 | Apache License, Version 2.0 |
| `org.springframework:spring-core` | 7.0.9 | Apache License, Version 2.0 |
| `org.springframework:spring-expression` | 7.0.9 | Apache License, Version 2.0 |
| `org.springframework:spring-messaging` | 7.0.9 | Apache License, Version 2.0 |
| `org.springframework:spring-web` | 7.0.9 | Apache License, Version 2.0 |
| `org.springframework.ai:spring-ai-autoconfigure-mcp-server-common` | 2.0.1 | Apache 2.0 |
| `org.springframework.ai:spring-ai-commons` | 2.0.1 | Apache 2.0 |
| `org.springframework.ai:spring-ai-mcp` | 2.0.1 | Apache 2.0 |
| `org.springframework.ai:spring-ai-mcp-annotations` | 2.0.1 | Apache 2.0 |
| `org.springframework.ai:spring-ai-model` | 2.0.1 | Apache 2.0 |
| `org.springframework.ai:spring-ai-starter-mcp-server` | 2.0.1 | Apache 2.0 |
| `org.springframework.ai:spring-ai-template-st` | 2.0.1 | Apache 2.0 |
| `org.springframework.boot:spring-boot` | 4.1.1 | Apache License, Version 2.0 |
| `org.springframework.boot:spring-boot-autoconfigure` | 4.1.1 | Apache License, Version 2.0 |
| `org.springframework.boot:spring-boot-http-client` | 4.1.1 | Apache License, Version 2.0 |
| `org.springframework.boot:spring-boot-http-converter` | 4.1.1 | Apache License, Version 2.0 |
| `org.springframework.boot:spring-boot-jackson` | 4.1.1 | Apache License, Version 2.0 |
| `org.springframework.boot:spring-boot-restclient` | 4.1.1 | Apache License, Version 2.0 |
| `org.springframework.boot:spring-boot-starter` | 4.1.1 | Apache License, Version 2.0 |
| `org.springframework.boot:spring-boot-starter-jackson` | 4.1.1 | Apache License, Version 2.0 |
| `org.springframework.boot:spring-boot-starter-logging` | 4.1.1 | Apache License, Version 2.0 |
| `org.springframework.boot:spring-boot-starter-restclient` | 4.1.1 | Apache License, Version 2.0 |
| `org.springframework.boot:spring-boot-starter-validation` | 4.1.1 | Apache License, Version 2.0 |
| `org.springframework.boot:spring-boot-validation` | 4.1.1 | Apache License, Version 2.0 |
| `org.yaml:snakeyaml` | 2.6 | Apache License, Version 2.0 |
| `tools.jackson.core:jackson-core` | 3.1.5 | The Apache Software License, Version 2.0 |
| `tools.jackson.core:jackson-databind` | 3.1.5 | The Apache Software License, Version 2.0 |
| `tools.jackson.dataformat:jackson-dataformat-yaml` | 3.1.5 | The Apache Software License, Version 2.0 |
