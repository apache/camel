# Camel CLI Self-Executing Launcher

This module provides a self-contained executable JAR that includes all dependencies required to run Camel CLI without the need for the JBang two-step process.

The launcher uses Spring Boot's loader tools to create a self-executing JAR with a nested structure, similar to Spring Boot's executable JARs. This provides better performance and avoids classpath conflicts compared to traditional fat JARs.

## Building

To build the fat-jar launcher:

```bash
mvn clean package
```

This will create:
1. A self-executing JAR (`camel-launcher-<version>.jar`) in the `target` directory using Spring Boot's nested JAR structure
2. Distribution archives (`camel-launcher-<version>-bin.zip` and `camel-launcher-<version>-bin.tar.gz`) in the `target` directory

When `-Dcamel.exe.build=true` is enabled, the distribution archives also include native Windows bootstraps built by
[`tooling/camel-exe`](../../../tooling/camel-exe): `bin/camel-x64.exe` and `bin/camel-arm64.exe`.
Release builds use llvm-mingw to cross-compile both executables on Linux. The launcher module verifies during `verify`
that both files are staged and present in the assembled ZIP:

```bash
mvn -pl buildingtools,tooling/camel-exe,dsl/camel-jbang/camel-launcher -am verify -Dcamel.exe.build=true
```

To build and test only the native bootstraps:

```bash
mvn -pl buildingtools,tooling/camel-exe -am verify -Dcamel.exe.build=true
```

See [`tooling/camel-exe/src/main/native/README.md`](../../../tooling/camel-exe/src/main/native/README.md) for
llvm-mingw requirements.

## Usage

### Using the JAR directly

```bash
java -jar camel-launcher-<version>.jar [command] [options]
```

For example:

```bash
java -jar camel-launcher-<version>.jar version
java -jar camel-launcher-<version>.jar run hello.java
```

### Using the distribution

1. Extract the distribution archive:
   ```bash
   unzip camel-launcher-<version>-bin.zip
   # or
   tar -xzf camel-launcher-<version>-bin.tar.gz
   ```

2. Use the provided scripts:
   ```bash
   # On Unix/Linux
   ./bin/camel.sh [command] [options]
   
   # On Windows (x64 or arm64)
   bin\camel.bat [command] [options]

   # On Windows (x64)
   bin\camel-x64.exe [command] [options]

   # On Windows (arm64)
   bin\camel-arm64.exe [command] [options]
   ```

   The native executables and `camel.bat` behave identically on Windows; the native executables exist for package managers
   (such as WinGet) that require a genuine executable command.

## Benefits

- No need for JBang installation
- Single executable JAR with all dependencies included
- Faster startup time (no dependency resolution step, on-demand class loading)
- Better memory usage (only loads classes that are actually used)
- Avoids classpath conflicts (dependencies kept as separate JARs)
- Each self-executing JAR is its own release, avoiding version complexity
- Can still be used with JBang if preferred

## JAR Structure

The launcher uses Spring Boot's nested JAR structure:

```
camel-launcher-<version>.jar
 |
 +-META-INF
 |  +-MANIFEST.MF (Main-Class: org.springframework.boot.loader.launch.JarLauncher)
 +-org
 |  +-springframework
 |     +-boot
 |        +-loader
 |           +-<spring boot loader classes>
 +-BOOT-INF
    +-classes
    |  +-org/apache/camel/dsl/jbang/launcher/CamelLauncher.class
    |  +-<other application classes>
    +-lib
       +-camel-jbang-core-<version>.jar
       +-camel-main-<version>.jar
       +-<other dependency JARs>
```

This structure provides better performance and reliability compared to traditional fat JARs where all classes are merged together.

## Packaging (JReleaser)

The `jreleaser.yml` in this module configures distribution packaging for Homebrew, SDKMAN,
WinGet, Scoop, and Chocolatey. Custom templates live under
`src/jreleaser/distributions/camel-cli/<packager>/`.

### Homebrew Maven Central URL convention

Homebrew's `FormulaAudit::Urls` rubocop
([source](https://docs.brew.sh/rubydoc/RuboCop/Cop/UrlHelper.html)) requires Maven Central
artifacts to use the `search.maven.org` redirector URL instead of `repo1.maven.org`:

```
# Required by Homebrew:
https://search.maven.org/remotecontent?filepath=org/apache/camel/camel-launcher/VERSION/FILE

# NOT accepted (triggers brew audit failure):
https://repo1.maven.org/maven2/org/apache/camel/camel-launcher/VERSION/FILE
```

Both resolve to the same artifact — `search.maven.org/remotecontent` simply redirects to
`repo1.maven.org/maven2`. This rule applies **only to Homebrew**; the other packagers
(SDKMAN, WinGet, Scoop, Chocolatey) use `repo1.maven.org` directly.

### Native bootstrap executables

The distribution zip ships `camel-x64.exe` and `camel-arm64.exe` for WinGet, which requires a
genuine portable executable per architecture (see the WinGet `installer.yaml.tpl` override).

Chocolatey and Scoop use `camel.bat` as their entry point instead. Their custom templates
remove both exe files during install to avoid exposing unused executables on PATH.

### Chocolatey ARM64

Native ARM64 support in Chocolatey is not yet available. Tracking issue:
https://github.com/chocolatey/choco/issues/1803

Until resolved, the Chocolatey package uses `camel.bat` as its entry point on both x64 and ARM64
Windows. `camel.bat` is architecture-neutral (it only needs a Java runtime, not a native
executable), so this works without emulation; it just means Chocolatey isn't yet shipping a
genuine per-architecture executable the way WinGet does.
