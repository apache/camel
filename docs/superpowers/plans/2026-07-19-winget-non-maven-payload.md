# WinGet Non-Maven Payload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the WinGet launcher ZIP reproducibly from `tooling/camel-exe` without attaching or publishing that ZIP as a Maven artifact, then vote on and publish the exact ZIP through the Apache distribution archive.

**Architecture:** Keep the current `maven-dependency-plugin:copy` and Maven Assembly Plugin flow, but set the WinGet assembly execution to `attach=false`. Add CI gates for byte reproducibility and Maven repository exclusion, stage the local ZIP with its Apache signature and SHA-512 checksum under `dist/dev`, promote the approved files unchanged to `dist/release`, and make JReleaser verify the archived bytes before generating WinGet packaging output.

**Tech Stack:** Maven 3.9.12+, Maven Assembly Plugin 3.8.0, Maven Dependency Plugin, JReleaser 1.25.0, Bash, JUnit 5, llvm-mingw 20260616, GitHub Actions, Apache Subversion distribution repositories.

## Global Constraints

- Do not create a custom Windows installer.
- Do not add dependencies.
- Keep `camel-x64.exe` and `camel-arm64.exe` out of the standard `-bin.zip` and `-bin.tar.gz` archives.
- Build both native executables from `tooling/camel-exe` with llvm-mingw version `20260616` and its pinned SHA-256.
- Build `camel-launcher-<version>-winget-bin.zip` only with Maven Assembly Plugin 3.8.0 and `project.build.outputTimestamp`.
- Do not attach, install, or deploy the WinGet ZIP through Maven. Maven must not create a WinGet ZIP `.sha1` sidecar.
- The Apache distribution staging step, not Maven, creates the WinGet ZIP `.asc` and `.sha512` files.
- Promote the approved `dist/dev` ZIP, signature, and checksum without rebuilding or repacking them.
- Point WinGet manifests at `https://archive.apache.org/dist/camel/apache-camel/<version>/camel-launcher-<version>-winget-bin.zip`.
- Treat missing artifacts, checksum mismatches, signature failures, unavailable archive URLs, and reproducibility differences as hard failures.
- Keep Scoop's existing Maven Central `.sha1` behavior unchanged because it applies to the standard launcher archive.
- Use American English and do not introduce em dashes in code comments or documentation.
- Before every `git add`, inspect the exact diff and scan it for secret-shaped content.
- Every implementation commit uses a `CAMEL-23703:` subject and the trailer `Co-authored-by: Codex <noreply@openai.com>`.

---

## File Map

- `dsl/camel-jbang/camel-launcher/pom.xml`: builds the local, non-attached WinGet ZIP.
- `dsl/camel-jbang/camel-launcher/src/main/assembly/winget-bin.xml`: remains the single definition of WinGet ZIP contents.
- `dsl/camel-jbang/camel-launcher/jreleaser.yml`: points only the WinGet distribution at the Apache archive.
- `dsl/camel-jbang/camel-launcher/src/jreleaser/bin/camel-package.sh`: verifies that the local WinGet ZIP exactly matches the archived ZIP before JReleaser runs.
- `dsl/camel-jbang/camel-launcher/src/test/java/org/apache/camel/dsl/jbang/launcher/PackagePlanTest.java`: verifies the Maven, workflow, JReleaser, and package-script contracts.
- `dsl/camel-jbang/camel-launcher/src/test/java/org/apache/camel/dsl/jbang/launcher/WingetDistroScriptsTest.java`: exercises candidate staging outputs and verifies promotion-script requirements.
- `.github/workflows/camel-launcher-native-exe.yml`: builds twice, compares hashes, deploys to a temporary Maven repository, and rejects a deployed WinGet artifact.
- `etc/scripts/stage-winget-distro.sh`: signs, checksums, and prepares the local `dist/dev` candidate working copy without committing it.
- `etc/scripts/release-distro.sh`: imports the approved candidate files into the existing `dist/release` working copy and verifies them first.
- `dsl/camel-jbang/camel-launcher/README.md`: documents the local-only Maven artifact and Apache archive URL.
- `tooling/camel-exe/README.md`: documents the reproducibility gate.
- `docs/user-manual/modules/ROOT/pages/release-guide.adoc`: documents pre-vote staging, vote evidence, post-vote promotion, and archive synchronization.

### Task 1: Make the Maven Assembly Local-Only

**Files:**
- Modify: `dsl/camel-jbang/camel-launcher/src/test/java/org/apache/camel/dsl/jbang/launcher/PackagePlanTest.java:137-161`
- Modify: `dsl/camel-jbang/camel-launcher/pom.xml:413-424`
- Modify: `dsl/camel-jbang/camel-launcher/README.md:11-30`

**Interfaces:**
- Consumes: the existing `include-camel-exe` profile, `camel-exe` artifacts with type `exe` and classifiers `x64` and `arm64`, and `src/main/assembly/winget-bin.xml`.
- Produces: `target/camel-launcher-${project.version}-winget-bin.zip` during `package`, while leaving `project.getAttachedArtifacts()` unchanged.

- [ ] **Step 1: Extend the existing packaging contract test**

Replace the final two assertions in `nativeExecutablesAreConfinedToWingetArchive()` with an execution-scoped check so another assembly execution cannot satisfy the test accidentally:

```java
        String nativeProfilePom = pom.substring(nativeProfile);
        int wingetExecutionStart = nativeProfilePom.indexOf("<id>assemble-winget-bin</id>");
        int wingetExecutionEnd = nativeProfilePom.indexOf("</execution>", wingetExecutionStart);
        String wingetExecution = nativeProfilePom.substring(wingetExecutionStart, wingetExecutionEnd);

        assertTrue(wingetExecutionStart >= 0, "the native executable profile must create the WinGet archive");
        assertTrue(wingetExecution.contains("<descriptor>src/main/assembly/winget-bin.xml</descriptor>"),
                wingetExecution);
        assertTrue(wingetExecution.contains("<attach>false</attach>"),
                "the WinGet payload must remain a local release file, not an attached Maven artifact");
```

