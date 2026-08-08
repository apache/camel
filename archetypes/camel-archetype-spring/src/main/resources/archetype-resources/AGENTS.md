# AGENTS.md

Guidance for AI coding assistants working on this project, which was generated from the
`camel-archetype-spring` Maven archetype.

## Status

This project was generated from a **deprecated** archetype. It uses the plain Spring XML setup rather
than Camel on Spring Boot. Before investing in large changes here, consider whether the project should
be migrated to Camel on Spring Boot, or regenerated with the Camel CLI (`camel init`).

## Start here

- Apache Camel LLM index: https://camel.apache.org/llms.txt
- Any Apache Camel documentation page is available as LLM-friendly Markdown by replacing `.html` with `.md` in its URL.
- Prefer the Camel CLI and the Camel MCP server (both linked from the index above) to look up components, their endpoint options and the catalog. Do not invent component URIs or options — verify them against the catalog or the documentation.

## Project layout

- `src/main/resources/META-INF/spring/camel-context.xml` — the Spring XML context holding the Camel routes.
- `src/main/resources/log4j2.properties` — logging.
- `src/data` — sample messages consumed by the generated route.

## Build and run

- Build: `mvn install`
- Run: `mvn camel:run`

## Conventions

- Routes are defined in the Spring XML DSL inside `<camelContext>`; keep the XML valid against the Camel Spring schema.
- When adding a component, add its Maven dependency before referencing its scheme in an endpoint URI.
