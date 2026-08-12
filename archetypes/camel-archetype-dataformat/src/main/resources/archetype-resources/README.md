# Camel Data Format Project

A custom Apache Camel data format generated from the `camel-archetype-dataformat` Maven archetype.

The data format implementation is in `src/main/java` and implements `org.apache.camel.spi.DataFormat`
(`marshal` and `unmarshal`).

## Build

    mvn install

## For AI coding assistants

See `AGENTS.md` in this directory, and start from the Apache Camel LLM index:
https://camel.apache.org/llms.txt

## More information

Data formats in Camel: https://camel.apache.org/manual/data-format.html

Writing the implementation: https://camel.apache.org/manual/writing-components.html