- [ ] **Step 2: Run the focused test and confirm the intended failure**

Run:

```bash
cd dsl/camel-jbang/camel-launcher
mvn -Dtest=PackagePlanTest#nativeExecutablesAreConfinedToWingetArchive test
```

Expected: FAIL because the `assemble-winget-bin` execution does not contain `<attach>false</attach>`.

- [ ] **Step 3: Disable attachment only for the WinGet assembly**

Add `attach` beside the existing descriptor configuration in `pom.xml`:

```xml
                                <configuration>
                                    <attach>false</attach>
                                    <descriptors combine.self="override">
                                        <descriptor>src/main/assembly/winget-bin.xml</descriptor>
                                    </descriptors>
                                </configuration>
```

Do not change the standard launcher assemblies or the attached `camel-exe` classifier artifacts. The launcher still consumes those classifier artifacts through `maven-dependency-plugin:copy`.

- [ ] **Step 4: Document the Maven lifecycle result**

Replace the WinGet build paragraph in `dsl/camel-jbang/camel-launcher/README.md` with:

```markdown
When `-Dcamel.exe.build=true` is enabled, Maven also creates a WinGet-only archive,
`camel-launcher-<version>-winget-bin.zip`, containing the native Windows bootstraps built by
[`tooling/camel-exe`](../../../tooling/camel-exe): `bin/camel-x64.exe` and `bin/camel-arm64.exe`.
The assembly uses `attach=false`, so the ZIP remains in this module's `target` directory for the
Apache distribution release flow and is not installed or deployed to a Maven repository.
Release builds use llvm-mingw to cross-compile both executables on Linux. The launcher module verifies during `verify`
that both files are staged, absent from the public archives, and present in the WinGet ZIP:
```

- [ ] **Step 5: Run the focused test and package check**

Run:

```bash
cd dsl/camel-jbang/camel-launcher
mvn -Dtest=PackagePlanTest#nativeExecutablesAreConfinedToWingetArchive test
```

Expected: PASS with one test run and no failures.

The native build itself is covered in Task 2 because it requires the pinned llvm-mingw toolchain.

- [ ] **Step 6: Review, scan, and commit**

Run:

```bash
git diff -- dsl/camel-jbang/camel-launcher/pom.xml dsl/camel-jbang/camel-launcher/README.md dsl/camel-jbang/camel-launcher/src/test/java/org/apache/camel/dsl/jbang/launcher/PackagePlanTest.java
git diff --check
git diff | rg -n '(-----BEGIN [A-Z ]*PRIVATE KEY-----|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{36,}|sk-[A-Za-z0-9]{20,})'
```

Expected: the first command shows only this task's changes, `git diff --check` is silent, and the secret scan is silent. Then run:

```bash
git add dsl/camel-jbang/camel-launcher/pom.xml \
  dsl/camel-jbang/camel-launcher/README.md \
  dsl/camel-jbang/camel-launcher/src/test/java/org/apache/camel/dsl/jbang/launcher/PackagePlanTest.java
git commit -m "CAMEL-23703: Keep WinGet payload out of Maven repositories" \
  -m "Co-authored-by: Codex <noreply@openai.com>"
```

### Task 2: Prove Reproducibility and Maven Repository Exclusion

**Files:**
- Modify: `dsl/camel-jbang/camel-launcher/src/test/java/org/apache/camel/dsl/jbang/launcher/PackagePlanTest.java:163-172`
- Modify: `.github/workflows/camel-launcher-native-exe.yml:188-202`
- Modify: `tooling/camel-exe/README.md:37-48`

**Interfaces:**
- Consumes: the local-only ZIP from Task 1, the pinned llvm-mingw setup action, and the existing three-module native launcher build.
- Produces: a CI gate that compares SHA-256 values across two clean builds and rejects any WinGet ZIP or WinGet ZIP `.sha1` in a temporary Maven deployment repository.

- [ ] **Step 1: Add a workflow contract test**

Add this method to `PackagePlanTest`:

```java
    @Test
    void nativeWorkflowChecksReproducibilityAndMavenExclusion() throws Exception {
        String workflow = Files.readString(
                MODULE_DIR.resolve("../../../.github/workflows/camel-launcher-native-exe.yml").normalize(),
                StandardCharsets.UTF_8);

        assertTrue(workflow.contains("-name 'camel-launcher-*-winget-bin.zip'"), workflow);
        assertTrue(workflow.contains("sha256sum --check \"$HASH_FILE\""), workflow);
        assertTrue(workflow.contains("-DaltSnapshotDeploymentRepository=winget-check::file://$MAVEN_REPO"), workflow);
        assertTrue(workflow.contains("-name '*-winget-bin.zip'"), workflow);
        assertTrue(workflow.contains("-name '*-winget-bin.zip.sha1'"), workflow);
    }
```

- [ ] **Step 2: Run the new test and confirm it fails**

Run:

```bash
cd dsl/camel-jbang/camel-launcher
mvn -Dtest=PackagePlanTest#nativeWorkflowChecksReproducibilityAndMavenExclusion test
```

Expected: FAIL because the workflow has no second-build hash comparison or temporary deployment check, and its current ZIP glob is not WinGet-specific.

- [ ] **Step 3: Make the archive assertion unambiguous**

Change the ZIP lookup in `.github/workflows/camel-launcher-native-exe.yml` to:

```yaml
          ZIP=$(find dsl/camel-jbang/camel-launcher/target -maxdepth 1 -name 'camel-launcher-*-winget-bin.zip' -print -quit)
```

- [ ] **Step 4: Add the clean rebuild checksum gate**

