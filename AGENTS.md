# Apache Camel - AI Agent Guidelines

Guidelines for AI agents working on this codebase.

## Project Info

Apache Camel is an integration framework supporting routing rules in Java, XML and YAML DSLs.

- Version: 4.19.0-SNAPSHOT
- Java: 17+ (21 for Virtual Threads)
- Build: Maven 3.9.12+

## Structure

```
camel/
├── core/           # Core modules (camel-api, camel-core, camel-support, etc.)
├── components/     # 300+ components (kafka, aws, http, etc.)
├── dsl/            # DSLs (jbang, yaml-dsl, endpointdsl)
├── test-infra/     # Testcontainers-based test services
├── catalog/        # Component metadata
├── tooling/        # Maven plugins
├── archetypes/     # Maven archetypes
├── tests/          # Integration tests
└── docs/           # AsciiDoc docs
```

## Build

```bash
mvn clean install -Dquickly          # fast build, no tests
mvn clean install                     # full build
mvn clean install -pl components/camel-kafka -am  # single module
mvn formatter:format && mvn impsort:sort          # format code
mvn clean install -Psourcecheck      # verify style
```

## Testing

```bash
mvn test              # unit tests
mvn verify -Pit       # integration tests
```

Test infra usage:
```java
@RegisterExtension
static KafkaService service = KafkaServiceFactory.createService();
```

## Component Layout

```
camel-<name>/
├── pom.xml
└── src/
    ├── main/java/org/apache/camel/component/<name>/
    │   ├── <Name>Component.java
    │   ├── <Name>Endpoint.java
    │   ├── <Name>Producer.java
    │   └── <Name>Consumer.java
    ├── main/docs/
    ├── generated/
    └── test/java/
```

## Conventions

Classes:
- `<Name>Component extends DefaultComponent`
- `<Name>Endpoint extends DefaultEndpoint`
- `<Name>Producer extends DefaultProducer`
- `<Name>Consumer extends DefaultConsumer`
- Tests: `*Test.java` (JUnit 5)

Packages:
- Components: `org.apache.camel.component.<name>`
- Core: `org.apache.camel.<module>`

Annotations:
- `@UriPath` for path params
- `@UriParam` for query params
- Always add `description` for docs

## Adding Components

### Direct child of components/

No extra config needed.

### Inside a parent folder (camel-aws, camel-ai, camel-azure, etc.)

You must register the component in MojoHelper:

**File:** `tooling/maven/camel-package-maven-plugin/src/main/java/org/apache/camel/maven/packaging/MojoHelper.java`

Add to the `getComponentPath()` switch:

```java
case "camel-aws":
    return Arrays.asList(dir.resolve("camel-aws2-athena"),
            // ...
            dir.resolve("camel-your-new-component"));
```

Parent folders needing registration:
- `camel-ai` (langchain4j, vector dbs)
- `camel-aws`
- `camel-azure`
- `camel-google`
- `camel-ibm`
- `camel-huawei`
- `camel-debezium`
- `camel-vertx`
- `camel-microprofile`
- `camel-infinispan`
- `camel-cxf`
- `camel-spring-parent`
- `camel-test`

Without this, the build won't pick up your component for catalog/docs generation.

## Commits

```
CAMEL-XXXX: Brief description
```

Reference JIRA when applicable.

## Links

- https://camel.apache.org/
- https://github.com/apache/camel
- https://issues.apache.org/jira/browse/CAMEL
- dev@camel.apache.org
- https://camel.zulipchat.com/
