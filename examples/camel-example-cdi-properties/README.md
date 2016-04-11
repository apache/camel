# Properties Example - CDI

### Introduction

This example illustrates the integration between Camel, DeltaSpike and CDI
for configuration properties.

The example uses DeltaSpike to source configuration properties and creates
a `PropertiesComponent` bean that Camel uses to resolve property placeholders
in endpoint URIs. Besides, the application uses DeltaSpike's `@ConfigProperty`
qualifier to directly inject configuration property values. More information
can be found in [DeltaSpike configuration mechanism][] documentation.

The example is implemented in Java with CDI dependency injection.
It uses JBoss Weld as the minimal CDI container to run the application,
though you can run the application in any CDI compliant container.

The `camel-cdi` and `camel-core` components are used in this example.
The `camel-test-cdi` module is used for the JUnit test runner.

[DeltaSpike configuration mechanism]: http://deltaspike.apache.org/documentation/configuration.html

### Build

You can build this example using:

```sh
$ mvn package
```

### Run

You can run this example using:

```sh
$ mvn camel:run
```

When the Camel application runs, you should see the following messages
being logged to the console, e.g.:
```
2016-01-28 15:02:46,223 [cdi.Main.main()] INFO  CdiCamelExtension   - Camel CDI is starting Camel context [hello]
2016-01-28 15:02:46,223 [cdi.Main.main()] INFO  DefaultCamelContext - Apache Camel 2.17.0 (CamelContext: hello) is starting
...
2016-01-28 15:02:46,460 [cdi.Main.main()] INFO  DefaultCamelContext - Route: route1 started and consuming from: Endpoint[direct://hello]
2016-01-28 15:02:46,461 [cdi.Main.main()] INFO  DefaultCamelContext - Total 1 routes, of which 1 is started.
2016-01-28 15:02:46,461 [cdi.Main.main()] INFO  DefaultCamelContext - Apache Camel 2.17.0 (CamelContext: hello) started in 0.238 seconds
2016-01-28 15:02:46,661 [cdi.Main.main()] INFO  route1              - Hello from CamelContext (hello)
```

The Camel application can be stopped pressing <kbd>ctrl</kbd>+<kbd>c</kbd> in the shell.

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may have. Enjoy!

The Camel riders!
