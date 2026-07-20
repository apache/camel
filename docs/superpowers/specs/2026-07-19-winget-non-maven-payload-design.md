# CAMEL-23703: Non-Maven WinGet Payload Design

Date: 2026-07-19

## Context

The WinGet manifest needs a ZIP containing the Camel launcher distribution plus the native
`camel-x64.exe` and `camel-arm64.exe` bootstraps. The current build creates
`camel-launcher-<version>-winget-bin.zip` as an attached Maven assembly. Attaching the assembly
causes Maven's `install` and `deploy` phases to publish it as an additional `camel-launcher`
artifact with the Maven repository's `.sha1` checksum.

The ZIP is a WinGet installer payload, not a Java dependency. It should therefore remain part of
the Apache Camel release while no longer being published to Maven Central.

## Decision

Generate the WinGet ZIP during the release candidate build with the existing Maven dependency and
assembly plugins, but configure the WinGet assembly with `attach=false`. Stage, vote on, sign, and
publish the generated ZIP through the Apache release distribution instead of Maven Central.

JReleaser will continue to read the local ZIP to generate the WinGet manifest and its SHA-256
checksum. The manifest will reference the immutable archived Apache release URL.

## Goals

- Keep `camel-launcher-<version>-winget-bin.zip` out of Maven Central.
- Preserve WinGet's ZIP-based portable installation and architecture-specific command selection.
- Build the ZIP from the normal launcher output and the native executables produced by
  `tooling/camel-exe`.
- Produce byte-for-byte reproducible native executables and WinGet ZIPs from the same released
  source, toolchain, and build instructions.
- Vote on and promote the exact bytes that WinGet later downloads.
- Fail the release when required artifacts, signatures, checksums, or reproducibility checks are
  missing or inconsistent.

## Non-goals

- Do not create a custom Windows installer.
- Do not add download, extraction, upgrade, PATH, or uninstall behavior to `camel.exe`.
- Do not add the native executables to the standard cross-platform `-bin.zip` or `-bin.tar.gz`.
- Do not publish release payloads from snapshots.
- Do not rebuild the WinGet ZIP after the release vote.

## Artifact Flow

```text
released source and pinned llvm-mingw
                |
                v
 tooling/camel-exe classified x64 and arm64 artifacts
                |
                | maven-dependency-plugin:copy
                v
 camel-launcher target staging directory
                |
                | maven-assembly-plugin:single, attach=false
                v
 camel-launcher-<version>-winget-bin.zip
                |
                +--> dist/dev release candidate + .asc + .sha512
                |              |
                |              v after approval
                |       dist/release and Apache archive
                |
                +--> JReleaser checksum and WinGet manifest
```

## Maven Build Design

The existing `include-camel-exe` profile remains responsible for declaring and copying the exact
native artifacts:

- `org.apache.camel:camel-exe:<version>:exe:x64`
- `org.apache.camel:camel-exe:<version>:exe:arm64`

The release build must resolve these artifacts from the same release reactor. It must not use
snapshot coordinates or reconstruct the ZIP from artifacts downloaded after the vote.

The `assemble-winget-bin` execution remains bound to `package` and continues using
`src/main/assembly/winget-bin.xml`. Its execution configuration will set `attach` to `false`.
This leaves the generated ZIP in `camel-launcher/target` while excluding it from the Maven
project's attached artifacts. Consequently, Maven `install` and `deploy` will not copy it into a
local or remote Maven repository. Maven will not generate a `.sha1`, `.sha512`, or detached
signature for this non-attached ZIP.

The assembly must be produced by Maven Assembly Plugin. Release scripts must not unpack and rezip
the distribution with platform-specific `zip`, PowerShell, or archive utilities because file order,
permissions, path separators, and timestamps may vary between implementations.

## Reproducibility Contract

The release build will rely on these controlled inputs:

- The released source commit.
- The exact Maven and JDK versions required by the Camel release process.
- Maven Assembly Plugin 3.8.0 from the Camel parent plugin management.
- `project.build.outputTimestamp` from the released root POM.
- llvm-mingw version `20260616`, downloaded from the pinned filename and verified with the pinned
  SHA-256 before use.
