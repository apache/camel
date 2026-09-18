# Camel API Component Project

A custom API-based Apache Camel component generated from the `camel-archetype-api-component` Maven archetype.

This is a multi-module project:

- `*-api` — the Java API that the component proxies.
- `*-component` — the Camel component, generated from that API by `camel-api-component-maven-plugin`
  using the API signatures under `*-component/signatures`.

## Build

    mvn install

## For AI coding assistants

See `AGENTS.md` in this directory, and start from the Apache Camel LLM index:
https://camel.apache.org/llms.txt

## More information

Writing custom components: https://camel.apache.org/manual/writing-components.html
