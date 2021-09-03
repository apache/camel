Integration testing
===================

The camel-rabbitmq component has both unit tests and integration tests.

The integration tests requires docker, so that a RabbitMQ instance can be brought up by TestContainers. The execution
of such tests is done automatically by checking either the docker socket or the DOCKER_HOST variable.

It is also possible to run the tests using an embedded Qpid broker, for interoperability test. To run such tests,
execute maven with the `-Drabbitmq.instance.type=qpid`

```mvn -Drabbitmq.instance.type=qpid verify```