- Exact, versioned Maven coordinates for all copied artifacts.

`project.build.outputTimestamp` supplies the timestamp for archive entries. The assembly descriptor
defines the file layout and file modes. `dependency:copy` copies the native executable bytes without
transforming them.

A reproducibility gate will build the native executables and WinGet ZIP twice from clean output
directories using the release build instructions. It will compare SHA-256 values for:

- `camel-x64.exe`
- `camel-arm64.exe`
- `camel-launcher-<version>-winget-bin.zip`

Any mismatch fails the release. The first implementation must not claim that the native executable
build is reproducible until this gate passes. If it fails, the differing binaries must be inspected
before changing compiler or linker flags.

The release process must stage the ZIP generated by the approved release candidate. Promotion must
copy that exact file from `dist/dev` to `dist/release`; it must not invoke Maven again. The detached
signature and SHA-512 file created by the Apache distribution staging step must accompany the ZIP.

Camel's Maven release plugin already runs `deploy -Prelease` in its release checkout, and the
`release` profile enables `camel.exe.build`. After `release:perform`, the non-attached ZIP is read
from:

```text
target/checkout/dsl/camel-jbang/camel-launcher/target/
    camel-launcher-<version>-winget-bin.zip
```

The release operator stages that file before starting the vote. The Nexus staging repository and
the WinGet payload candidate URL are both included in the same vote email.

## Apache Distribution and URL Stability

The release candidate will stage these files under the existing Camel release directory:

- `camel-launcher-<version>-winget-bin.zip`
- `camel-launcher-<version>-winget-bin.zip.asc`
- `camel-launcher-<version>-winget-bin.zip.sha512`

After the release vote, the files will be promoted with the other release artifacts. WinGet
publication must wait until the archived URL is available and its bytes match the promoted ZIP:

```text
https://archive.apache.org/dist/camel/apache-camel/<version>/camel-launcher-<version>-winget-bin.zip
```

The WinGet manifest will use this versioned archive URL because manifests for older Camel versions
remain in `winget-pkgs` after a release leaves `downloads.apache.org`. The archive URL therefore
avoids invalidating older manifests when Apache rotates current releases.

Before JReleaser publishes or submits the WinGet manifest, the packaging command must download or
hash the archived URL and compare it with the local ZIP. A missing URL or checksum mismatch is a
hard failure. The command may be rerun after Apache archive synchronization completes; it must not
regenerate the ZIP.

## Release Script Integration

A new `etc/scripts/stage-winget-distro.sh` command will prepare the WinGet release candidate. It
accepts the release version, candidate number, and path to the ZIP produced by `release:perform`.
It validates the filename and release version, creates the detached signature and SHA-512 file,
prepares an SVN working copy under:

```text
https://dist.apache.org/repos/dist/dev/camel/apache-camel/<version>-rc<candidate>/
```

Like the existing `release-distro.sh`, it stops before the remote commit and prints the exact SVN
status and commit commands for the release operator to review.

The staging script, not Maven, creates
`camel-launcher-<version>-winget-bin.zip.asc` and
`camel-launcher-<version>-winget-bin.zip.sha512`. This matches the existing Apache distribution
flow in `release-distro.sh`, which removes Maven repository `.sha1` files and creates `.sha512`
files before preparing the `dist/release` working copy.

After a successful vote, `etc/scripts/release-distro.sh` will accept an optional WinGet candidate
number. When supplied, it exports the approved ZIP, signature, and checksum from `dist/dev`, verifies
them, and adds those exact files to the local `dist/release` working copy alongside the artifacts it
already retrieves from the released Maven repository. The operator reviews and commits that combined
working copy using the existing release process, then removes the promoted candidate directory from
`dist/dev`.

The release guide will place WinGet candidate staging after `release:perform` and before the vote. It
will place candidate promotion in the existing `release-distro.sh` step after the vote.

## JReleaser Design

The dedicated `camel-cli-winget` distribution remains because it describes a payload whose contents
differ from the public `camel-cli` ZIP. Its artifact path continues to reference the local
`target/camel-launcher-<version>-winget-bin.zip`.

