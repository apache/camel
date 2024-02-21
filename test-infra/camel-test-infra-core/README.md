# Converting Projects

## Converting projects that manage the CamelContext directly

This section describe how to convert projects that create and manage a `CamelContext` directly (i.e.; not relying on `CamelTestSupport`).

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
