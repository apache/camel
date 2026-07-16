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

On Windows, the distribution archives also include `bin/camel.exe`, a native bootstrap built by
[`tooling/camel-exe`](../../../tooling/camel-exe). Release builds on a Windows x64 host run an integration test during `verify` that asserts
`target/camel.exe` is staged and `bin/camel.exe` is present in the assembled ZIP:

```bash
mvn -pl tooling/camel-exe,dsl/camel-jbang/camel-launcher -am verify -Dcamel.launcher.requireWindowsExe=true
```

To build and test only the native bootstrap:

```bash
mvn -pl tooling/camel-exe verify -Dcamel.exe.requireWindowsExe=true
```

See [`tooling/camel-exe/src/main/native/README.md`](../../../tooling/camel-exe/src/main/native/README.md) for MSVC requirements.

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
   bin\camel.exe [command] [options]
   ```

   `camel.exe` and `camel.bat` behave identically on Windows; `camel.exe` exists for package managers
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