Insert this step after the archive-content assertion:

```yaml
      - name: Verify reproducible native executables and WinGet archive
        run: |
          HASH_FILE="$RUNNER_TEMP/winget-first-build.sha256"
          sha256sum \
            tooling/camel-exe/target/camel-x64.exe \
            tooling/camel-exe/target/camel-arm64.exe \
            dsl/camel-jbang/camel-launcher/target/camel-launcher-*-winget-bin.zip \
            > "$HASH_FILE"

          mvn -P apache-snapshots \
            -pl buildingtools,tooling/camel-exe,dsl/camel-jbang/camel-launcher clean verify \
            -Dcamel.exe.build=true -DskipTests

          sha256sum --check "$HASH_FILE"
```

The stored paths are repository-relative, so the second clean build recreates files at the exact paths recorded by `sha256sum`.

- [ ] **Step 5: Add the temporary Maven deployment gate**

Insert this step after the reproducibility step:

```yaml
      - name: Verify WinGet archive is not deployed by Maven
        run: |
          MAVEN_REPO="$RUNNER_TEMP/winget-maven-repo"
          mvn -P apache-snapshots \
            -pl buildingtools,tooling/camel-exe,dsl/camel-jbang/camel-launcher deploy \
            -Dcamel.exe.build=true -DskipTests \
            -DaltSnapshotDeploymentRepository=winget-check::file://$MAVEN_REPO

          DEPLOYED_WINGET_FILES=$(find "$MAVEN_REPO" -type f \
            \( -name '*-winget-bin.zip' -o -name '*-winget-bin.zip.sha1' \) -print)
          if [ -n "$DEPLOYED_WINGET_FILES" ]; then
            echo "ERROR: Maven deployed the local-only WinGet payload:"
            echo "$DEPLOYED_WINGET_FILES"
            exit 1
          fi
```

Do not accept the absence of only `.sha512` as proof. This gate must specifically reject the ZIP and the Maven repository's `.sha1` sidecar.

- [ ] **Step 6: Document the reproducibility gate**

Append this paragraph after the release-build command in `tooling/camel-exe/README.md`:

```markdown
The native launcher workflow runs this build twice from clean output directories and compares the
SHA-256 values of `camel-x64.exe`, `camel-arm64.exe`, and the WinGet ZIP. It also deploys the reactor
to a temporary file repository and fails if Maven publishes the WinGet ZIP or a corresponding
`.sha1` sidecar. A checksum difference is a release failure and must be investigated before changing
compiler or linker flags.
```

- [ ] **Step 7: Run the focused contract test and local syntax checks**

Run:

```bash
cd dsl/camel-jbang/camel-launcher
mvn -Dtest=PackagePlanTest#nativeWorkflowChecksReproducibilityAndMavenExclusion test
cd ../../..
git diff --check
```

Expected: the JUnit test passes and `git diff --check` is silent. Do not report the native binaries as reproducible until the GitHub Actions job, or the equivalent pinned-toolchain command, completes both builds successfully.

- [ ] **Step 8: Review, scan, and commit**

Run:

```bash
git diff -- .github/workflows/camel-launcher-native-exe.yml tooling/camel-exe/README.md dsl/camel-jbang/camel-launcher/src/test/java/org/apache/camel/dsl/jbang/launcher/PackagePlanTest.java
git diff | rg -n '(-----BEGIN [A-Z ]*PRIVATE KEY-----|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{36,}|sk-[A-Za-z0-9]{20,})'
```

Expected: only this task's changes appear and the secret scan is silent. Then run:

```bash
git add .github/workflows/camel-launcher-native-exe.yml \
  tooling/camel-exe/README.md \
  dsl/camel-jbang/camel-launcher/src/test/java/org/apache/camel/dsl/jbang/launcher/PackagePlanTest.java
git commit -m "CAMEL-23703: Verify reproducible WinGet packaging" \
  -m "Co-authored-by: Codex <noreply@openai.com>"
```

### Task 3: Use and Verify the Archived Apache Payload

**Files:**
- Modify: `dsl/camel-jbang/camel-launcher/jreleaser.yml:139-155`
- Modify: `dsl/camel-jbang/camel-launcher/src/jreleaser/bin/camel-package.sh:147-214`
- Modify: `dsl/camel-jbang/camel-launcher/src/test/java/org/apache/camel/dsl/jbang/launcher/PackagePlanTest.java:41-72,163-171,236-305,412-555`
- Modify: `dsl/camel-jbang/camel-launcher/README.md:107-141`

**Interfaces:**
- Consumes: local `target/camel-launcher-<version>-winget-bin.zip` and the immutable Apache archive URL for the same version and filename.
- Produces: JReleaser WinGet output only after `cmp` proves that the local ZIP and archived ZIP are byte-identical.

- [ ] **Step 1: Add the archive URL assertions**

Replace `wingetUsesDedicatedJreleaserDistribution()` with:

```java
    @Test
    void wingetUsesDedicatedJreleaserDistribution() throws Exception {
        String config = Files.readString(MODULE_DIR.resolve("jreleaser.yml"), StandardCharsets.UTF_8);
        int wingetStart = config.indexOf("  camel-cli-winget:");
        String wingetDistribution = config.substring(wingetStart);

        assertTrue(wingetStart >= 0, config);
        assertTrue(wingetDistribution.contains("camel-launcher-{{projectVersion}}-winget-bin.zip"), config);
        assertTrue(wingetDistribution.contains(
                "https://archive.apache.org/dist/camel/apache-camel/{{projectVersion}}/{{artifactFile}}"), config);
        assertFalse(wingetDistribution.contains("repo1.maven.org"), wingetDistribution);
        assertFalse(config.substring(config.indexOf("camel-cli:"), wingetStart).contains("winget:"),
                "the public distribution must not generate the WinGet package");
    }
```

