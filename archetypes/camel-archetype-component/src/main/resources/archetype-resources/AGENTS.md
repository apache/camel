# AGENTS.md

Guidance for AI coding assistants working on this project, which was generated from the
`camel-archetype-component` Maven archetype.

## Start here

- Apache Camel LLM index: https://camel.apache.org/llms.txt
- Any Apache Camel documentation page is available as LLM-friendly Markdown by replacing `.html` with `.md` in its URL.
- Prefer the Camel CLI and the Camel MCP server (both linked from the index above) to look up components, their endpoint options and the catalog. Do not invent component URIs or options — verify them against the catalog or the documentation.

## Project layout

- `src/main/java` — the `Component`, `Endpoint`, `Producer` and `Consumer` classes of this component.
- `src/main/resources/META-INF/services/org/apache/camel/component/<scheme>` — registers the component for its URI scheme.
- `src/test/java` — tests based on `CamelTestSupport`.

## Build and test

- Build: `mvn install`
- Test: `mvn test`

## Conventions

- Annotate the endpoint with `@UriEndpoint`, and its options with `@UriPath`, `@UriParam` and `@Metadata`; these annotations drive the generated component metadata and documentation.
- Keep the file name under `META-INF/services/org/apache/camel/component` in sync with the endpoint scheme.
- Reference: https://camel.apache.org/manual/writing-components.html
