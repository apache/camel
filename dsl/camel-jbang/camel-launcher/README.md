# Camel JBang Self-Executing Launcher

This module provides a self-contained executable JAR that includes all dependencies required to run Camel JBang without the need for the JBang two-step process.

The launcher uses Spring Boot's loader tools to create a self-executing JAR with a nested structure, similar to Spring Boot's executable JARs. This provides better performance and avoids classpath conflicts compared to traditional fat JARs.

## Building

To build the fat-jar launcher:

```bash
mvn clean package
```

This will create:
1. A self-executing JAR (`camel-launcher-<version>.jar`) in the `target` directory using Spring Boot's nested JAR structure
2. Distribution archives (`camel-launcher-<version>-bin.zip` and `camel-launcher-<version>-bin.tar.gz`) in the `target` directory

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
