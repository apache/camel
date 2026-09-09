# Maven Dependencies

The Maven Dependencies tab shows the declared dependencies for the
selected integration, as they would appear in a pom.xml file. Unlike
the Classpath tab (which shows all JARs on the JVM classpath including
internal infrastructure), this tab filters out internal bootstrap JARs
and shows only the dependencies the application actually declares.

## Data Sources

The tab discovers dependencies from (in order of priority):

- **pom.xml** — For exported Maven projects (Spring Boot, Quarkus).
  Parses compile-scoped dependencies from the project's pom.xml.
- **.camel-jbang/camel-jbang-run.properties** — For Camel CLI (JBang)
  mode. Reads the `dependency=mvn:...` lines that list all declared
  dependencies.

The data source is shown in the title bar (e.g., "pom.xml" or "jbang").

## Table Columns

- **GROUP:ARTIFACT** — Maven coordinate (e.g., `org.apache.camel:camel-core`)
- **VERSION** — The dependency version (e.g., `4.22.0`)

Each row shows the Maven coordinate and its version.

## Sort

Press `s` to cycle sort column (artifact, version).
Press `S` to reverse sort order.

## Scope

Press `f` to cycle the scope filter:

- **all** — show all dependencies (default)
- **camel** — show only Apache Camel dependencies
- **other** — show only non-Camel (third-party) dependencies

The active scope is shown in the footer and title bar.

## Transitive Dependencies

Press `t` to resolve transitive dependencies on demand. The first
press triggers Maven resolution (shown as "resolving..." in the
footer). Once resolved, transitive dependencies appear dimmed in
the table with a **VIA** column showing which direct dependency
pulled them in. Subsequent presses toggle between showing all
dependencies (direct + transitive) and direct only.

The title shows "(+transitive)" when transitive mode is active.
This is useful for CVE auditing — when a transitive JAR has a
known vulnerability, the VIA column tells you which direct
dependency to upgrade or exclude.

## Filter

Press `/` to open the filter input. Type a search term and press
`Enter` to filter by substring match. For example, type `kafka` to
find Kafka-related dependencies, or `spring` to find Spring
dependencies. The scope and text filter work together.

## When To Use

- **CVE auditing**: Review only the real application dependencies, not
  internal infrastructure JARs that leak in via camel-kamelet-main
- **Dependency review**: Understand what your integration actually
  depends on
- **Version checking**: Verify specific dependency versions
- **Transitive analysis**: See the full dependency tree similar to
  `mvn dependency:tree` (flat list)

## Keys

- `s` — cycle sort column
- `S` — reverse sort order
- `f` — cycle scope (all, camel, other)
- `t` — resolve and toggle transitive dependencies
- `/` — open filter
- `Esc` — clear filter or back
