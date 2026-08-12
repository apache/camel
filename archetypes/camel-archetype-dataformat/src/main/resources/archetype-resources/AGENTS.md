# AGENTS.md

Guidance for AI coding assistants working on this project, which was generated from the
`camel-archetype-dataformat` Maven archetype.

## Start here

- Apache Camel LLM index: https://camel.apache.org/llms.txt
- Any Apache Camel documentation page is available as LLM-friendly Markdown by replacing `.html` with `.md` in its URL.
- Prefer the Camel CLI and the Camel MCP server (both linked from the index above) to look up components, their endpoint options and the catalog. Do not invent component URIs or options — verify them against the catalog or the documentation.

## Project layout

- `src/main/java` — the `org.apache.camel.spi.DataFormat` implementation.
- `src/test/java` — tests based on `CamelTestSupport`.

## Build and test

- Build: `mvn install`
- Test: `mvn test`

## Conventions

- Implement both `marshal` and `unmarshal`, and stream from the given `InputStream`/`OutputStream` instead of buffering whole messages where possible.
- Use the Camel type converters (`exchange.getContext().getTypeConverter()`) rather than casting the body directly.
- The data format is registered by the `@org.apache.camel.spi.annotations.Dataformat("<scheme>")` annotation on the class; there is no `META-INF/services` file to add. Keep the annotation value and `getDataFormatName()` in sync.
- Data formats in Camel: https://camel.apache.org/manual/data-format.html
- Writing the implementation: https://camel.apache.org/manual/writing-components.html
