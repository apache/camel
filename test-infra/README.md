# Test Infrastructure

## Simulating the Test Infrastructure

One of the first steps when implementing a new test, is to identify how to simulate infrastructure required for it to 
run. The test-infra module provides a reusable library of infrastructure that can be used for that purpose. 

In general, the integration test leverages the features provided by the project [TestContainers](https://www.testcontainers.org/)
and uses container images to simulate the environments. Additionally, it may also support running the tests against remote 
environments as well as, when available, embeddable components. This varies by each component, and it is recommended to 
check the code for additional details.

## Implementing New Test Infrastructure Module

The test code abstracts the provisioning of test environments behind service classes (i.e.: JMSService, JDBCService,
etc). The purpose of the service class is to abstract the both the type service (i.e.: Kafka, Strimzi, etc) and
the location of the service (i.e.: remote, local, embedded, etc). This provides flexibility to test the code under 
different circumstances (i.e.: using a remote JMS broker or using a local JMS broker running in a container managed by
TestContainers). It makes it easier to hit edge cases as well as try different operating scenarios (i.e.: higher
latency, slow backends, etc).

JUnit 5 manages the lifecycle of the services, therefore each service must be a JUnit 5 compliant extension. The exact
extension point that a service must extend is specific to each service. The JUnit 5
[documentation](https://junit.org/junit5/docs/current/user-guide/) is the reference for the extension points.

When a container image is not available via TestContainers, tests can provide their own implementation using officially
available images. The license must be compatible with Apache 2.0. If an official image is not available, a Dockerfile
to build the service can be provided. The Dockerfile should try to minimize the container size and resource usage
whenever possible.

It is also possible to use embeddable components when required, although this usually lead to more code and higher 
maintenance.

### Recommended Structure for Test Infrastructure Module 

The service should provide an interface, named after the infrastructure being implemented, and this interface should 
extend the [TestService](./camel-test-infra-common/src/test/java/org/apache/camel/test/infra/common/services/TestService.java) 
interface. The services should try to minimize the test execution time and resource usage when running. As such,
the [BeforeAllCallback](https://junit.org/junit5/docs/5.1.1/api/org/junit/jupiter/api/extension/BeforeAllCallback.html)
and [AfterAllCallback](https://junit.org/junit5/docs/5.1.1/api/org/junit/jupiter/api/extension/AfterAllCallback.html)
should be the preferred extensions whenever possible because they allow the instance of the infrastructure to be static
throughout the test execution.

*Note*: bear in mind that, according to the [JUnit 5 extension](https://junit.org/junit5/docs/5.1.1/api/org/junit/jupiter/api/extension/RegisterExtension.html) 
model, the time of initialization of the service may differ depending on whether the service instance is declared as 
static or not in the test class. As such, the code should make no assumptions as to its time of initialization.

Ideally, there should be two concrete implementations of the services: one of the remote service (if applicable) and 
another for the container service: 

```
              MyService
                 / \
                /   \
               /     \
 MyRemoteService    MyContainerService
```
                     

In most cases a specialized service factory class is responsible for creating the service according to runtime
parameters and/or other test scenarios constraints. When a service allows different service types or locations to be
selected, this should be done via command line properties (`-D<property.name>=<value>`). For example, when allowing a
service to choose between running as a local container or as remote instance, a property in the format
`<name>.instance.type` should be handled. Additional runtime parameters used in different scenarios, should be handled
as `<name>.<parameter>`. More complex services may use the builder available through the factory classes to compose 
the service accordingly.


### Registering Properties

All services should register the properties, via `System.setProperty` that allow access to the services. This is required
in order to resolve those properties when running tests using the Spring framework. This registration allows the properties
to be resolved in Spring's XML files. 

This registration is done in the `registerProperties` methods during the service initialization. 

### Registering Properties Example:

Registering the properties in the concrete service implementation: 

```
    public void registerProperties() {
        // MyServiceProperties.MY_SERVICE_HOST is a string with value "my.service.host"
        System.setProperty(MyServiceProperties.MY_SERVICE_HOST, container.getHost());
        
        // MyServiceProperties.MY_SERVICE_PORT is a string with value "my.service.port"
        System.setProperty(MyServiceProperties.MY_SERVICE_PORT, String.valueOf(container.getServicePort()));
        
        // MyServiceProperties.MY_SERVICE_ADDRESS is a string with value "my.service.address"
        System.setProperty(MyServiceProperties.MY_SERVICE_ADDRESS, getServiceAddress());
    }
    
    public void initialize() {
        LOG.info("Trying to start the MyService container");
        container.start();

        registerProperties();
        LOG.info("MyService instance running at {}", getServiceAddress());
    }
```

Then, when referring these properties in Camel routes or Spring XML properties, you may use ```{{my.service.host}}```,
```{{my.service.port}}``` and ```{{my.service.address}}```.


### Packaging Recommendations

This is test infrastructure code, therefore it should be package as test type artifacts. The 
[parent pom](./camel-test-infra-parent) should provide all the necessary bits for packaging the test infrastructure.

## Using The New Test Infrastructure

Using the test infra in a new component test is rather straightforward similar to using any other reusable component. 
You start by declaring the test infra dependencies in your pom file. 

This should be similar to:

```
<!-- test infra -->
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-test-infra-common</artifactId>
    <version>${project.version}</version>
    <type>test-jar</type>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-test-infra-myservice</artifactId>
    <version>${project.version}</version>
    <type>test-jar</type>
    <scope>test</scope>
</dependency>
```

*Note*: on the dependencies above, the dependency version is set to `${project.version}`. This should be adjusted to the
Camel version when used outside the Camel Core project.

On the test class, add a member variable for the service and annotate it with the [@RegisterExtension](https://junit.org/junit5/docs/5.1.1/api/org/junit/jupiter/api/extension/RegisterExtension.html), 
in order to let JUnit 5 manage its lifecycle. 

```
@RegisterExtension
static MyService service = MyServiceServiceFactory.createService();
```

More complex test services can be created using something similar to: 

```
@RegisterExtension
static MyService service = MyServiceServiceFactory
    .builder()
        .addRemoveMapping(MyTestClass::myCustomRemoteService) // this is rarely needed
        .addLocalMapping(MyTestClass::staticMethodReturningAService) // sets the handler for -Dmy-service.instance.type=local-myservice-local-container
        .addMapping("local-alternative-service", MyTestClass::anotherMethodReturningAService) // sets the handler for -Dmy-service.instance.type=local-alternative-service
    .createService();
```

You can use the methods as well as the registered properties to access the test infrastructure services available. 
When using these properties in Spring XML files, you may use those properties. 

```
<someSpringXmlElement httpHost="{{my.service.host}}" port="{{my.service.port}}" />
```

It's also possible to use these properties in the test code itself. For example, when setting up the test url for the
Camel component:

```
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:put")
                    .to("mycomponent:someoption?host={{my.service.host}}&port={{my.service.port}}");
            }
        };
    }
```


## Converting Camel TestContainers Code To The New Test Infrastructure

Using the camel-nats as an example, we can compare how the base test class for nats changed between [3.6.x](https://github.com/apache/camel/blob/camel-3.6.0/components/camel-nats/src/test/java/org/apache/camel/component/nats/NatsTestSupport.java)
and [3.7.x](https://github.com/apache/camel/blob/camel-3.7.0/components/camel-nats/src/test/java/org/apache/camel/component/nats/NatsTestSupport.java).

The first conversion step is to remove the [camel-testcontainer dependencies](https://github.com/apache/camel/blob/camel-3.6.0/components/camel-nats/pom.xml#L59-L63)
and replace them with the ones from the [test-infra module](https://github.com/apache/camel/blob/camel-3.7.0/components/camel-nats/pom.xml#L61-L75).
Then, it's necessary to replace the [container handling code and the old base class](https://github.com/apache/camel/blob/camel-3.6.0/components/camel-nats/src/test/java/org/apache/camel/component/nats/NatsTestSupport.java#L24-L45)
with the [service provided in the module](https://github.com/apache/camel/blob/camel-3.7.0/components/camel-nats/src/test/java/org/apache/camel/component/nats/NatsTestSupport.java#L26-L27).  
Then, we replace the base class. The `ContainerAwareTestSupport` class and other similar classes from other 
`camel-testcontainer` modules are not necessary and can be replaced with `CamelTestSupport` or the spring based one 
`CamelSpringTestSupport`.

With the base changes in place, the next step is to make sure that addresses (URLs, hostnames, ports, etc) and 
resources (usernames, passwords, tokens, etc) referenced during the test execution, use the test-infra services. This
may differ according to each service. Replacing the call to get the [service URL](https://github.com/apache/camel/blob/camel-3.6.0/components/camel-nats/src/test/java/org/apache/camel/component/nats/NatsAuthConsumerLoadTest.java#L38)
with the one provided by the new [test infra service](https://github.com/apache/camel/blob/camel-3.7.0/components/camel-nats/src/test/java/org/apache/camel/component/nats/NatsAuthConsumerLoadTest.java#L38)
is a good example of this type of changes that may be necessary.

In some cases, it may be necessary to adjust the variables used in [simple language](https://github.com/apache/camel/blob/camel-3.6.0/components/camel-consul/src/test/resources/org/apache/camel/component/consul/cloud/SpringConsulRibbonServiceCallRouteTest.xml#L36)
so that they match the [new property format](https://github.com/apache/camel/blob/camel-3.7.0/components/camel-consul/src/test/resources/org/apache/camel/component/consul/cloud/SpringConsulRibbonServiceCallRouteTest.xml#L36)
used in the test infra service.


There are some cases where the container instance requires [extra customization](https://github.com/apache/camel/blob/camel-3.6.0/components/camel-pg-replication-slot/src/test/java/org/apache/camel/component/pg/replication/slot/integration/PgReplicationTestSupport.java#L31).
Nonetheless, the migrated code still benefits from the [test-infra approach](https://github.com/apache/camel/blob/camel-3.7.0/components/camel-pg-replication-slot/src/test/java/org/apache/camel/component/pg/replication/slot/integration/PgReplicationTestSupport.java#L31),
but this may be very specific to the test scenario.