## Install Camel-JBang

The general process to install Camel-JBang is described [here](https://camel.apache.org/manual/camel-jbang.html#_installation)

```shell
jbang app install camel@apache/camel
```

If you however like to install camel-jbang from this project build you create an alias to the local entry point.

```shell
jbang alias add --name camel -Dcamel.jbang.version=4.8.3-SNAPSHOT ./dsl/camel-jbang/camel-jbang-main/dist/CamelJBang.java

jbang camel version  
Camel JBang version: 4.8.3-SNAPSHOT
```

Alternatively, you can change the version in [`CamelJBang.java`](https://github.com/apache/camel/blob/main/dsl/camel-jbang/camel-jbang-main/src/main/jbang/main/CamelJBang.java#L22)