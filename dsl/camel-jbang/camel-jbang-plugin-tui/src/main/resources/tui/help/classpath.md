# Classpath

The Classpath tab shows all JAR files on the integration's classpath,
parsed into Maven coordinates (groupId, artifactId, version). This is
useful for verifying which dependency versions are in use, finding
unexpected or duplicate JARs, and understanding the integration's
dependency footprint.

## Table Columns

- **GROUP:ARTIFACT** — Maven coordinate of the JAR (e.g., `org.apache.camel:camel-core-model`)
- **VERSION** — The version of the dependency (e.g., `4.12.0`)

Camel JARs (those with `org.apache.camel` group) are displayed in
bold. Other dependencies are dimmed for visual distinction.

## Example Screen

```
 org.apache.camel:camel-api                 4.12.0
 org.apache.camel:camel-core-model           4.12.0
 org.apache.camel:camel-support              4.12.0
 org.apache.camel:camel-yaml-dsl             4.12.0
 com.fasterxml.jackson.core:jackson-core     2.18.3
 org.slf4j:slf4j-api                         2.0.16
```

## Scope

Press `f` to cycle the scope filter:

- **all** — show all classpath entries (default)
- **camel** — show only Apache Camel JARs
- **other** — show only non-Camel (third-party) JARs

The active scope is shown in the footer and title bar.

## Filter

Press `/` to open the filter input. Type a search term and press
`Enter` to filter the classpath by substring match. For example,
type `kafka` to find all Kafka-related JARs, or `jackson` to find
Jackson dependencies. The scope and text filter work together.

## When To Use

- **Version conflicts**: Check if the expected version of a library is present. Multiple versions of the same library can cause class loading issues
- **Missing dependencies**: Verify that a component's dependency JAR is on the classpath
- **Dependency audit**: Review all transitive dependencies pulled in by the integration
- **Size analysis**: Get a sense of how many JARs are loaded — large classpaths can slow startup

## Keys

- `Up/Down` — navigate entries
- `PgUp/PgDn` — scroll by page
- `f` — cycle scope (all, camel, other)
- `/` — open filter
- `Esc` — clear filter or back
