# Apache Camel - AI Agent Guidelines

Guidelines for AI agents working on this codebase.

## Project Info

Apache Camel is an integration framework supporting routing rules in Java, XML and YAML DSLs.

- Version: 4.19.0-SNAPSHOT
- Java: 21+
- Build: Maven 3.9.12+

## AI Agent Rules of Engagement

These rules apply to ALL AI agents working on this codebase.

### Attribution

- All AI-generated content (GitHub PR descriptions, review comments, JIRA comments) MUST clearly
  identify itself as AI-generated and mention the human operator.
  Example: "_Claude Code on behalf of [Human Name]_"

### PR Volume

- An agent MUST NOT open more than 10 PRs per day per operator to ensure human reviewers can keep up.
- Prioritize quality over quantity — fewer well-tested PRs are better than many shallow ones.

### Branch Ownership

- An agent MUST NEVER push commits to a branch it did not create.
- If a contributor's PR needs changes, the agent may suggest changes via review comments,
  but must not push to their branch without explicit permission.

### JIRA Ticket Ownership

- An agent MUST ONLY pick up **Unassigned** JIRA tickets.
- If a ticket is already assigned to a human, the agent must not reassign it or work on it.
- Before starting work, the agent must assign the ticket to its operator and transition it to "In Progress".
- Before closing a ticket, always set the correct `fixVersions` field.
  Note: `fixVersions` cannot be set on an already-closed issue — set it before closing,
  or reopen/set/close if needed.

### Merge Requirements

- An agent MUST NOT merge a PR if there are any **unresolved review conversations**.
- An agent MUST NOT merge a PR without at least **one human approval**.
- An agent MUST NOT approve its own PRs — human review is always required.

### Code Quality

- Every PR must include tests for new functionality or bug fixes.
- Every PR must include documentation updates where applicable.
- All code must pass formatting checks (`mvn formatter:format impsort:sort`) before pushing.
- All generated files must be regenerated and committed (CI checks for uncommitted changes).

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

Deprecation:
- Add `(deprecated)` suffix to `<name>` in `pom.xml`: `<name>Camel :: MyComponent (deprecated)</name>`
- Add `(deprecated)` suffix to the doc page title in `src/main/docs/*.adoc`
- Add `@Deprecated` to Java classes
- Document in the upgrade guide (`docs/user-manual/modules/ROOT/pages/camel-4x-upgrade-guide-4_XX.adoc`)

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
