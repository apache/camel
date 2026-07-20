# Camel :: Exe

Standalone Maven module that builds the native Windows **`camel-x64.exe`** and
**`camel-arm64.exe`** bootstraps for the
[Camel CLI launcher](../../dsl/camel-jbang/camel-launcher).

## Purpose

WinGet's portable installer (and similar package managers) require a genuine executable as
the `camel` command. A `.bat` or `.cmd` shim is not enough — it produces a broken
`camel.exe` symlink. This module compiles minimal native binaries that satisfy that
contract.

`camel-x64.exe` / `camel-arm64.exe` do not embed Java or run Camel. They locate
`camel.bat` in the same directory, forward the caller's command line (preserving spaces
and Unicode), and return its exit code. All Java discovery and CLI parsing remain in
`camel.bat` and the launcher JAR.

## Why a separate module?

The launcher (`dsl/camel-jbang/camel-launcher`) is a fat JAR with a large upstream
dependency tree (jbang core, plugins, repackager, and their transitive Camel deps). The
native bootstrap is ~100 lines of C with **no Java dependencies**. Keeping it here allows:

- **Fast CI** — `mvn -pl tooling/camel-exe verify -Dcamel.exe.build=true` compiles and
  tests the exe without building the full jbang graph.
- **Clear separation** — packaging bootstrap vs. runtime launcher.
- **Reusable artifact** — `camel-launcher` copies the attached `exe` artifacts into its
  distribution (`bin/camel-x64.exe` and `bin/camel-arm64.exe` inside
  `camel-launcher-*-bin.zip`).

## Build and test

Requires [llvm-mingw](https://github.com/mstorsjo/llvm-mingw) (release `20260616` pinned)
on `PATH`. Works on Linux, macOS, or Windows — no host-OS restriction.

```bash
mvn -pl tooling/camel-exe verify -Dcamel.exe.build=true
```

Release and integration builds that produce the launcher ZIP also build this module first:

```bash
mvn -pl tooling/camel-exe,dsl/camel-jbang/camel-launcher -am verify -Dcamel.exe.build=true
```

See [src/main/native/README.md](src/main/native/README.md) for toolchain setup, compiler
flags, and the release-gate profile.
