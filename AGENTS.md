# Apache Camel - AI Agent Guidelines

Guidelines for AI agents working on this codebase. Apache Camel is an integration framework supporting routing rules in
Java, XML and YAML DSLs. Java 21+, Maven 3.9.12+.

## AI Agent Rules of Engagement

These rules apply to ALL AI agents working on this codebase.

### Attribution

- All AI-generated content (GitHub PR descriptions, review comments, JIRA comments) MUST clearly
  identify itself as AI-generated and mention the human operator.
  Example: "_Claude Code on behalf of [Human Name]_"
- **Never guess or hallucinate the operator's name.** Look it up with `gh api /user --jq '.login'`;
  if the lookup fails, omit the name.
- AI coding agents MUST be configured to add co-authorship trailers to commits
  (e.g., `Co-authored-by`). For Claude Code, enable this via the
  [attribution settings](https://code.claude.com/docs/en/settings#attribution-settings).

### PR Volume

- An agent MUST NOT open more than 10 PRs per day per operator to ensure human reviewers can keep up.
- Prioritize quality over quantity — fewer well-tested PRs are better than many shallow ones.

### Git branch

- An agent MUST NEVER push commits to a branch it did not create. If a contributor's PR needs changes,
  suggest them via review comments; do not push to their branch without explicit permission.
- An agent MUST push branches to its own fork, not to apache/camel (stale upstream branches waste CI time and
  disk space for all contributors).
- Give the branch a useful name, with the topic and the issue number if possible.
- After a PR is merged or rejected, delete the branch. The `pr-cleanup-branches.yml` workflow only cleans up
  branches pushed to `apache/camel`; branches on a fork must be deleted manually.

### JIRA Ticket Ownership

- An agent MUST ONLY pick up **Unassigned** JIRA tickets, and must not reassign or work on a ticket assigned to a human.
- Before starting work, assign the ticket to the operator and transition it to "In Progress".
  CAMEL transition IDs: `4` Start Progress, `5` Resolve Issue, `2` Close Issue. Query the issue's `/transitions`
  first: availability depends on the state and the caller (`4` is only offered once the issue is assigned to you).
- Set the `fixVersions` field before closing a ticket (it cannot be set on a closed issue).

### Pull Requests

- When pushing new commits, **always update the PR description** (and title if needed) so it describes the current
  changeset: `gh pr edit --title "..." --body "..."`.
- When a PR is **ready for review** (not draft), request review from **at least 2 relevant committers**
  (`gh pr edit --add-reviewer`). Find them with
  `git log --format='%an' --since='1 year' -- <affected-files> | sort | uniq -c | sort -rn | head -10` and `git blame`,
  and check the [committer list](https://home.apache.org/committers-by-project.html#camel). Prefer committers active on
  the component; for core/API changes include committers with broader project knowledge.
- Once all comments are addressed and checks are green, re-request review from the existing reviewers.
- **Backport PRs:** a straight cherry-pick of a PR already reviewed and merged on `main` onto an LTS maintenance branch
  (e.g. `camel-4.18.x`) gets **no reviewers** and MAY be merged once CI is green without a new human approval.
  Verify it is a straight cherry-pick by diffing the two PRs (`gh pr diff <backport-pr>` vs `gh pr diff <original-pr>`);
  any semantic difference, or a conflict resolution beyond a mechanical port, makes it a normal PR that needs review.

### Doing a review

- Wait until PR checks are green as they will already catch most trivial issues using less resources.
- Challenge the code and ensure that it respects all conventions. Check `git log` / `git blame` and the design docs (see
  "Issue Investigation") so a "fix" does not revert a deliberate decision without justification.
- For Dependabot PRs, either do not review them or do a real review: check for deprecated APIs, removed features, or
  breaking changes in the changelog.

### Merging

- An agent MUST NOT merge a PR with **unresolved review conversations**, or without at least **one human approval**
  (exception: straight-cherry-pick backport PRs, see above).
- An agent MUST NOT approve its own PRs.
- Follow the merge procedure in [`.oss-ai-helper-rules/project-guidelines.md`](.oss-ai-helper-rules/project-guidelines.md)
  (milestone, assignee, label, squash-merge, resolve JIRA, delete branch).

### Code Quality

- Every PR must include tests for new functionality or bug fixes.
- Every PR must include documentation updates where applicable; new features are documented in the relevant command or
  component page.
- Changes that affect users upgrading go in the upgrade guide
  (`docs/user-manual/modules/ROOT/pages/camel-4x-upgrade-guide-4_XX.adoc`): changed defaults, new auto-detection,
  removed or renamed options, changed header names or values, API/SPI signature changes, removed or deprecated
  components, migrated libraries, renamed documentation pages. It is for migration only, not new features. For
  backported changes, add the entry on `main`.
- All code must pass formatting checks (`mvn formatter:format impsort:sort`) before pushing.
- All generated files must be regenerated and committed (CI checks for uncommitted changes).
- Avoid introducing static analysis issues (code smells, maintainability regressions, CWE/OWASP weaknesses) and the
  use of deprecated code.

### Test Code

- Prefer AssertJ (`assertThat(...)`) over JUnit assertions, but match the module: if its tests predominantly use JUnit
  assertions, write new tests in that style. Never mix both styles in one test method. When touching JUnit assertions in
  a module that is not predominantly JUnit, migrate them where it helps readability.
- Never add `Thread.sleep()`; when touching a test that has one, replace it. For mock expectations use
  `MockEndpoint.assertIsSatisfied(context)` (it already waits 10 seconds) or the timed overload
  `assertIsSatisfied(context, 20, TimeUnit.SECONDS)`. Never wrap a `MockEndpoint` assertion in Awaitility.
- For any other condition use Awaitility with an explicit `atMost`, e.g.
  `await().atMost(10, TimeUnit.SECONDS).until(() -> mock.getReceivedCounter() >= 2)`.
- `MockEndpoint` assertions are Camel assertions, not JUnit ones — keep using them.

### Issue Investigation (Before Implementation)

Camel is a large, long-lived project — code often looks "wrong" but exists for good reasons. Before implementing a fix:

1. **Validate the issue**: confirm the problem is real and reproducible; question the issue's assumptions.
2. **Check git history**: `git log --oneline <file>` and `git blame <file>`, and read the linked JIRA tickets.
3. **Search JIRA** for related tickets, prior discussions and rejected approaches.
4. **Read the design docs** in `design/` for the area:
   [`security.adoc`](design/security.adoc) (secrets, SSL/TLS, serialization, policy enforcement),
   [`tracing.adoc`](design/tracing.adoc) (OpenTelemetry, spans, context propagation),
   [`mdc.adoc`](design/mdc.adoc) (MDC, logging context),
   [`headers.adoc`](design/headers.adoc) (header naming, constants, upgrade policy),
   [`cross-cutting-binding.adoc`](design/cross-cutting-binding.adoc) (onException, interceptors, onCompletion, route
   configurations), [`chaos.adoc`](design/chaos.adoc) (proposed `camel-chaos`),
   [`java-dsl-parser.adoc`](design/java-dsl-parser.adoc) (`LwJavaParser`, reading Java routes without compiling them).
5. **Understand the broader context**: if a module replaced another (e.g. `camel-opentelemetry2`), understand what was
   intentionally changed vs. accidentally omitted.
6. **Check if the fix reverts prior work**: if so, stop and reconsider; if still justified, say so in the PR description.

**Present your findings** to the operator before implementing, flagging risks, ambiguities, or conflicts with prior
decisions.

Training data has a cutoff: verify the state of external projects (versions, APIs, deprecations) against official
sources before relying on it or calling something wrong.

### Documentation Conventions

- Use `xref:` for internal links, never `https://camel.apache.org/...` URLs
  (e.g. `xref:manual::camel-jbang.adoc[Camel CLI]`).
- `components::` xrefs resolve to the latest **released** version: only link to a section anchor that exists there.
  Check this when reviewing doc PRs too.
- Do not add an explicit `[[anchor]]` before a heading: it replaces the auto-generated `_`-prefixed id that other xrefs
  expect and breaks the website link checker. Only add one when an id must survive a heading rename.
- Editing a component's `src/main/docs/*.adoc` requires regenerating and committing the copy under
  `catalog/camel-catalog/src/generated/resources/org/apache/camel/catalog/docs/`, even for small edits.

## Security Model

The threat model is [`docs/user-manual/modules/ROOT/pages/security-model.adoc`](docs/user-manual/modules/ROOT/pages/security-model.adoc).
Use it when triaging security reports, deciding whether a finding warrants a CVE, or reviewing a security-sensitive PR.

An agent that discovers or is handed a suspected vulnerability MUST NOT open a public issue, PR, or mailing-list post
about it — follow the private process in [`SECURITY.md`](SECURITY.md) and stop.

- **Route authors** and **deployment operators** are **fully trusted**: code execution by a route author (`.bean()`,
  `.process()`, `simple`/`groovy` expressions) and operator misconfiguration are not vulnerabilities, unless a Camel
  default exposed it.
- **External message senders** (HTTP clients, JMS producers, file droppers, etc.) are **untrusted**. The framework must
  not turn data flowing through a route into code execution, file read, request forgery, or auth bypass on its own.
- What is in and out of scope is in the "Vulnerability scope" section. Behaviour enabled by an explicit opt-in
  (`allowJavaSerializedObject=true`, `trustAllCertificates=true`, ...), DoS through unthrottled routes, and scanner
  reports without a PoC are out of scope.
- When reviewing a PR that touches a consumer, type converter, aggregation repository, data format, parser, or a
  security-relevant `@UriParam`, go through "Guidance for component authors and reviewers" in the security model.
  For deployment advice, see its "Deployment hardening" section.
- A new public method on the route builder (`RouteBuilder`, `BuilderSupport`, `RouteConfigurationBuilder`) or the static
  DSL (`Builder`, `PredicateBuilder`, `AggregationStrategies`) fails `LwJavaParserSecurityTest` on purpose. Check that
  the method only builds the model (no `CamelContext`, files, class loading or network) before regenerating
  `replay-builder-methods.txt` with `-Dcamel.java.in.updateSurface=true`. See
  [`design/java-dsl-parser.adoc`](design/java-dsl-parser.adoc).

## Build and Test

Build commands and code style rules are in
[`.oss-ai-helper-rules/project-standards.md`](.oss-ai-helper-rules/project-standards.md).

```bash
mvn clean install -Dquickly          # fast build, no tests
mvn clean install -pl components/camel-kafka -am  # single module
mvn formatter:format && mvn impsort:sort          # format code
mvn clean install -Psourcecheck      # verify style
mvn verify -Pit                      # integration tests
```

Tests needing external services use the Testcontainers-based services in `test-infra/`:

```java
@RegisterExtension
static KafkaService service = KafkaServiceFactory.createService();
```

Deterministic simulation tests (`camel-test-dst`, preview): for timing bugs of a consumer (checkpoint, commit,
acknowledge, visibility, reconnect, suspend), the real consumer runs under a seeded virtual-time scheduler against a
fake of the external system. Before writing such a "world" for a component, follow the guide "Writing a fake world" in
[`components/camel-test/camel-test-dst/src/main/docs/test-dst.adoc`](components/camel-test/camel-test-dst/src/main/docs/test-dst.adoc).
The SQS world in `components/camel-aws/camel-aws2-sqs/src/test/java/org/apache/camel/component/aws2/sqs/dst/` is the
worked example. Do not report a failing seed as a bug before the triage step of that guide.

New integration tests with a container image: avoid Docker Hub images, prefer `mirror.gcr.io` or `quay.io`. The image
must be available for `amd64`, `ppc64le` and `s390x` (sometimes from another registry such as `icr.io`); otherwise
disable the tests with the `skipITs.ppc64le` and `skipITs.s390x` Maven properties.

## Conventions

Deprecation:
- Add `(deprecated)` suffix to `<name>` in `pom.xml`: `<name>Camel :: MyComponent (deprecated)</name>`
- Add `(deprecated)` suffix to the doc page title in `src/main/docs/*.adoc`
- Add `@Deprecated` to Java classes
- Document in the upgrade guide

Annotations:
- `@UriPath` for path params, `@UriParam` for query params; always add a `description` for the docs.
- Mark sensitive parameters with `secret = true` on `@UriParam` or `@Metadata` (passwords, tokens, API keys).
- Mark insecure configuration flags (e.g., `trustAllCertificates`, `allowJavaSerializedObject`) with
  `security = "insecure:ssl"` / `"insecure:serialization"` / `"insecure:dev"` on `@UriParam`.
  See [`design/security.adoc`](design/security.adoc).
- A new `@UriParam` that controls a security-relevant default errs toward "denied unless opted in". Relaxing a default
  needs an upgrade-guide entry and PMC sign-off.

Imports:
- Do NOT use fully qualified class names in Java code (production, test and test-infra); import and use the simple
  name. Exception: two classes with the same simple name — import the most-used one and qualify the other.
- Generated code (`src/generated/`) is excluded. The build shortens FQCNs via OpenRewrite, and CI fails on the
  resulting uncommitted changes.

Javadoc `@since` tags:
- New public classes, interfaces, enums and annotations in `core/camel-api`, and new public methods added to existing
  ones, MUST have a `@since X.Y` tag with the upcoming minor version (e.g. `@since 4.24` for 4.24.0-SNAPSHOT), placed
  as the last Javadoc tag.

Commits: `CAMEL-XXXX: Brief description`, referencing JIRA when applicable.

## Adding Components

A component that is a direct child of `components/` needs no extra config. A component inside a parent folder
(`camel-aws`, `camel-ai`, `camel-azure`, ...) must be added to that parent's `case` in `getComponentPath()` of
`tooling/maven/camel-package-maven-plugin/src/main/java/org/apache/camel/maven/packaging/MojoHelper.java`, or the build
won't pick it up for catalog/docs generation:

```java
case "camel-aws":
    return Arrays.asList(dir.resolve("camel-aws2-athena"),
            // ...
            dir.resolve("camel-your-new-component"));
```
