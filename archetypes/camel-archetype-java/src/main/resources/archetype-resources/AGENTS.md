# AGENTS.md

Guidance for AI coding assistants working on this project, which was generated from the
`camel-archetype-java` Maven archetype.

## Start here

- Apache Camel LLM index: https://camel.apache.org/llms.txt
- Any Apache Camel documentation page is available as LLM-friendly Markdown by replacing `.html` with `.md` in its URL.
- Prefer the Camel CLI and the Camel MCP server (both linked from the index above) to look up components, their endpoint options and the catalog. Do not invent component URIs or options — verify them against the catalog or the documentation.

## Project layout

- `src/main/java/<package>/MyRouteBuilder.java` — the Camel routes.
- `src/main/java/<package>/MainApp.java` — starts the application via `org.apache.camel.main.Main`.
- `src/main/resources/log4j2.properties` — logging.
- `src/data` — sample messages consumed by the generated route.

## Build and run

- Build: `mvn install`
- Run: `mvn camel:run`

## Conventions

- Add routes as `RouteBuilder` classes and register them on `Main` the same way as `MyRouteBuilder`, with `main.configure().addRoutesBuilder(...)`. This archetype does not scan for routes.
- Keep endpoint configuration in `pom.xml` dependencies plus route URIs; add a component dependency before using its scheme.
