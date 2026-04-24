# Apache Camel - AI Agent Guidelines

Guidelines for AI agents working on this codebase.

## Project Info

Apache Camel is an integration framework supporting routing rules in Java, XML and YAML DSLs.

- Version: 4.20.0-SNAPSHOT
- Java: 17+
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

### Git branch

- An agent MUST NEVER push commits to a branch it did not create.
- If a contributor's PR needs changes, the agent may suggest changes via review comments,
  but must not push to their branch without explicit permission.
- An agent should prefer to use his own fork to push branches instead of the main apache/camel repository. It avoids to fill the main repository with a long list of uncleaned branches.
- An agent must provide a useful name for the git branch. It should contain the global topic and issue number if possible.
- After a Pull Request is merged or rejected, the branch should be deleted.

### JIRA Ticket Ownership

- An agent MUST ONLY pick up **Unassigned** JIRA tickets.
- If a ticket is already assigned to a human, the agent must not reassign it or work on it.
- Before starting work, the agent must assign the ticket to its operator and transition it to "In Progress".
- Before closing a ticket, always set the correct `fixVersions` field.
  Note: `fixVersions` cannot be set on an already-closed issue — set it before closing,
  or reopen/set/close if needed.

### PR Description Maintenance

When pushing new commits to a PR, **always update the PR description** (and title if needed) to
reflect the current state of the changeset. PRs evolve across commits — the description must stay
accurate and complete. Use `gh pr edit --title "..." --body "..."` after each push.

### PR Reviewers

When creating a PR, **always identify and request reviews** from the most relevant committers:

- Run `git log --format='%an' --since='1 year' -- <affected-files> | sort | uniq -c | sort -rn | head -10`
  to find who has been most active on the affected files.
