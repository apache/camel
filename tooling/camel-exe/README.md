# Camel :: Exe

Standalone Maven module that builds the native Windows **`camel.exe`** bootstrap for the
[Camel CLI launcher](../../dsl/camel-jbang/camel-launcher).

## Purpose

WinGet's portable installer (and similar package managers) require a genuine executable as
the `camel` command. A `.bat` or `.cmd` shim is not enough — it produces a broken
`camel.exe` symlink. This module compiles a minimal x64 native binary that satisfies that
contract.

`camel.exe` does not embed Java or run Camel itself. It locates `camel.bat` in the same
directory, forwards the caller's command line (preserving spaces and Unicode), and returns
its exit code. All Java discovery and CLI parsing remain in `camel.bat` and the launcher
JAR.

## Why a separate module?

The launcher (`dsl/camel-jbang/camel-launcher`) is a fat JAR with a large upstream
dependency tree (jbang core, plugins, repackager, and their transitive Camel deps). The
native bootstrap is ~100 lines of C with **no Java dependencies**. Keeping it here allows:

- **Fast Windows CI** — `mvn -pl tooling/camel-exe verify` compiles and tests the exe
  without building the full jbang graph.
- **Clear separation** — packaging bootstrap vs. runtime launcher.
- **Reusable artifact** — `camel-launcher` copies the attached `exe` artifact into its
  distribution on Windows (`bin/camel.exe` inside `camel-launcher-*-bin.zip`).

## Build and test

On a Windows x64 host with Microsoft C/C++ Build Tools (`cl.exe`) on PATH:

```bash
mvn -pl tooling/camel-exe verify -Dcamel.exe.requireWindowsExe=true
```

Release and integration builds that produce the launcher ZIP also build this module first:

```bash
mvn -pl tooling/camel-exe,dsl/camel-jbang/camel-launcher -am install -Dcamel.launcher.requireWindowsExe=true
```

See [src/main/native/README.md](src/main/native/README.md) for MSVC setup, compiler
flags, and the release-gate profile.