- [ ] **Step 2: Add deterministic local and remote ZIP fixtures**

Extend `cleanupFixtures()` with:

```java
        Files.deleteIfExists(MODULE_DIR.resolve("target/camel-launcher-" + TEST_VERSION + "-winget-bin.zip"));
```

Add this helper below `writeReleaseFixture`:

```java
    Path writeWingetFixture(String content) throws IOException {
        return writeReleaseFixture("-winget-bin.zip", content);
    }
```

In both `testModeEnvWithMvnStub` and `envWithMvnStubProducingFormula`, create an identical remote source and add the guarded test-only variable:

```java
        Path winget = writeWingetFixture("fixture-winget");
        env.put("CAMEL_PACKAGE_TEST_WINGET_REMOTE", winget.toString());
```

Change `productionStyleEnvWithMvnStub` to return a mutable `LinkedHashMap`, create the local fixture, and install a successful `curl` stub:

```java
        Path winget = writeWingetFixture("fixture-winget");
        Path curlRecord = tmp.resolve("archive-curl.txt");
        Path curlStub = stubDir.resolve("curl");
        Files.writeString(curlStub,
                "#!/bin/sh\n"
                                   + "printf '%s\\n' \"$*\" >> \"" + curlRecord + "\"\n"
                                   + "output=''\n"
                                   + "previous=''\n"
                                   + "for argument in \"$@\"; do\n"
                                   + "  if [ \"$previous\" = '-o' ]; then output=$argument; fi\n"
                                   + "  previous=$argument\n"
                                   + "done\n"
                                   + "[ -n \"$output\" ] || exit 98\n"
                                   + "cp \"" + winget + "\" \"$output\"\n",
                StandardCharsets.UTF_8);
        assertTrue(curlStub.toFile().setExecutable(true));

        Map<String, String> env = new LinkedHashMap<>();
        env.put("PATH", stubDir + File.pathSeparator + System.getenv("PATH"));
        return env;
```

Keep the existing `mvn` stub in that method unchanged. Do not use a test variable in this production-style helper, because this path must exercise the real `curl` branch.

- [ ] **Step 3: Add byte-match, mismatch, and guard tests**

Add these methods to `PackagePlanTest`:

```java
    @Test
    void productionPrepareVerifiesTheArchivedWingetPayload(@TempDir Path tmp) throws Exception {
        writeReleaseFixture("-bin.tar.gz", "fixture-tar");
        writeReleaseFixture("-bin.zip", "fixture-zip");
        Path recordFile = tmp.resolve("mvn-calls.txt");

        Result r = run(productionStyleEnvWithMvnStub(tmp, recordFile), "prepare", "--channel", "stable");

        assertEquals(0, r.exit, r.stderr);
        String curlCalls = Files.readString(tmp.resolve("archive-curl.txt"), StandardCharsets.UTF_8);
        assertTrue(curlCalls.contains("https://archive.apache.org/dist/camel/apache-camel/" + TEST_VERSION
                + "/camel-launcher-" + TEST_VERSION + "-winget-bin.zip"), curlCalls);
        assertTrue(Files.exists(recordFile), "JReleaser must run after the byte comparison succeeds");
    }

    @Test
    void prepareRejectsAnArchivedWingetPayloadWithDifferentBytes(@TempDir Path tmp) throws Exception {
        writeReleaseFixture("-bin.tar.gz", "fixture-tar");
        writeReleaseFixture("-bin.zip", "fixture-zip");
        Path remote = tmp.resolve("remote-winget.zip");
        Files.writeString(remote, "different-remote-winget", StandardCharsets.UTF_8);
        Path recordFile = tmp.resolve("mvn-calls.txt");
        Map<String, String> env = testModeEnvWithMvnStub(tmp, recordFile);
        writeWingetFixture("local-winget");
        env.put("CAMEL_PACKAGE_TEST_WINGET_REMOTE", remote.toString());

        Result r = run(env, "prepare", "--channel", "stable");

        assertNotEquals(0, r.exit);
        assertTrue(r.stderr.contains("does not match the archived WinGet payload"), r.stderr);
        assertFalse(Files.exists(recordFile), "JReleaser must not run after a byte mismatch");
    }

    @Test
    void wingetRemoteOverrideRequiresExplicitTestMode(@TempDir Path tmp) throws Exception {
        writeReleaseFixture("-bin.tar.gz", "fixture-tar");
        writeReleaseFixture("-bin.zip", "fixture-zip");
        Path recordFile = tmp.resolve("mvn-calls.txt");
        Map<String, String> env = new LinkedHashMap<>(productionStyleEnvWithMvnStub(tmp, recordFile));
        env.put("CAMEL_PACKAGE_TEST_WINGET_REMOTE", writeWingetFixture("fixture-winget").toString());

        Result r = run(env, "prepare", "--channel", "stable");

        assertEquals(2, r.exit);
        assertTrue(r.stderr.contains("CAMEL_PACKAGE_TEST_WINGET_REMOTE requires CAMEL_PACKAGE_TEST_MODE=true"), r.stderr);
        assertFalse(Files.exists(recordFile));
    }

    @Test
    void missingWingetPayloadFailsBeforeJReleaser(@TempDir Path tmp) throws Exception {
        writeReleaseFixture("-bin.tar.gz", "fixture-tar");
        writeReleaseFixture("-bin.zip", "fixture-zip");
        Path recordFile = tmp.resolve("mvn-calls.txt");
        Map<String, String> env = testModeEnvWithMvnStub(tmp, recordFile);
        Files.delete(MODULE_DIR.resolve("target/camel-launcher-" + TEST_VERSION + "-winget-bin.zip"));

        Result r = run(env, "prepare", "--channel", "stable");

        assertNotEquals(0, r.exit);
        assertTrue(r.stderr.contains("WinGet release ZIP not found"), r.stderr);
        assertFalse(Files.exists(recordFile));
    }
```

