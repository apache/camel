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

## The Camel Version

The line above the table tells where the Camel version of the
integration stands, from the known releases of the catalog: whether it
is an LTS release, its end of life, the latest patch release of the same
line, the latest LTS release and the latest release. The Camel JARs move
together, so they have no newer versions of their own: upgrade Camel
instead.

## Table Columns

- **GROUP:ARTIFACT** — Maven coordinate (e.g., `org.apache.camel:camel-core`)
- **VERSION** — The dependency version (e.g., `4.22.0`)
- **CAMEL** — The version the Camel dependencies bring of this library,
  empty when Camel does not bring it
- **STATUS** — How the version relates to Camel (see below)
- **NEWER** — The latest release of the same major.minor line, when
  newer (after `u`)
- **LATEST** — The latest release, marked `(major)` when it is another
  major version (after `u`)
- **CVES** — The known vulnerabilities of the JAR, from the CVE Audit tab
  (after `u`)

The line below the table says, in words, what the status of the
selected dependency means.

## Status

- **conflict** — the JAR is loaded in more than one version, or in
  another version than the one declared. A declared old version of a
  library Camel also brings ends up next to Camel's version.
- **mixed** — a Camel JAR of another Camel version than the one of the
  integration.
- **override** — the project declares another version than the one the
  Camel dependencies bring.
- **managed** — the version the Camel dependencies bring.
- **camel** — a Camel JAR of the Camel version of the integration.
- empty — Camel does not bring this library.

## Newer Versions and CVEs

Press `u` to look up the newer releases of the dependencies in the Maven
repositories of your Maven settings (Maven Central, mirrors), and to run
the CVE Audit for the CVES column. Nothing is looked up before. Press
`v` to open the CVE Audit tab on the selected dependency.

A newer release is not always a safe upgrade: a version newer than the
one the Camel dependencies bring, or another major version, may not work
with this Camel version.

The same check is a command: `camel dependency outdated`.

## Sort

Press `s` to cycle sort column (artifact, version, status, via).
Press `S` to reverse sort order.

## Scope

Press `f` to cycle the scope filter:

- **all** — show all dependencies (default)
- **camel** — show only Apache Camel dependencies
- **other** — show only non-Camel (third-party) dependencies
- **outdated** — show only the dependencies worth a look: a status to
  act on, a newer release or a CVE

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
- **Version checking**: Verify specific dependency versions, and find
  the newer ones
- **Upgrade planning**: Press `Shift+F8` to ask the AI which
  dependencies to upgrade and whether it is safe
- **Transitive analysis**: See the full dependency tree similar to
  `mvn dependency:tree` (flat list)

## Keys

- `s` — cycle sort column
- `S` — reverse sort order
- `f` — cycle scope (all, camel, other)
- `t` — resolve and toggle transitive dependencies
- `u` — look up the newer versions and the CVEs
- `v` — open the CVE Audit tab on the selected dependency
- `Shift+F8` — ask the AI which dependencies to upgrade
- `/` — open filter
- `Esc` — clear filter or back