The WinGet `downloadUrl` changes from the Maven Central coordinate to the versioned Apache archive
URL. The existing custom installer template continues to emit two installer entries that share the
same ZIP and checksum while selecting different nested executables:

- x64 selects `bin/camel-x64.exe`.
- arm64 selects `bin/camel-arm64.exe`.

JReleaser must calculate `distributionChecksumSha256` from the same local ZIP whose SHA-512 and
signature were staged for the release. The packaging tests will assert the final URL and compare the
manifest SHA-256 with the local and remotely published bytes.

## Failure Handling

The release or package preparation fails when any of the following occurs:

- Either native executable is missing.
- The WinGet ZIP is missing either native executable or required launcher content.
- A clean rebuild produces a different executable or ZIP checksum.
- Maven installs or deploys the WinGet ZIP as an attached artifact.
- A Maven-generated `.sha1` for the WinGet ZIP appears in the release staging input, indicating that
  the ZIP was still attached and deployed.
- The detached signature or SHA-512 file is missing from the release candidate.
- The archived Apache URL is unavailable.
- The archived ZIP differs from the approved local ZIP.
- The generated WinGet manifest checksum differs from the approved ZIP.
- A snapshot version reaches release packaging.

No failure may be converted into a skip. The operator may retry only availability checks after
Apache distribution synchronization; the approved artifact itself remains unchanged.

## Verification

The implementation will add or update focused verification for these behaviors:

1. The native executable integration test verifies staging and both entries in the WinGet ZIP.
2. The standard ZIP and TAR tests verify that native executables remain excluded.
3. A Maven integration check deploys into a temporary file repository and verifies that no
   `camel-launcher-<version>-winget-bin.zip` or corresponding `.sha1` is deployed.
4. A two-build reproducibility check compares the native executable and WinGet ZIP hashes.
5. Package-plan tests verify the Apache archive download URL and reject Maven Central as the WinGet
   payload host.
6. Manifest tests verify both architectures, nested executable paths, and the exact ZIP checksum.
7. Release staging verification checks the ZIP, `.asc`, and `.sha512` files before promotion.
8. Remote verification compares the archived ZIP with the approved local ZIP before WinGet
   publication.

## Expected Code and Documentation Scope

Implementation is expected to update only the files required by this release flow:

- `dsl/camel-jbang/camel-launcher/pom.xml`
- `dsl/camel-jbang/camel-launcher/jreleaser.yml`
- `dsl/camel-jbang/camel-launcher/src/jreleaser/bin/camel-package.sh`
- Focused launcher packaging and manifest tests
- Launcher and native executable packaging documentation
- `.github/workflows/camel-launcher-native-exe.yml`
- `etc/scripts/stage-winget-distro.sh`
- `etc/scripts/release-distro.sh`
- `docs/user-manual/modules/ROOT/pages/release-guide.adoc`

No unrelated release or launcher refactoring is included.

## Alternatives Rejected

### Include native executables in the standard launcher ZIP

This would remove the dedicated payload but would add two Windows-only binaries to every launcher
ZIP consumer. The approved design keeps the standard distribution architecture-neutral.

### Custom installer executable

A custom installer would still require a published installer artifact and would add network access,
checksum verification, extraction, silent installation, upgrade, rollback, PATH, and uninstall
behavior. WinGet's portable ZIP support already handles installation lifecycle without that added
security and maintenance surface.

### Generate the ZIP after release publication

Rebuilding or first generating the ZIP after the release vote would mean WinGet downloads bytes that
were not part of the approved release candidate. It would also make reproducibility and provenance
harder to verify. The approved ZIP is therefore created and staged before the vote.

## Success Criteria

- Maven Central contains no `camel-launcher-<version>-winget-bin.zip` artifact.
- The approved Apache release contains the ZIP, detached signature, and SHA-512 checksum.
- Two clean release builds produce identical x64 and arm64 executables and an identical WinGet ZIP.
- The promoted ZIP is byte-for-byte identical to the approved release candidate.
- The WinGet manifest references the immutable Apache archive URL and contains the correct SHA-256.
- WinGet selects `camel-x64.exe` on x64 and `camel-arm64.exe` on arm64 while retaining the complete
  launcher distribution beside the selected executable.