Also add `writeWingetFixture("fixture-winget")` before invoking the script in any happy-path test whose environment helper does not create it. Preserve `scoopAutoupdateUsesPublishedMavenCentralSha1Sidecar()` unchanged.

Extend `wingetInstallerManifestUsesLatestAcceptedSchema()` so the template continues to bind both architectures and both installer checksums to this one distribution ZIP:

```java
        assertTrue(winget.contains("Architecture: x64"), winget);
        assertTrue(winget.contains("Architecture: arm64"), winget);
        assertTrue(winget.contains("bin\\camel-x64.exe"), winget);
        assertTrue(winget.contains("bin\\camel-arm64.exe"), winget);
        assertEquals(2, winget.split("InstallerSha256: \\{\\{distributionChecksumSha256\\}\\}", -1).length - 1,
                "both architecture entries must use the checksum of the same approved ZIP");
```

- [ ] **Step 4: Run the new tests and confirm they fail for the intended reasons**

Run:

```bash
cd dsl/camel-jbang/camel-launcher
mvn -Dtest=PackagePlanTest#wingetUsesDedicatedJreleaserDistribution+productionPrepareVerifiesTheArchivedWingetPayload+prepareRejectsAnArchivedWingetPayloadWithDifferentBytes+wingetRemoteOverrideRequiresExplicitTestMode+missingWingetPayloadFailsBeforeJReleaser+wingetInstallerManifestUsesLatestAcceptedSchema test
```

Expected: FAIL because JReleaser still uses Maven Central and `camel-package.sh` neither locates nor verifies the WinGet ZIP.

- [ ] **Step 5: Point only WinGet at the Apache archive**

Change the `camel-cli-winget` block in `jreleaser.yml` to:

```yaml
    winget:
      active: RELEASE
      downloadUrl: "https://archive.apache.org/dist/camel/apache-camel/{{projectVersion}}/{{artifactFile}}"
```

Do not change the Maven Central URLs for Homebrew, SDKMAN, Scoop, or Chocolatey.

- [ ] **Step 6: Verify the archived bytes before any staging or JReleaser work**

Immediately after the snapshot guard in `camel-package.sh`, reject an unguarded test override:

```bash
if [ -n "${CAMEL_PACKAGE_TEST_WINGET_REMOTE:-}" ] && [ "${CAMEL_PACKAGE_TEST_MODE:-}" != "true" ]; then
  echo "Error: CAMEL_PACKAGE_TEST_WINGET_REMOTE requires CAMEL_PACKAGE_TEST_MODE=true." 1>&2
  exit 2
fi
```

Add the WinGet paths beside `TAR` and `ZIP`:

```bash
WINGET_ZIP="$MODULE_DIR/target/camel-launcher-$PROJECT_VERSION-winget-bin.zip"
WINGET_URL="https://archive.apache.org/dist/camel/apache-camel/$PROJECT_VERSION/$(basename -- "$WINGET_ZIP")"
```

Add this check after the standard ZIP check and before website staging:

```bash
if [ ! -f "$WINGET_ZIP" ]; then
  echo "Error: WinGet release ZIP not found: $WINGET_ZIP" 1>&2
  exit 1
fi

archived_winget=$(mktemp)
cleanup_archived_winget() {
  rm -f "$archived_winget"
}
trap cleanup_archived_winget EXIT

if [ -n "${CAMEL_PACKAGE_TEST_WINGET_REMOTE:-}" ]; then
  cp "$CAMEL_PACKAGE_TEST_WINGET_REMOTE" "$archived_winget"
elif ! curl -fsSL -o "$archived_winget" "$WINGET_URL"; then
  echo "Error: archived WinGet payload is not available at $WINGET_URL" 1>&2
  exit 1
fi

if ! cmp -s "$WINGET_ZIP" "$archived_winget"; then
  echo "Error: local WinGet ZIP does not match the archived WinGet payload at $WINGET_URL" 1>&2
  exit 1
fi

cleanup_archived_winget
trap - EXIT
```

This comparison must happen before creating the website staging directory and before invoking Maven/JReleaser. Do not regenerate the ZIP after a mismatch.

- [ ] **Step 7: Document the distinct URL rules**

Replace the sentence that says all other packagers use Maven Central in `dsl/camel-jbang/camel-launcher/README.md` with:

```markdown
Both resolve to the same artifact. `search.maven.org/remotecontent` simply redirects to
`repo1.maven.org/maven2`. This rule applies **only to Homebrew**. SDKMAN, Scoop, and Chocolatey
use `repo1.maven.org` directly. WinGet uses the immutable Apache archive URL because its dedicated
ZIP is an Apache distribution payload and is not published to Maven Central.
```

Append this sentence to the native bootstrap subsection:

```markdown
Before JReleaser prepares the WinGet manifest, `camel-package.sh` downloads the versioned Apache
archive URL and fails unless its bytes exactly match the local WinGet ZIP.
```

- [ ] **Step 8: Run all package-plan tests**

Run:

```bash
cd dsl/camel-jbang/camel-launcher
mvn -Dtest=PackagePlanTest test
```

Expected: all `PackagePlanTest` tests pass. A test that deliberately supplies different local and remote bytes must fail inside the script and pass at the JUnit assertion level.

- [ ] **Step 9: Review, scan, and commit**

Run:

```bash
git diff -- dsl/camel-jbang/camel-launcher/jreleaser.yml \
  dsl/camel-jbang/camel-launcher/src/jreleaser/bin/camel-package.sh \
  dsl/camel-jbang/camel-launcher/src/test/java/org/apache/camel/dsl/jbang/launcher/PackagePlanTest.java \
  dsl/camel-jbang/camel-launcher/README.md
git diff --check
git diff | rg -n '(-----BEGIN [A-Z ]*PRIVATE KEY-----|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{36,}|sk-[A-Za-z0-9]{20,})'
```

