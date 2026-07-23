# Native build (`camel.c`)

Toolchain setup, compiler flags, and release-gate details for the `camel-x64.exe` and
`camel-arm64.exe` bootstraps. See the [module README](../../../README.md) for purpose,
architecture, and integration with `camel-launcher`.

## Toolchain: llvm-mingw

The `build-native-exe` Maven profile cross-compiles for Windows x64 and arm64 using
[llvm-mingw](https://github.com/mstorsjo/llvm-mingw), a clang/LLVM-based mingw-w64
toolchain. The build is host-OS-independent: it works on Linux, macOS, or Windows
wherever llvm-mingw is installed.

### Installation

Download the pinned release (`20260616`) from
[GitHub releases](https://github.com/mstorsjo/llvm-mingw/releases/tag/20260616):

- **Linux (x86\_64):** `llvm-mingw-20260616-ucrt-ubuntu-22.04-x86_64.tar.xz`
  (SHA256: `534b92e067b22a6b4441f48ae9240a3341b17825d04d577eab0cf85c44b4deda`)
- **macOS (universal):** `llvm-mingw-20260616-ucrt-macos-universal.tar.xz`
  (SHA256: `2cab02a2e964bd4aae981150a45985d07c657cfa8d244959eb9e2dcc5eedd7b1`)

Extract and add the `bin/` directory to `PATH` so that `x86_64-w64-mingw32-clang` and
`aarch64-w64-mingw32-clang` are available.

## Compiler invocations

    x86_64-w64-mingw32-clang -Wall -Wextra -O1 -static -mconsole -municode -o target/camel-x64.exe src/main/native/camel.c
    aarch64-w64-mingw32-clang -Wall -Wextra -O1 -static -mconsole -municode -o target/camel-arm64.exe src/main/native/camel.c

- `-static` — statically links libgcc/compiler-rt; UCRT itself is dynamically imported
  via `api-ms-win-crt-*.dll` API-set forwarders (OS components on Windows 10 1607+)
- `-mconsole` — sets PE subsystem to `CONSOLE`, equivalent to MSVC `/SUBSYSTEM:CONSOLE`
- `-municode`: selects the wide-character CRT startup so the `wmain` entry point links
  and receives the Unicode command line

## CRT linking difference from MSVC

The previous MSVC build used `/MT` (static CRT, no runtime DLL dependency). The
clang/llvm-mingw build dynamically imports UCRT via `api-ms-win-crt-*.dll` API-set
forwarders. UCRT ships as an OS component on Windows 10 1607+, which is WinGet's
practical minimum target.

## Release gate

Release builds use `-Prelease`, which sets `camel.exe.build=true`, activating the
`require-native-exe` profile. The enforcer fails the build if `target/camel-x64.exe` or
`target/camel-arm64.exe` is absent.
