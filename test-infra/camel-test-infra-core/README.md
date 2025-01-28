# Converting Projects

## Working with the Camel Context Infra

One of the infrastructure components provided by test infra is a JUnit 5 extension that injects a Camel context into the tests.
This extension is called `camel-test-infra-core`.
It is an internal interface, meant to be used only by Camel itself, for very
specific test scenarios.

When testing Camel or a Camel-based integration, you almost certainly need to use the `CamelContext` to configure the registry, add routes and execute other operations. The test infra comes with a module that provides a JUnit 5 extension that allows you to inject a Camel context into your tests.

Adding it to your test code is as simple as adding the following lines of code to your test class:

```java
@RegisterExtension
protected static CamelContextExtension contextExtension = new DefaultCamelContextExtension();
```

Then, via the extension, you can access the context (i.e.,: `contextExtension.getContext()`) to manipulate it as needed in the tests.

The extension comes with a few utilities to simplify configuring the context, and adding routes at the appropriate time.

### Configuring the Camel Context

To create a method that configures the context,
you can declare a method receiving an instance of `CamelContext` and annotate it with `@ContextFixture`.

```java
@ContextFixture
public void configureContext(CamelContext context) {
    // context configuration code here
}
```

Additionally, you can simplify the class hierarchy,
and ensure consistency you may also implement the `ConfigurableContext` interface.


### Configuring the Routes

You can configure the routes using a similar process as the one described for configuring the Camel context. You can create a method that receives an instance of `CamelContext` and annotate it with `@RouteFixture`.

```java
@RouteFixture
public void createRouteBuilder(CamelContext context) throws Exception {
    context.addRoutes(new RouteBuilder() {
        @Override
        public void configure() {
            from(fromUri).to(destUri);
        }
    });
}
```

### Using the Camel Context Extension

To start using the Camel Context extension on your code, add the following dependency:

```xml
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-test-infra-core</artifactId>
    <version>${camel.version}</version>
    <scope>test</scope>
    <type>test-jar</type>
</dependency>
```

For simplicity and consistency, you may also declare the route as implementing the `ConfigurableRoute`.


## Converting projects that manage the CamelContext directly

This section describes how to convert projects that create and manage a `CamelContext` directly (i.e.; not relying on `CamelTestSupport`).

1. Add the dependency that brings the CamelContext JUnit 5 extension

```xml
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-test-infra-core</artifactId>
    <version>${project.version}</version>
    <type>test-jar</type>
    <scope>test</scope>
</dependency>
```

2. Add the extension as a member variable to the test case:

```java
@RegisterExtension
private static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
```

**Tips**: when running multiple tests in the same class, it may be necessary to completed trash the context instance. In this case, create an instance of `TransientCamelContextExtension` and JUnit will properly dispose the instance and create a new one. 


3. If necessary, add a private variable for the camel context and assign it during setup:

```java
private CamelContext context;

@BeforeEach
void setupTest() throws Exception {
    context = camelContextExtension.getContext();
}
```

4. Routes can be configured by creating a public method annotated with the `@RouteFixture` annotation:

```java
@RouteFixture
public void setupRoute(CamelContext camelContext) throws Exception {
    camelContext.addRoutes(new RouteBuilder() {
        @Override
        public void configure() {
            restConfiguration()
                .host("localhost")
                .component("dummy-rest");

            from("direct:foo")
                .routeId("foo")
                .to("mock:foo");
        	}
   	});
}
```

5. The context can be configured by creating a `public` method annotated with the `@ContextFixture` annotation: 

```java
@ContextFixture
public void setupContext(CamelContext camelContext) throws Exception{
    // configure the context
}
```


## Converting projects that use `CamelTestSupport`

### Easy way

1. Add the dependency that brings the CamelContext JUnit 5 extension

```xml
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-test-infra-core</artifactId>
    <version>${project.version}</version>
    <type>test-jar</type>
    <scope>test</scope>
</dependency>
```

2. Replace the inheritance from `CamelTestSupport` with the implementation of support interfaces from `CamelTestSupportHelper`, `ConfigurableRoute` and `ConfigurableContext`. These brings several helper methods from CamelTestSupport and simulate the legacy behavior. 

```java
public class MyTest implements ConfigurableRoute, CamelTestSupportHelper {
   // ...
}
```

2. Add the extension as a member variable to the test case:

```java
@RegisterExtension
public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
```

**Tips**: when running multiple tests in the same class, it may be necessary to completed trash the context instance. In this case, create an instance of `TransientCamelContextExtension` and JUnit will properly dispose the instance and create a new one.

3. (Optional) If using other test-infra services, adjust the startup ordering, so that it reflects the expected order in which services should be initialized: 

```java
@Order(1)
@RegisterExtension
public static MyLocalContainerService service = new MyLocalContainerService();

@Order(2)
@RegisterExtension
public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
```


4. Create the route configuration fixture. Previously that used to be done in a method `createRouteBuilder`, so just create a new method that calls that old method, but, make sure to annotated it with the `@RouteFixture` annotation: 

```java
@Override
@RouteFixture
public void createRouteBuilder(CamelContext context) throws Exception {
    final RouteBuilder routeBuilder = createRouteBuilder();

    if (routeBuilder != null) {
        context.addRoutes(routeBuilder);
    }
}
```

5. If necessary, the context can be configured by creating a `public` method annotated with the `@ContextFixture` annotation:

```java
@ContextFixture
public void setupContext(CamelContext camelContext) throws Exception {
    // configure the context
}
```