Expected: only archive-verification changes appear, whitespace checks are silent, and the secret scan is silent. Then run:

```bash
git add dsl/camel-jbang/camel-launcher/jreleaser.yml \
  dsl/camel-jbang/camel-launcher/src/jreleaser/bin/camel-package.sh \
  dsl/camel-jbang/camel-launcher/src/test/java/org/apache/camel/dsl/jbang/launcher/PackagePlanTest.java \
  dsl/camel-jbang/camel-launcher/README.md
git commit -m "CAMEL-23703: Verify archived WinGet payload bytes" \
  -m "Co-authored-by: Codex <noreply@openai.com>"
```

### Task 4: Stage and Promote the Approved Apache Distribution Files

**Files:**
- Create: `etc/scripts/stage-winget-distro.sh`
- Modify: `etc/scripts/release-distro.sh:18-75`
- Create: `dsl/camel-jbang/camel-launcher/src/test/java/org/apache/camel/dsl/jbang/launcher/WingetDistroScriptsTest.java`
- Modify: `docs/user-manual/modules/ROOT/pages/release-guide.adoc:254-278,370-398`

**Interfaces:**
- Consumes: `stage-winget-distro.sh <version> <candidate> <zip> [work-directory]` and `release-distro.sh <version> [temp-directory] [winget-candidate]`.
- Produces: an uncommitted `dist/dev` working copy containing the ZIP, `.asc`, and `.sha512`, followed after the vote by an uncommitted `dist/release` working copy containing the same verified bytes.

- [ ] **Step 1: Add a real staging-output test**

Create `WingetDistroScriptsTest.java` with the ASF license header and this class body:

```java
package org.apache.camel.dsl.jbang.launcher;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WingetDistroScriptsTest {

    private static final Path ROOT = Paths.get("").toAbsolutePath().resolve("../../..").normalize();
    private static final Path STAGE_SCRIPT = ROOT.resolve("etc/scripts/stage-winget-distro.sh");
    private static final Path RELEASE_SCRIPT = ROOT.resolve("etc/scripts/release-distro.sh");
    private static final String VERSION = "9.9.9";
    private static final String FILE_NAME = "camel-launcher-9.9.9-winget-bin.zip";

    @Test
    void stageScriptCreatesSignedChecksummedUncommittedCandidate(@TempDir Path tmp) throws Exception {
        Path zip = tmp.resolve(FILE_NAME);
        Files.writeString(zip, "approved-winget-bytes", StandardCharsets.UTF_8);
        Path calls = tmp.resolve("calls.txt");
        Path bin = tmp.resolve("bin");
        Files.createDirectories(bin);
        writeExecutable(bin.resolve("svn"),
                "#!/bin/sh\n"
                                      + "printf 'svn %s\\n' \"$*\" >> \"" + calls + "\"\n"
                                      + "if [ \"$1\" = checkout ]; then\n"
                                      + "  for destination in \"$@\"; do :; done\n"
                                      + "  mkdir -p \"$destination\"\n"
                                      + "fi\n");
        writeExecutable(bin.resolve("gpg"),
                "#!/bin/sh\n"
                                      + "output=''\n"
                                      + "input=''\n"
                                      + "while [ $# -gt 0 ]; do\n"
                                      + "  case \"$1\" in\n"
                                      + "    --output) output=$2; shift 2 ;;\n"
                                      + "    *) input=$1; shift ;;\n"
                                      + "  esac\n"
                                      + "done\n"
                                      + "printf 'signature for %s\\n' \"$input\" > \"$output\"\n");

        ProcessBuilder pb = new ProcessBuilder("bash", STAGE_SCRIPT.toString(), VERSION, "1", zip.toString(),
                tmp.resolve("work").toString());
        pb.environment().put("PATH", bin + File.pathSeparator + System.getenv("PATH"));
        Process process = pb.start();
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), stderr);

        Path candidate = tmp.resolve("work/dist-dev/" + VERSION + "-rc1");
        Path stagedZip = candidate.resolve(FILE_NAME);
        assertEquals("approved-winget-bytes", Files.readString(stagedZip, StandardCharsets.UTF_8));
        assertTrue(Files.exists(candidate.resolve(FILE_NAME + ".asc")));
        String expectedSha512 = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-512").digest(Files.readAllBytes(stagedZip)));
        assertEquals(expectedSha512 + "  " + FILE_NAME + "\n",
                Files.readString(candidate.resolve(FILE_NAME + ".sha512"), StandardCharsets.UTF_8));
        String recorded = Files.readString(calls, StandardCharsets.UTF_8);
        assertTrue(recorded.contains("svn checkout --depth immediates"), recorded);
        assertTrue(recorded.contains("svn add " + VERSION + "-rc1"), recorded);
        assertFalse(recorded.contains(" commit "), "the staging script must stop before remote mutation");
        assertFalse(recorded.contains(" ci "), "the staging script must stop before remote mutation");
    }

    @Test
    void stageScriptRejectsAFileWithTheWrongReleaseName(@TempDir Path tmp) throws Exception {
        Path zip = tmp.resolve("wrong.zip");
        Files.writeString(zip, "bytes", StandardCharsets.UTF_8);

        Process process = new ProcessBuilder("bash", STAGE_SCRIPT.toString(), VERSION, "1", zip.toString(),
                tmp.resolve("work").toString()).start();
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));

        assertEquals(2, process.exitValue());
        assertTrue(stderr.contains(FILE_NAME), stderr);
    }

    @Test
    void releaseScriptExportsAndVerifiesTheApprovedCandidate() throws Exception {
        String script = Files.readString(RELEASE_SCRIPT, StandardCharsets.UTF_8);

        assertTrue(script.contains("WINGET_CANDIDATE=${3:-}"), script);
        assertTrue(script.contains("https://dist.apache.org/repos/dist/dev/camel/apache-camel/"), script);
        assertTrue(script.contains("svn export"), script);
        assertTrue(script.contains("WINGET_NAME=\"camel-launcher-${VERSION}-winget-bin.zip\""), script);
        assertTrue(script.contains("for suffix in \"\" \".asc\" \".sha512\""), script);
        assertTrue(script.contains("sha512sum -c"), script);
        assertTrue(script.contains("gpg --verify"), script);
        assertFalse(script.contains("mvn "), "promotion must not rebuild the approved WinGet payload");
    }

    private static void writeExecutable(Path path, String content) throws Exception {
        Files.writeString(path, content, StandardCharsets.UTF_8);
        assertTrue(path.toFile().setExecutable(true));
    }
}
```

