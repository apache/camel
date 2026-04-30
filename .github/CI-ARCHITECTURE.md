# CI Architecture

Overview of the GitHub Actions CI/CD ecosystem for Apache Camel.

## Workflow Overview

```
PR opened/updated
       │
       ├──► pr-id.yml ──► pr-commenter.yml (welcome message)
       │
       ├──► pr-build-main.yml (Build and test)
       │        │
       │        ├── regen.sh (full build, no tests)
       │        ├── incremental-build (test affected modules)
       │        │       ├── File-path analysis
       │        │       ├── POM dependency analysis
       │        │       └── Extra modules (/component-test)
       │        │
       │        └──► pr-test-commenter.yml (post unified comment)
       │
       └──► sonar-build.yml ──► sonar-scan.yml (SonarCloud analysis)
                                    [currently disabled — INFRA-27808]

PR comment: /component-test kafka http
       │
       └──► pr-manual-component-test.yml
                │
                └── dispatches "Build and test" with extra_modules
```

## Workflows

### `pr-build-main.yml` — Build and test

- **Trigger**: `pull_request` (main branch), `workflow_dispatch`
- **Matrix**: JDK 17, 21, 25 (25 is experimental)
- **Steps**:
  1. Full build via `regen.sh` (`mvn install -DskipTests -Pregen`)
  2. Check for uncommitted generated files
  3. Run incremental tests (only affected modules)
  4. Upload test comment as artifact
- **Inputs** (workflow_dispatch): `pr_number`, `pr_ref`, `extra_modules`, `skip_full_build`

### `pr-test-commenter.yml` — Post CI test comment

- **Trigger**: `workflow_run` on "Build and test" completion
- **Purpose**: Posts the unified test summary comment on the PR
- **Why separate**: Uses `workflow_run` to run in base repo context, allowing comment posting on fork PRs (where `GITHUB_TOKEN` is read-only)

### `pr-manual-component-test.yml` — /component-test handler

- **Trigger**: `issue_comment` with `/component-test` prefix
- **Who**: MEMBER, OWNER, or CONTRIBUTOR only
- **What**: Resolves component names to module paths, dispatches the main "Build and test" workflow with `extra_modules` and `skip_full_build=true`
- **Build**: Uses a quick targeted build (`-Dquickly`) of the requested modules and their dependencies instead of the full `regen.sh` build

### `pr-id.yml` + `pr-commenter.yml` — Welcome message

- **Trigger**: `pull_request` (all branches)
- **Purpose**: Posts the one-time welcome message on new PRs
- **Why two workflows**: `pr-id.yml` runs in PR context (uploads PR number), `pr-commenter.yml` runs via `workflow_run` with write permissions

### `main-build.yml` — Main branch build

- **Trigger**: `push` to main, camel-4.14.x, camel-4.18.x
- **Steps**: Same as PR build but without comment posting

### `sonar-build.yml` + `sonar-scan.yml` — SonarCloud PR analysis

- **Status**: Temporarily disabled (INFRA-27808 — SonarCloud quality gate adjustment pending)
- **Trigger**: `pull_request` (main branch) → `workflow_run` on SonarBuild completion
- **Why two workflows**: `sonar-build.yml` runs in PR context (builds with JaCoCo coverage on core modules, uploads compiled classes artifact), `sonar-scan.yml` runs via `workflow_run` with secrets access to run the Sonar scanner and post results
- **Coverage scope**: Currently limited to core modules (`camel-api`, `camel-core`, etc.) and `coverage` aggregator. Component coverage planned for future integration with `incremental-build.sh` module detection

### Other workflows

- `pr-labeler.yml` — Auto-labels PRs based on changed files
- `pr-doc-validation.yml` — Validates documentation changes
- `pr-cleanup-branches.yml` — Cleans up merged PR branches
- `alternative-os-build-main.yml` — Tests on non-Linux OSes
- `check-container-versions.yml` — Checks test container version updates
- `generate-sbom-main.yml` — Generates SBOM for releases
- `security-scan.yml` — Security vulnerability scanning

## Actions

### `incremental-build`

The core test runner. Determines which modules to test using:

1. **File-path analysis**: Maps changed files to Maven modules
2. **POM dependency analysis** (dual detection):
   - **Grep-based**: For `parent/pom.xml` changes, detects property changes and finds modules that explicitly reference the affected properties via `${property}` in their `pom.xml` files
   - **Scalpel-based**: Uses [Maveniverse Scalpel](https://github.com/maveniverse/scalpel) (Maven extension) for effective POM model comparison — catches managed dependencies, plugin version changes, BOM imports, and transitive dependency impacts that the grep approach misses
3. **Extra modules**: Additional modules passed via `/component-test`

Both detection methods run in parallel. Their results are merged (union), deduplicated, and tested. If Scalpel fails (build error, runtime error), the script falls back to grep-only with no regression.

The script also:

- Detects tests disabled in CI (`@DisabledIfSystemProperty(named = "ci.env.name")`)
- Applies an exclusion list for generated/meta modules
- Checks for excluded modules with associated integration tests (via `manual-it-mapping.txt`) and advises contributors to run them manually
- Generates a unified PR comment with all test information

### `install-mvnd`

Installs the Maven Daemon (mvnd) for faster builds.

### `install-packages`

Installs system packages required for the build.

## PR Labels

| Label | Effect |
| --- | --- |
| `skip-tests` | Skip all tests |
| `test-dependents` | Force testing dependent modules even if threshold exceeded |

## CI Environment

The CI sets `-Dci.env.name=github.com` via `MVND_OPTS` (in `install-mvnd`). Tests can use `@DisabledIfSystemProperty(named = "ci.env.name")` to skip flaky tests in CI. The test comment warns about these skipped tests.

## POM Dependency Detection: Dual Approach

### Grep-based detection (legacy)

The grep approach searches for `${property-name}` references in module `pom.xml` files. It has known limitations:

1. **Managed dependencies without explicit `<version>`** — Modules inheriting versions via `<dependencyManagement>` without declaring `<version>${property}</version>` are missed.
2. **Maven plugin version changes** — Plugin version properties consumed in `parent/pom.xml` via `<pluginManagement>` are invisible to child modules.
3. **BOM imports** — Modules using artifacts from a BOM are not linked to the BOM version property.
4. **Transitive dependency changes** — Only direct property references are detected.
5. **Non-property version changes** — Structural `<dependencyManagement>` edits without property substitution are not caught.

### Scalpel-based detection (new)

[Maveniverse Scalpel](https://github.com/maveniverse/scalpel) is a Maven core extension that compares effective POM models between the base branch and the PR. It resolves all 5 grep limitations by:

- Reading old POM files from the merge-base commit (via JGit)
- Comparing properties, managed dependencies, and managed plugins between old and new POMs
- Resolving the full transitive dependency graph to find all affected modules
- Detecting plugin version changes via `project.getBuildPlugins()` comparison

Scalpel runs in **report mode** (`-Dscalpel.mode=report`), writing a JSON report to `target/scalpel-report.json` without modifying the Maven reactor. The report includes affected modules with reasons (`SOURCE_CHANGE`, `POM_CHANGE`, `TRANSITIVE_DEPENDENCY`, `MANAGED_PLUGIN`).

### Dual-detection strategy

Both methods run in parallel. Results are merged (union) before testing. This lets us:

1. **Validate Scalpel** — Compare what each method detects across many PRs
2. **No regression** — If Scalpel fails, grep results are still used
3. **Gradual migration** — Once Scalpel is validated, grep can be removed

Scalpel is configured permanently in `.mvn/extensions.xml` (version `0.1.0`). On developer machines it is a no-op — without CI environment variables (`GITHUB_BASE_REF`), no base branch is detected and Scalpel returns immediately. The `mvn validate` with report mode adds ~60-90 seconds in CI.

Note: the script overrides `fullBuildTriggers` to empty (`-Dscalpel.fullBuildTriggers=`) because Scalpel's default (`.mvn/**`) would trigger a full build whenever `.mvn/extensions.xml` itself changes (e.g., Dependabot bumping Scalpel).

## Manual Integration Test Advisories

Some modules are excluded from CI's `-amd` expansion (the `EXCLUSION_LIST`) because they are generated code, meta-modules, or expensive integration test suites. When a contributor changes one of these modules, CI cannot automatically test all downstream effects.

The file `manual-it-mapping.txt` (co-located with the incremental build script) maps source modules to their associated integration test suites. When a changed module has a mapping entry, CI posts an advisory in the PR comment:

> You modified `dsl/camel-jbang/camel-jbang-core`. The related integration tests in `dsl/camel-jbang/camel-jbang-it` are excluded from CI. Consider running them manually:
>
> ```
> mvn verify -f dsl/camel-jbang/camel-jbang-it -Djbang-it-test
> ```

To add new mappings, edit `manual-it-mapping.txt` using the format:

```
source-artifact-id:it-module-path:command
```

## Multi-JDK Artifact Behavior

All non-experimental JDK matrix entries (17, 21) upload the CI comment artifact with `overwrite: true`. This ensures a comment is posted even if one JDK build fails. Since the comment content is identical across JDKs (same modules are tested regardless of JDK version), last writer wins.

## Comment Markers

PR comments use HTML markers for upsert (create-or-update) behavior:

- `<!-- ci-tested-modules -->` — Unified test summary comment