- Use `git blame` on key modified files to identify who wrote the code being changed.
- Cross-reference with the [committer list](https://home.apache.org/committers-by-project.html#camel)
  to ensure you request reviews from active committers (not just contributors).
- For component-specific changes, prefer reviewers who have recently worked on that component.
- For cross-cutting changes (core, API), include committers with broader project knowledge.
- Request review from **at least 2 relevant committers** using `gh pr edit --add-reviewer`.
- When all comments on the Pull Request are addressed (by providing a fix or providing more explanation) and the PR checks are green, re-request review on existing reviewers so that they are aware that the new changeset is ready to be reviewed.

### Merge Requirements

- An agent MUST NOT merge a PR if there are any **unresolved review conversations**.
- An agent MUST NOT merge a PR without at least **one human approval**.
- An agent MUST NOT approve its own PRs — human review is always required.

### Code Quality

- Every PR must include tests for new functionality or bug fixes.
- Every PR must include documentation updates where applicable.
- All code must pass formatting checks (`mvn formatter:format impsort:sort`) before pushing.
- All generated files must be regenerated and committed (CI checks for uncommitted changes).

### Asynchronous Testing: Use Awaitility Instead of Thread.sleep

Do **NOT** use `Thread.sleep()` in test code. It leads to flaky, slow, and non-deterministic tests.
Use the [Awaitility](https://github.com/awaitility/awaitility) library instead, which is already
available as a test dependency in the project.

**Example — waiting for a route to be registered:**

```java
import static org.awaitility.Awaitility.await;

await().atMost(20, TimeUnit.SECONDS)
       .untilAsserted(() -> assertEquals(1, context.getRoutes().size()));
```

**Rules:**

- New test code MUST NOT introduce `Thread.sleep()` calls.
- When modifying existing test code that contains `Thread.sleep()`, migrate it to Awaitility.
- Always set an explicit `atMost` timeout to avoid hanging builds.
- Use `untilAsserted` or `until` with a clear predicate — do not replace a sleep with a
  busy-wait loop.

### Issue Investigation (Before Implementation)

Before implementing a fix for a JIRA issue, **thoroughly investigate** the issue's validity and context.
Camel is a large, long-lived project — code often looks "wrong" but exists for good reasons. Do NOT
jump straight to implementation after reading the issue description and the current code.

**Required investigation steps:**

1. **Validate the issue**: Confirm the reported problem is real and reproducible. Question assumptions
   in the issue description — they may be incomplete or based on misunderstanding.
2. **Check git history**: Run `git log --oneline <file>` and `git blame <file>` on the affected code.
   Read the commit messages and linked JIRA tickets for prior changes to understand *why* the code
   is written the way it is.
3. **Search for related issues**: Search JIRA for related tickets (same component, similar keywords)
   to find prior discussions, rejected approaches, or intentional design decisions.
4. **Look for design documents**: Check the `proposals/` directory for design docs (`.adoc` files)
   that may explain architectural decisions in the affected area. Key proposals by area:
   - **Security** (secrets, SSL/TLS, serialization, policy enforcement): [`proposals/security.adoc`](proposals/security.adoc)
   - **Tracing / Telemetry** (OpenTelemetry, spans, context propagation): [`proposals/tracing.adoc`](proposals/tracing.adoc)
   - **MDC / Logging** (MDC propagation, logging context): [`proposals/mdc.adoc`](proposals/mdc.adoc)
5. **Understand the broader context**: If the issue involves a module that replaced or deprecated
   another (e.g., `camel-opentelemetry2` replacing `camel-opentelemetry`), understand *why* the
   replacement was made and what was intentionally changed vs. accidentally omitted.
6. **Check if the "fix" reverts prior work**: If your proposed change effectively reverts a prior
   intentional commit, stop and reconsider. If the revert is still justified, explicitly acknowledge
   it in the PR description and explain why despite the original rationale.

**Present your findings** to the operator before implementing. Flag any risks, ambiguities, or cases
where the issue may be invalid or the proposed approach may conflict with prior decisions.

### Knowledge Cutoff Awareness

AI agents have a training data cutoff and may not know about recent releases, API changes, or
deprecations in external projects. **Never make authoritative claims about external project state
based solely on training knowledge.**

- When a JIRA issue, PR, or code references a specific version of an external dependency (e.g.,
  Spring Boot 4.0, JUnit 6, Jakarta EE 11), **verify it exists** by checking official sources
  (web search, Maven Central, release notes) before questioning or relying on it.
- When implementing or reviewing changes that depend on external project behavior, verify the
  current state rather than assuming training data is up to date.
- If uncertain about whether something exists or has changed, say so and verify — do not
  confidently assert something is wrong based on potentially stale knowledge.

### Git History Review (When Reviewing PRs)

When reviewing PRs, apply the same investigative rigor:

- Check `git log` and `git blame` on modified files to see if the change conflicts with prior
  intentional decisions.
- Verify that "fixes" don't revert deliberate behavior without justification.
- Check for design proposals (`proposals/*.adoc`) related to the affected area
  (see the area-to-proposal mapping in "Issue Investigation" above).
- Search for related JIRA tickets that provide context on why the code was written that way.

### Documentation Conventions

When writing or modifying `.adoc` documentation:

- **Use `xref:` for internal links**, never external `https://camel.apache.org/...` URLs.
  Example: `xref:manual::camel-jbang.adoc[Camel JBang]` instead of
  `https://camel.apache.org/manual/camel-jbang.html[Camel JBang]`.
- **Cross-version xref fragments**: When linking to a section anchor (e.g., `#_my_section`) using
  the `components::` prefix, verify that the target section exists in the **current released version**,
  not just on `main`. The `components::` prefix resolves to the latest released version, so anchors
  that only exist on `main` will produce broken links. Either omit the fragment or use a
  version-aware reference.
- **When reviewing doc PRs**, check that all `xref:` links and anchors resolve correctly, especially
  cross-component references that may span versions.

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
- Mark sensitive parameters with `secret = true` on `@UriParam` or `@Metadata` (passwords, tokens, API keys)
- For insecure configuration flags (e.g., `trustAllCertificates`, `allowJavaSerializedObject`),
  add `security = "insecure:ssl"` / `"insecure:serialization"` / `"insecure:dev"` on `@UriParam`.
  See [`proposals/security.adoc`](proposals/security.adoc) for categories and rationale.

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

## Adding Integration tests with new container image

- Avoid using Docker Hub images, prefer to use Google `mirror.gcr.io` or Red Hat `quay.io` ones.
- Verify that the container image is available for the tested architectures (currently `amd64`, `ppc64le` and `s390x`). Sometimes the image is provided in different registries such as `icr.io`. If not available, use `skipITs.ppc64le` and `skipITs.s390x` Maven properties to disable it.

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