- [ ] **Step 2: Run the tests and confirm both implementation-dependent tests fail**

Run:

```bash
cd dsl/camel-jbang/camel-launcher
mvn -Dtest=WingetDistroScriptsTest test
```

Expected: FAIL because `stage-winget-distro.sh` does not exist and `release-distro.sh` has no candidate export or verification logic.

- [ ] **Step 3: Add the focused candidate staging script**

Create `etc/scripts/stage-winget-distro.sh` with the ASF license header followed by:

```bash
set -euo pipefail

usage() {
  echo "Usage: stage-winget-distro.sh <camel-version> <candidate-number> <winget-zip> [work-directory]" 1>&2
  exit 2
}

VERSION=${1:-}
CANDIDATE=${2:-}
ZIP=${3:-}
WORK_DIR=${4:-/tmp/camel-winget-release}
DIST_DEV_REPO="https://dist.apache.org/repos/dist/dev/camel/apache-camel"

[ -n "$VERSION" ] && [ -n "$CANDIDATE" ] && [ -n "$ZIP" ] || usage
case "$VERSION" in
  *-SNAPSHOT) echo "Error: refusing to stage snapshot version '$VERSION'." 1>&2; exit 2 ;;
esac
case "$CANDIDATE" in
  ''|*[!0-9]*) echo "Error: candidate number must be a positive integer." 1>&2; exit 2 ;;
esac
if [ "$CANDIDATE" -lt 1 ]; then
  echo "Error: candidate number must be a positive integer." 1>&2
  exit 2
fi

FILE_NAME="camel-launcher-$VERSION-winget-bin.zip"
if [ "$(basename -- "$ZIP")" != "$FILE_NAME" ]; then
  echo "Error: expected WinGet ZIP filename '$FILE_NAME'." 1>&2
  exit 2
fi
if [ ! -f "$ZIP" ]; then
  echo "Error: WinGet ZIP not found: $ZIP" 1>&2
  exit 1
fi

command -v svn >/dev/null 2>&1 || { echo "Error: svn is required." 1>&2; exit 1; }
command -v gpg >/dev/null 2>&1 || { echo "Error: gpg is required." 1>&2; exit 1; }
command -v sha512sum >/dev/null 2>&1 || { echo "Error: sha512sum is required." 1>&2; exit 1; }

SVN_DIR="$WORK_DIR/dist-dev"
CANDIDATE_NAME="$VERSION-rc$CANDIDATE"
CANDIDATE_DIR="$SVN_DIR/$CANDIDATE_NAME"
if [ -e "$CANDIDATE_DIR" ]; then
  echo "Error: candidate working directory already exists: $CANDIDATE_DIR" 1>&2
  exit 1
fi

mkdir -p "$WORK_DIR"
svn checkout --depth immediates "$DIST_DEV_REPO" "$SVN_DIR"
mkdir "$CANDIDATE_DIR"
cp -p "$ZIP" "$CANDIDATE_DIR/$FILE_NAME"
gpg --batch --verbose --armor --detach-sign \
  --output "$CANDIDATE_DIR/$FILE_NAME.asc" "$CANDIDATE_DIR/$FILE_NAME"
(
  cd "$CANDIDATE_DIR"
  sha512sum "$FILE_NAME" > "$FILE_NAME.sha512"
)
(
  cd "$SVN_DIR"
  svn add "$CANDIDATE_NAME"
)

echo "WinGet candidate prepared, but not committed. Review it before upload:"
echo "cd $SVN_DIR"
echo "svn status"
echo "svn commit -m \"Apache Camel $VERSION WinGet RC$CANDIDATE\""
echo "Candidate URL after commit: $DIST_DEV_REPO/$CANDIDATE_NAME/"
```

Make the script executable with `chmod +x etc/scripts/stage-winget-distro.sh`. This is a file-mode change, not a content rewrite.

- [ ] **Step 4: Import and verify the voted candidate during release promotion**

Add these variables near the top of `etc/scripts/release-distro.sh`:

```bash
WINGET_CANDIDATE=${3:-}
DIST_DEV_REPO="https://dist.apache.org/repos/dist/dev/camel/apache-camel"
```

Change the usage validation to accept and validate the optional positive candidate number:

```bash
if [ -z "${VERSION}" -o ! -d "${DOWNLOAD}" ]; then
 echo "Usage: release-distro.sh <camel-version> [temp-directory] [winget-candidate]"
 exit 1
fi
case "${WINGET_CANDIDATE}" in
 *[!0-9]*)
   echo "Error: winget-candidate must be a positive integer."
   exit 1
   ;;
esac
if [ -n "${WINGET_CANDIDATE}" ] && [ "${WINGET_CANDIDATE}" -lt 1 ]; then
  echo "Error: winget-candidate must be a positive integer."
  exit 1
fi
```

Immediately after the existing loop that creates Maven distribution `.sha512` files, add:

