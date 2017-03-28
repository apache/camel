# REST DSL / Servlet Example - CDI

### Introduction

This example illustrates the Camel REST DSL being used in a Java application
that uses CDI as dependency injection framework.

The example uses the [`camel-servlet`][Servlet component] component
as the underlying HTTP engine to service REST APIs defined with the Camel
REST DSL.

This example uses JBoss Weld as the minimal CDI container to run the application,
and is deployed in Jetty as Servlet engine, though you can run the application
in any CDI compliant container and Servlet container.

[Servlet component]: http://camel.apache.org/servlet.html

### Build

You can build this example using:

```sh
$ mvn package
```

### Run

You can run this example using:

```sh
$ mvn jetty:run
```

When the Camel application runs, you should see the following messages
being logged to the console, e.g.:
```
2016-01-29 18:52:57,591 [main           ] INFO  servletWeldServlet        - WELD-ENV-001008: Initialize Weld using ServletContainerInitializer
2016-01-29 18:52:58,313 [main           ] INFO  servletJetty              - WELD-ENV-001200: Jetty 7.2+ detected, CDI injection will be available in Servlets and Filters. Injection into Listeners should work on Jetty 9.1.1 and newer.
2016-01-29 18:52:58,885 [main           ] INFO  CdiCamelExtension         - Camel CDI is starting Camel context [hello]
2016-01-29 18:52:58,886 [main           ] INFO  DefaultCamelContext       - Apache Camel 2.17.0 (CamelContext: hello) is starting
2016-01-29 18:52:59,237 [main           ] INFO  DefaultCamelContext       - Route: route1 started and consuming from: Endpoint[servlet:/say/hello?httpMethodRestrict=GET]
2016-01-29 18:52:59,242 [main           ] INFO  DefaultCamelContext       - Route: route2 started and consuming from: Endpoint[servlet:/say/hello/%7Bname%7D?httpMethodRestrict=GET]
2016-01-29 18:52:59,242 [main           ] INFO  DefaultCamelContext       - Total 2 routes, of which 2 is started.
2016-01-29 18:52:59,243 [main           ] INFO  DefaultCamelContext       - Apache Camel 2.17.0 (CamelContext: hello) started in 0.357 seconds
2016-01-29 18:52:59,439 [main           ] INFO  servletWeldServlet        - WELD-ENV-001006: org.jboss.weld.environment.servlet.EnhancedListener used for ServletContext notifications
2016-01-29 18:52:59,439 [main           ] INFO  servletWeldServlet        - WELD-ENV-001009: org.jboss.weld.environment.servlet.Listener used for ServletRequest and HttpSession notifications
2016-01-29 18:52:59,471 [main           ] INFO  CamelHttpTransportServlet - Initialized CamelHttpTransportServlet[name=CamelServlet, contextPath=]
[INFO] Started ServerConnector@63f7f62{HTTP/1.1}{0.0.0.0:8080}
[INFO] Started @12857ms
[INFO] Started Jetty Server
```

Then you can open the following URL into your Web browser, <http://localhost:8080/camel/say/hello/>, and
being responded with `Hello World!`.

Otherwise, in a separate prompt, by running:

```
curl http://localhost:8080/camel/say/hello/Antonin
```

You should being responded with the following message:

```
Hello Antonin, I'm CamelContext(hello)!
```

And see the following message being logged by the Camel application:

```
016-01-29 19:03:20,293 [tp1211352799-18] INFO  route2 - Hello Antonin, I'm CamelContext(hello)!
```

The Camel application can be stopped pressing <kbd>ctrl</kbd>+<kbd>c</kbd> in the shell.

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may have. Enjoy!

The Camel riders!
