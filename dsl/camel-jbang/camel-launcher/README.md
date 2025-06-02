# Camel JBang Fat-Jar Launcher

This module provides a self-contained executable JAR that includes all dependencies required to run Camel JBang without the need for the JBang two-step process.

## Building

To build the fat-jar launcher:

```bash
mvn clean package
```

This will create:
1. A fat-jar (`camel-launcher-<version>.jar`) in the `target` directory
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
- Faster startup time (no dependency resolution step)
- Each fat-jar is its own release, avoiding version complexity
- Can still be used with JBang if preferred
