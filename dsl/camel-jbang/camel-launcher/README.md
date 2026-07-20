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

When `-Dcamel.exe.build=true` is enabled, Maven also creates a WinGet-only archive,
`camel-launcher-<version>-winget-bin.zip`, containing the native Windows bootstraps built by
[`tooling/camel-exe`](../../../tooling/camel-exe): `bin/camel-x64.exe` and `bin/camel-arm64.exe`.
The assembly uses `attach=false`, so the ZIP remains in this module's `target` directory for the
Apache distribution release flow and is not installed or deployed to a Maven repository.
Release builds use llvm-mingw to cross-compile both executables on Linux. The launcher module verifies during `verify`
that both files are staged, absent from the public archives, and present in the WinGet ZIP:

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
   
   # On Windows
   bin\camel.bat [command] [options]
   ```

   The native executables are confined to the WinGet-only archive. WinGet selects the executable matching the host
   architecture and exposes it as `camel.exe`.

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

The dedicated `camel-launcher-<version>-winget-bin.zip` ships `camel-x64.exe` and
`camel-arm64.exe` for WinGet, which requires a genuine portable executable per architecture
(see the WinGet `installer.yaml.tpl` override). The public ZIP and TAR archives do not contain
these WinGet-specific executables.

Chocolatey and Scoop use `camel.bat` as their entry point instead (it needs only a JRE, so it is
architecture-neutral and requires no per-architecture binary).