```bash
if [ -n "${WINGET_CANDIDATE}" ]; then
  WINGET_NAME="camel-launcher-${VERSION}-winget-bin.zip"
  WINGET_RC_URL="${DIST_DEV_REPO}/${VERSION}-rc${WINGET_CANDIDATE}"
  for suffix in "" ".asc" ".sha512"; do
    if ! svn export "${WINGET_RC_URL}/${WINGET_NAME}${suffix}" \
        "${DOWNLOAD_LOCATION}/${WINGET_NAME}${suffix}"; then
      echo "Error: could not export approved WinGet candidate file ${WINGET_NAME}${suffix}."
      exit 1
    fi
  done
  if ! sha512sum -c "${WINGET_NAME}.sha512"; then
    echo "Error: approved WinGet candidate SHA-512 verification failed."
    exit 1
  fi
  if ! gpg --verify "${WINGET_NAME}.asc" "${WINGET_NAME}"; then
    echo "Error: approved WinGet candidate signature verification failed."
    exit 1
  fi
fi
```

Do not invoke Maven, Assembly Plugin, `zip`, or any repacking command in this promotion path. The existing wildcard copy then places all three exported files into the local `dist/release` working copy.

- [ ] **Step 5: Document the pre-vote candidate flow**

After the main Camel `release:perform` command in `release-guide.adoc`, add:

```adoc
. Stage the WinGet payload in the Apache development distribution repository:

* `release:perform` builds the non-attached WinGet ZIP in its release checkout. Prepare the
candidate with the same candidate number used in the vote:

  cd ${CAMEL_ROOT_DIR}/etc/scripts
  ./stage-winget-distro.sh <Camel version> <candidate number> \
    ${CAMEL_ROOT_DIR}/target/checkout/dsl/camel-jbang/camel-launcher/target/camel-launcher-<Camel version>-winget-bin.zip

* Review the printed `svn status`, commit the working copy only after checking the ZIP, `.asc`,
and `.sha512`, and include the resulting `dist/dev` candidate URL together with the Nexus staging
repository URL in the vote email. The WinGet ZIP is not present in the Nexus staging repository.
```

- [ ] **Step 6: Document post-vote promotion and archive availability**

Change the existing release distribution command to:

```adoc
  ./release-distro.sh <Camel version> <temp-directory> <WinGet candidate number>
```

Add below it:

```adoc
* The script exports the voted WinGet ZIP, signature, and SHA-512 checksum from `dist/dev`, verifies
them, and adds the exact files to the local `dist/release` working copy. It does not rebuild the ZIP.
Review and commit the combined working copy using the commands printed by the script.

* After the release is available from
`https://archive.apache.org/dist/camel/apache-camel/<Camel version>/`, run the JReleaser package
preparation. WinGet preparation fails until the archived ZIP is available and byte-identical to the
approved local ZIP.

* Remove the promoted candidate after the release copy is committed:

  svn rm https://dist.apache.org/repos/dist/dev/camel/apache-camel/<Camel version>-rc<WinGet candidate number> \
    -m "Remove promoted Apache Camel <Camel version> WinGet candidate"
```

- [ ] **Step 7: Run script behavior, syntax, and package tests**

Run:

```bash
bash -n etc/scripts/stage-winget-distro.sh
bash -n etc/scripts/release-distro.sh
cd dsl/camel-jbang/camel-launcher
mvn -Dtest=WingetDistroScriptsTest,PackagePlanTest test
```

Expected: both Bash syntax checks return zero. The staging test verifies the exact ZIP bytes, detached signature file, SHA-512 content, local SVN add, and absence of a commit. All package-plan tests pass.

- [ ] **Step 8: Review, scan, and commit**

Run:

```bash
git diff -- etc/scripts/stage-winget-distro.sh etc/scripts/release-distro.sh \
  dsl/camel-jbang/camel-launcher/src/test/java/org/apache/camel/dsl/jbang/launcher/WingetDistroScriptsTest.java \
  docs/user-manual/modules/ROOT/pages/release-guide.adoc
git diff --check
git diff | rg -n '(-----BEGIN [A-Z ]*PRIVATE KEY-----|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{36,}|sk-[A-Za-z0-9]{20,})'
```

Expected: only candidate staging, promotion, tests, and release documentation appear. Whitespace and secret scans are silent. Then run:

```bash
git add etc/scripts/stage-winget-distro.sh etc/scripts/release-distro.sh \
  dsl/camel-jbang/camel-launcher/src/test/java/org/apache/camel/dsl/jbang/launcher/WingetDistroScriptsTest.java \
  docs/user-manual/modules/ROOT/pages/release-guide.adoc
git commit -m "CAMEL-23703: Stage WinGet payload with Apache distributions" \
  -m "Co-authored-by: Codex <noreply@openai.com>"
```

## Final Verification Gate

- [ ] Run the launcher module's complete test suite from its module directory:

```bash
cd dsl/camel-jbang/camel-launcher
mvn verify
```

Expected: BUILD SUCCESS with no skipped or excluded test failures.

- [ ] Apply the module's normal formatting and generated-file check:

```bash
cd dsl/camel-jbang/camel-launcher
mvn -DskipTests install
```

Expected: BUILD SUCCESS and `git status --short` shows no unexpected generated changes.

- [ ] Run the native release gate with the pinned llvm-mingw toolchain available:

```bash
cd ../../..
mvn -P apache-snapshots \
  -pl buildingtools,tooling/camel-exe,dsl/camel-jbang/camel-launcher clean verify \
  -Dcamel.exe.build=true -DskipTests
```

Expected: BUILD SUCCESS, both executables are present only in the WinGet ZIP, and the standard ZIP and TAR remain free of native executables. If the pinned compiler is unavailable on the current machine, report this command as unverified and require the GitHub Actions `camel-launcher-native` job to supply the evidence.

- [ ] Verify the final history and worktree:

```bash
git diff --check
git status --short --branch
git log --oneline -6
```

Expected: no whitespace errors, no unintended uncommitted files, and four focused implementation commits after the approved design and plan commits.
