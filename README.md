# Apache Camel

[![Maven Central](https://img.shields.io/maven-central/v/org.apache.camel/apache-camel.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.apache.camel/apache-camel)
[![Javadocs](https://www.javadoc.io/badge/org.apache.camel/apache-camel.svg?color=brightgreen)](https://www.javadoc.io/doc/org.apache.camel/camel-api)
[![Stack Overflow](https://img.shields.io/:stack%20overflow-apache--camel-brightgreen.svg)](http://stackoverflow.com/questions/tagged/apache-camel)
[![Chat](https://img.shields.io/badge/zulip-join_chat-brightgreen.svg)](https://camel.zulipchat.com/)
[![Twitter](https://img.shields.io/twitter/follow/ApacheCamel.svg?label=Follow&style=social)](https://twitter.com/ApacheCamel)

[Apache Camel](https://camel.apache.org/) is an open source integration framework with 350+ connectors for databases, APIs, message brokers, and cloud services. Write routes in Java, YAML, or XML. Run on [Spring Boot](https://camel.apache.org/camel-spring-boot/latest/), [Quarkus](https://camel.apache.org/camel-quarkus/latest/), or standalone with the [Camel CLI](https://camel.apache.org/manual/camel-jbang.html). In production since 2007 — used by thousands of companies worldwide. Apache License 2.0.

[What is Apache Camel?](https://camel.apache.org/what-is-apache-camel/) | [Getting Started](https://camel.apache.org/manual/getting-started.html) | [Components](https://camel.apache.org/components/latest/) | [Tooling](https://camel.apache.org/tooling/)

## Get started in seconds

```bash
camel init hello.yaml
camel run hello.yaml
```

Or add to your existing Spring Boot project:

```xml
<dependency>
    <groupId>org.apache.camel.springboot</groupId>
    <artifactId>camel-spring-boot-starter</artifactId>
</dependency>
```

## Write it your way

The same route in YAML, Java, or XML — pick what fits your team:

**YAML:**
```yaml
- route:
    from:
      uri: kafka:incoming-orders
      steps:
        - unmarshal:
            json: {}
        - to:
            uri: sql:INSERT INTO orders(id, data) VALUES(:#${header.id}, :#${body})
```

**Java:**
```java
from("kafka:incoming-orders")
    .unmarshal().json()
    .to("sql:INSERT INTO orders(id, data) VALUES(:#${header.id}, :#${body})");
```

## Runtimes

| Runtime | What it does |
|---------|-------------|
| **[Camel Spring Boot](https://camel.apache.org/camel-spring-boot/latest/)** | Camel on Spring Boot with starters for 350+ connectors |
| **[Camel Quarkus](https://camel.apache.org/camel-quarkus/latest/)** | Cloud-native Camel with fast startup, low memory, native compilation |
| **[Camel CLI](https://camel.apache.org/manual/camel-jbang.html)** | Run, develop, test, and trace routes from the command line |

Other runtimes: [Camel K](https://camel.apache.org/camel-k/latest/) (Kubernetes), [Camel Karaf](https://camel.apache.org/manual/camel-on-osgi.html) (OSGi), [Camel Kafka Connector](https://camel.apache.org/camel-kafka-connector/latest/) (Kafka Connect)

## Components

350+ connectors for connecting to anything — Kafka, REST, JDBC, AWS, Azure, GCP, Salesforce, and more:

* [Components](https://camel.apache.org/components/latest/)
* [Enterprise Integration Patterns (EIPs)](https://camel.apache.org/components/latest/eips/enterprise-integration-patterns.html)
* [Data Formats](https://camel.apache.org/components/latest/dataformats/)
* [Languages](https://camel.apache.org/components/latest/languages/)

## AI integration

Apache Camel provides an [MCP server](https://camel.apache.org/manual/camel-jbang-mcp.html) (Model Context Protocol) for AI coding assistants — Claude Code, GitHub Copilot, Cursor, and Gemini CLI get full Camel catalog context. Camel also includes components for [LangChain4j](https://camel.apache.org/components/latest/langchain4j-chat-component.html) and [OpenAI](https://camel.apache.org/components/latest/openai-component.html), and supports the [A2A](https://google.github.io/A2A/) agent-to-agent protocol for connecting AI agents to enterprise systems.

## Visual designers

* [Kaoto](https://kaoto.io) — open source visual designer for Camel routes, drag-and-drop, no code required
* [Karavan](https://github.com/apache/camel-karavan) — visual designer for Camel integrations in VS Code and standalone

## Examples

* [Camel CLI Examples](https://github.com/apache/camel-jbang-examples) — YAML and scripting examples
* [Camel Examples](https://github.com/apache/camel-examples) — Camel Standalone examples
* [Camel Spring Boot Examples](https://github.com/apache/camel-spring-boot-examples) — Camel Spring Boot integration
* [Camel Quarkus Examples](https://github.com/apache/camel-quarkus-examples) — Camel Quarkus integration

## Contributing

We welcome all kinds of contributions:

<https://github.com/apache/camel/blob/main/CONTRIBUTING.md>

## Community

* User Stories: <https://camel.apache.org/community/user-stories/>
* Website: <https://camel.apache.org/>
* Issue tracker: <https://issues.apache.org/jira/projects/CAMEL>
* Mailing list: <https://camel.apache.org/community/mailing-list/>
* Chat: <https://camel.zulipchat.com/>
* Stack Overflow: <https://stackoverflow.com/questions/tagged/apache-camel>

## Licensing

Apache License 2.0 — see [LICENSE.txt](LICENSE.txt).
