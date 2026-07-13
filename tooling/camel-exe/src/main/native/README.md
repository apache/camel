# camel.exe native bootstrap

`camel.c` compiles to a minimal x64 `camel.exe` that WinGet's portable installer
exposes as the `camel` command. WinGet's portable command must be a real
executable (a `.bat`/`.cmd`/`.ps1` target produces a broken `camel.exe` symlink),
so a genuine native binary is required.

## What it does

`camel.exe` locates the `camel.bat` in the same directory, forwards the caller's
exact command-line tail (preserving Unicode and Windows quoting) to
`cmd.exe /S /C`, inherits the standard streams, and returns the child exit code.
It performs no Java discovery and no Camel option parsing; all of that lives in
`camel.bat`.

## Build

Built by the `build-windows-exe` Maven profile, which activates automatically on
Windows. It requires the Microsoft C/C++ Build Tools (`cl.exe`) on PATH — set up
a developer command prompt (e.g. `vcvars64.bat`) or a CI action that provisions
MSVC before running Maven:

    mvn -pl tooling/camel-exe package

The compiler is invoked as:

    cl /nologo /W4 /O1 /MT /Brepro /Fe:target\camel.exe src\main\native\camel.c /link /Brepro /SUBSYSTEM:CONSOLE

`/MT` links the static CRT (no runtime DLL dependency); `/Brepro` on both the
compiler and linker makes the PE header timestamp deterministic, so repeated
builds from the same source produce byte-identical output.

## Release gate

Release builds pass `-Dcamel.exe.requireWindowsExe=true`, which makes the
`require-windows-exe` profile fail the build if `target/camel.exe` is absent. This
prevents publishing a Windows package that cannot satisfy WinGet's portable
command contract. Because MSVC cannot cross-compile from macOS/Linux, the release
ZIP/TAR that carries `camel.exe` must be assembled on a Windows x64 host.
