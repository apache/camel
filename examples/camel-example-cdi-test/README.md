# Testing Example - CDI

### Introduction

This example demonstrates the testing features that are provided as part of
the integration between Camel and CDI.

The example is implemented in Java with CDI dependency injection. It uses JBoss Weld
as the minimal CDI container to run the application, though you can run the application
in any CDI compliant container.

This example comes with a series of test classes that each demonstrates particular
features provided by the `camel-test-cdi` module: 

| Test class                  | Description                                               |
| --------------------------- | --------------------------------------------------------- |
| [`AdviceTest`][]            | Adds a test route using Camel advice API                  |
| [`AlternativeTest`][]       | Mocks a bean used in a Camel route with a CDI alternative |
| [`ApplicationScopedTest`][] | A stateful `@ApplicationScoped` test class                |
| [`CustomContextTest`][]     | Declares a custom Camel context bean for test purpose     |
| [`OrderTest`][]             | Orders the test methods execution with `@Order`           |

[`AdviceTest`]: src/test/java/org/apache/camel/example/cdi/test/AdviceTest.java
[`AlternativeTest`]: src/test/java/org/apache/camel/example/cdi/test/AlternativeTest.java
[`ApplicationScopedTest`]: src/test/java/org/apache/camel/example/cdi/test/ApplicationScopedTest.java
[`CustomContextTest`]: src/test/java/org/apache/camel/example/cdi/test/CustomContextTest.java
[`OrderTest`]: src/test/java/org/apache/camel/example/cdi/test/OrderTest.java

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

When the Camel application runs, you should see the following message being logged
to the console, e.g.:

```
2016-03-04 17:54:04,147 [cdi.Main.main()] INFO  route - Hello from camel-test-cdi
```

The Camel application can be stopped pressing <kbd>ctrl</kbd>+<kbd>c</kbd> in the shell.

You should see the following message being logged to the console:

```
2016-03-04 17:54:18,725 [Thread-1       ] INFO  route - Bye from camel-test-cdi
```

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may have. Enjoy!

The Camel riders!
