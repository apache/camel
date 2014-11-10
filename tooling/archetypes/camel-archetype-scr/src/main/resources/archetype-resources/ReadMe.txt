Camel SCR bundle Project
========================

To build this project use

    mvn install

To deploy this project in Red Hat JBoss Fuse 6.1

    JBossFuse:karaf@root> profile-edit --bundles mvn:example/camel-scr-example/1.0-SNAPSHOT my-test 1.0
    Adding bundle:mvn:example/camel-scr-example/1.0-SNAPSHOT to profile:my-test version:1.0

    From the container's karaf.log:

    2014-10-10 14:51:56,785 | INFO  | pool-49-thread-1 | OsgiDefaultCamelContext          | e.camel.impl.DefaultCamelContext 1533 | 122 - org.apache.camel.camel-core - 2.12.0.redhat-610387 | Apache Camel 2.12.0.redhat-610379 (CamelContext: camel-scr-example) is starting
    2014-10-10 14:51:56,786 | INFO  | pool-49-thread-1 | OsgiDefaultCamelContext          | e.camel.impl.DefaultCamelContext 1610 | 122 - org.apache.camel.camel-core - 2.12.0.redhat-610387 | MDC logging is enabled on CamelContext: camel-scr-example
    2014-10-10 14:51:56,786 | INFO  | pool-49-thread-1 | ManagedManagementStrategy        | gement.ManagedManagementStrategy  187 | 122 - org.apache.camel.camel-core - 2.12.0.redhat-610387 | JMX is enabled
    2014-10-10 14:51:56,856 | INFO  | pool-49-thread-1 | OsgiDefaultCamelContext          | e.camel.impl.DefaultCamelContext 2224 | 122 - org.apache.camel.camel-core - 2.12.0.redhat-610387 | Route: foo/timer-log started and consuming from: Endpoint[timer://foo?period=5000]
    2014-10-10 14:51:56,858 | INFO  | pool-49-thread-1 | OsgiDefaultCamelContext          | e.camel.impl.DefaultCamelContext 2224 | 122 - org.apache.camel.camel-core - 2.12.0.redhat-610387 | Route: foo/timer-log.completion started and consuming from: Endpoint[direct://processCompletion]
    2014-10-10 14:51:56,860 | INFO  | pool-49-thread-1 | OsgiDefaultCamelContext          | e.camel.impl.DefaultCamelContext 1568 | 122 - org.apache.camel.camel-core - 2.12.0.redhat-610387 | Total 2 routes, of which 2 is started.
    2014-10-10 14:51:56,860 | INFO  | pool-49-thread-1 | OsgiDefaultCamelContext          | e.camel.impl.DefaultCamelContext 1569 | 122 - org.apache.camel.camel-core - 2.12.0.redhat-610387 | Apache Camel 2.12.0.redhat-610379 (CamelContext: camel-scr-example) started in 0.075 seconds
    2014-10-10 14:51:57,872 | INFO  | 14 - timer://foo | foo                              | rg.apache.camel.util.CamelLogger  176 | 122 - org.apache.camel.camel-core - 2.12.0.redhat-610387 | Exchange[ExchangePattern: InOnly, Headers: {breadcrumbId=ID-12345-58358-1412941739290-5-1}, BodyType: null, Body: [Body is null]]
    2014-10-10 14:51:57,881 | INFO  | 6 - OnCompletion | completion                       | rg.apache.camel.util.CamelLogger  176 | 122 - org.apache.camel.camel-core - 2.12.0.redhat-610387 | Success: timer:foo?period=5000 -> log:foo?showHeaders=true

For more help see the Apache Camel documentation

    http://camel.apache.org/
