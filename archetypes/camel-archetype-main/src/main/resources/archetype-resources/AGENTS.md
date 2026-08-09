# AGENTS.md

Guidance for AI coding assistants working on this project, which was generated from the
`camel-archetype-main` Maven archetype.

## Start here

- Apache Camel LLM index: https://camel.apache.org/llms.txt
- Any Apache Camel documentation page is available as LLM-friendly Markdown by replacing `.html` with `.md` in its URL.
- Prefer the Camel CLI and the Camel MCP server (both linked from the index above) to look up components, their endpoint options and the catalog. Do not invent component URIs or options — verify them against the catalog or the documentation.

## Project layout

- `src/main/java/<package>/MyRouteBuilder.java` — the Camel routes.
- `src/main/java/<package>/MyApplication.java` — starts the application via `org.apache.camel.main.Main`.
- `src/main/java/<package>/MyBean.java`, `src/main/java/<package>/MyConfiguration.java` — beans wired by Camel's built-in dependency injection.
- `src/main/resources/application.properties` — Camel Main configuration.
- `src/test/java/<package>/MyApplicationTest.java` — test using `CamelMainTestSupport`.

## Build and run

- Build: `mvn install`
- Run: `mvn camel:run`
- Test: `mvn test`

## Conventions

- Configure Camel through `application.properties` (`camel.main.*`, `camel.component.*`) rather than in code where possible.
- Register beans with `@BindToRegistry`, and inject with `@BeanInject` and `@PropertyInject`.
