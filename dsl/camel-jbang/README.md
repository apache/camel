## Install Camel-JBang

The general process to install Camel-JBang is described [here](https://camel.apache.org/manual/camel-jbang.html#_installation)

```shell
jbang app install camel@apache/camel
```

If you however like to install camel-jbang from this project build you create an alias to the local entry point.

```shell
jbang alias add --name camel -Dcamel.jbang.version=4.21.0-SNAPSHOT ./dsl/camel-jbang/camel-jbang-main/dist/CamelJBang.java

jbang camel version  
Camel CLI version: 4.21.0-SNAPSHOT
```

Alternatively, you can change the version in [`CamelJBang.java`](https://github.com/apache/camel/blob/main/dsl/camel-jbang/camel-jbang-main/src/main/jbang/main/CamelJBang.java#L22)

### Using Maven coordinates from the local repository

After building the project with `mvn install`, you can also run camel-jbang directly from the Maven local repository using Maven coordinates.

One-shot run:

```shell
jbang run -Drepos=mavenLocal,central \
    --main=main.CamelJBang \
    --deps=org.apache.camel:camel-jbang-core:4.21.0-SNAPSHOT \
    org.apache.camel:camel-jbang-main:4.21.0-SNAPSHOT \
    version
```

Or install it as a persistent command:

```shell
jbang app install --name camel \
    -Drepos=mavenLocal,central \
    --main=main.CamelJBang \
    --deps=org.apache.camel:camel-jbang-core:4.21.0-SNAPSHOT \
    org.apache.camel:camel-jbang-main:4.21.0-SNAPSHOT

camel version
```