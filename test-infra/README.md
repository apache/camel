# Test Infrastructure

## Simulating the Test Infrastructure

One of the first steps when implementing a new test, is to identify how to simulate infrastructure required for it to 
run. The test-infra module provides a reusable library of infrastructure that can be used for that purpose. 

In general, the integration test leverages the features provided by the project [TestContainers](https://www.testcontainers.org/)
and uses container images to simulate the environments. Additionally, it may also support running the tests against remote 
environments as well as, when available, embeddable components. This varies by each component and it is recommended to 
check the code for additional details.

## Implementing new Test Infra

The test code abstracts the provisioning of test environments behind service classes (i.e.: JMSService, JDBCService,
etc). The purpose of the service class is to abstract the both the type service (i.e.: Kafka, Strimzi, etc) and
the location of the service (i.e.: remote, local, embedded, etc). This provides flexibility to test the code under 
different circumstances (ie.: using a remote JMS broker or using a local JMS broker running in a container managed by
TestContainers). It makes it easier to hit edge cases as well as try different operating scenarios (ie.: higher
latency, slow backends, etc).

JUnit 5 manages the lifecycle of the services, therefore each service must be a JUnit 5 compliant extension. The exact
extension point that a service must extend is specific to each service. The JUnit 5
[documentation](https://junit.org/junit5/docs/current/user-guide/) is the reference for the extension points.

In general, the services should aim to minimize the test execution time and resource usage when running. As such,
the [BeforeAllCallback](https://junit.org/junit5/docs/5.1.1/api/org/junit/jupiter/api/extension/BeforeAllCallback.html)
and [AfterAllCallback](https://junit.org/junit5/docs/5.1.1/api/org/junit/jupiter/api/extension/AfterAllCallback.html)
should be the preferred extensions whenever possible because they allow the instance of the infrastructure to be static
throughout the test execution.

In most cases a specialized service factory class is responsible for creating the service according to runtime 
parameters and/or other test scenarios constraints. When a service allows different service types or locations to be 
selected, this should be done via command line properties (`-D<property.name>=<value>`). For example, when allowing a 
service to choose between running as a local container or as remote instance, a property in the format 
`<name>.instance.type` should be handled. Additional runtime parameters used in different scenarios, should be handled
as `<name>.<parameter>`. More complex services may use the builder pattern to compose the service accordingly. 


When a container image is not available via TestContainers, tests can provide their own implementation using officially
available images. The license must be compatible with Apache 2.0. If an official image is not available, a Dockerfile
to build the service can be provided. The Dockerfile should try to minimize the container size and resource usage
whenever possible.

It is also possible to use embeddable components when required, although this usually lead to more code and higher 
maintenance.
