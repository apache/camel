# AGENTS.md

Guidance for AI coding assistants working on this project, which was generated from the
`camel-archetype-api-component` Maven archetype.

## Start here

- Apache Camel LLM index: https://camel.apache.org/llms.txt
- Any Apache Camel documentation page is available as LLM-friendly Markdown by replacing `.html` with `.md` in its URL.
- Prefer the Camel CLI and the Camel MCP server (both linked from the index above) to look up components, their endpoint options and the catalog. Do not invent component URIs or options — verify them against the catalog or the documentation.

## Project layout

This is a multi-module project:

- `*-api` — the Java API that this component proxies.
- `*-component` — the Camel component generated from that API.
- `*-component/signatures` — the API signature files consumed by `camel-api-component-maven-plugin`.

## Build and test

- Build: `mvn install` from the project root (the `*-api` module must be built before the `*-component` module).
- Test: `mvn test`

## Conventions

- The API method proxies and collections are generated at build time by `camel-api-component-maven-plugin` from the API signatures. Do not hand-edit them — change the API or the signature files instead.
- The endpoint and configuration classes under `*-component/src/main/java` are hand-written: annotate the endpoint with `@UriEndpoint` and its options with `@UriPath`, `@UriParam` and `@Metadata`.
- Reference: https://camel.apache.org/manual/writing-components.html
