# Camel Repackager Maven Plugin

This Maven plugin creates self-executing JARs using Spring Boot's loader tools, providing the same nested JAR structure that Spring Boot uses for its executable JARs.

## Overview

Instead of creating traditional "fat JARs" where all dependencies are unpacked and merged into a single JAR (like maven-shade-plugin does), this plugin creates JARs with Spring Boot's nested structure:

```
example.jar
 |
 +-META-INF
 |  +-MANIFEST.MF
 +-org
 |  +-springframework
 |     +-boot
 |        +-loader
 |           +-<spring boot loader classes>
 +-BOOT-INF
    +-classes
    |  +-<your application classes>
    +-lib
       +-dependency1.jar
       +-dependency2.jar
```

## Benefits

- **Faster startup**: No need to unpack all dependencies
- **Better memory usage**: Dependencies are loaded on-demand
- **Avoids classpath conflicts**: Preserves original JAR signatures and avoids file conflicts
- **Smaller memory footprint**: Only loads classes that are actually used
- **Better debugging**: Easier to identify which JAR a class comes from

## Usage

Add the plugin to your `pom.xml`:

```xml
<plugin>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-repackager-maven-plugin</artifactId>
    <version>${camel.version}</version>
    <executions>
        <execution>
            <id>repackage-executable</id>
            <phase>package</phase>
            <goals>
                <goal>repackage</goal>
            </goals>
            <configuration>
                <mainClass>com.example.MyMainClass</mainClass>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Configuration

| Parameter | Description | Default | Required |
|-----------|-------------|---------|----------|
| `mainClass` | The main class to use for the executable JAR | | Yes |
| `sourceJar` | The source JAR file to repackage | `${project.build.directory}/${project.build.finalName}.jar` | No |
| `outputDirectory` | The output directory for the repackaged JAR | `${project.build.directory}` | No |
| `finalName` | The final name of the repackaged JAR (without extension) | `${project.build.finalName}` | No |
| `backupSource` | Whether to backup the source JAR | `true` | No |

## How it works

1. The plugin takes your regular JAR file (containing just your application classes)
2. Uses Spring Boot's `Repackager` class to create the nested structure
3. Includes all compile and runtime dependencies as separate JARs in `BOOT-INF/lib/`
4. Adds Spring Boot's loader classes to handle the nested JAR loading
5. Sets up the manifest to use `JarLauncher` as the main class and your class as `Start-Class`

## Comparison with maven-shade-plugin

| Aspect | maven-shade-plugin | camel-repackager |
|--------|-------------------|------------------------------|
| JAR Structure | Single flat JAR with all classes | Nested JARs with Spring Boot loader |
| Startup Time | Slower (all classes loaded) | Faster (on-demand loading) |
| Memory Usage | Higher (all classes in memory) | Lower (only used classes loaded) |
| Classpath Conflicts | Possible (overlapping files) | Avoided (separate JARs) |
| Debugging | Harder (merged classes) | Easier (original JAR structure) |
| File Size | Smaller (no duplication) | Slightly larger (loader overhead) |

## Example

See the Camel JBang launcher (`dsl/camel-jbang/camel-launcher`) for a real-world example of how this plugin is used.
