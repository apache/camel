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
2. **POM dependency analysis**: For `parent/pom.xml` changes, detects property changes and finds modules that reference the affected properties in their `pom.xml` files (uses simple grep, not Maveniverse Toolbox — see Known Limitations below)
3. **Extra modules**: Additional modules passed via `/component-test`

Results are merged, deduplicated, and tested. The script also:

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

## Known Limitations of POM Dependency Detection

The property-grep approach has structural limitations that can cause missed modules:

1. **Managed dependencies without explicit** `<version>` — Most Camel modules inherit dependency versions via `<dependencyManagement>` in the parent POM and do not declare `<version>${property}</version>` themselves. When a managed dependency version property changes, only modules that explicitly reference the property are detected — modules relying on inheritance are missed.

2. **Maven plugin version changes are completely invisible** — Plugin version properties (e.g. `<maven-surefire-plugin-version>`) are both defined and consumed in `parent/pom.xml` via `<pluginManagement>`. Since the module search excludes `parent/pom.xml`, no modules are found and **no tests run at all** for plugin updates. Modules inherit plugins from the parent without any `${property}` reference in their own `pom.xml`.

3. **BOM imports** — When a BOM version property changes (e.g. `<spring-boot-bom-version>`), modules using artifacts from that BOM are not detected because they reference the BOM's artifacts, not the BOM property.

4. **Transitive dependency changes** — Modules affected only via transitive dependencies are not detected.

5. **Non-property version changes** — Direct edits to `<version>` values (not using `${property}` substitution) or structural changes to `<dependencyManagement>` sections are not caught.

These limitations mean the incremental build may under-test when parent POM properties change. A future improvement could use [Maveniverse Toolbox](https://github.com/maveniverse/toolbox) `tree-find` or [Scalpel](https://github.com/maveniverse/scalpel) to resolve the full dependency graph and detect all affected modules.

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