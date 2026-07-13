# Native build (`camel.c`)

Compiler flags, MSVC setup, and release-gate details for the `camel.exe` bootstrap.
See the [module README](../../../README.md) for purpose, architecture, and integration
with `camel-launcher`.

## MSVC setup

The `build-windows-exe` Maven profile activates automatically on Windows and invokes
`cl.exe`. MSVC must be on PATH — open a developer command prompt (e.g. run
`vcvars64.bat`) or provision MSVC in CI before running Maven.

## Compiler invocation

    cl /nologo /W4 /O1 /MT /Brepro /Fe:target\camel.exe src\main\native\camel.c /link /Brepro /SUBSYSTEM:CONSOLE

- `/MT` — static CRT (no runtime DLL dependency)
- `/Brepro` — deterministic PE header timestamp on both compiler and linker, so
  repeated builds from the same source produce byte-identical output

## Release gate

Release builds pass `-Dcamel.exe.requireWindowsExe=true`, which activates the
`require-windows-exe` profile. The enforcer fails the build if `target/camel.exe` is
absent. Because MSVC cannot cross-compile from macOS/Linux, the launcher ZIP/TAR that
carries `camel.exe` must be assembled on a Windows x64 host.
