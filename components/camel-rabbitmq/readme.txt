Integration testing
===================

The camel-rabbitmq component has both unit tests and integration tests.

The integration tests requires a running RabbitMQ broker.
All integration tests run docker container with RabbitMQ automatically.

The integration tests with Qpid could be run via Maven (disabled by default):
    mvn test -P qpid-itest

The broker can be run via Docker:

    docker run -it -p 5672:5672 -p 15672:15672 -e RABBITMQ_DEFAULT_USER=cameltest -e RABBITMQ_DEFAULT_PASS=cameltest --hostname my-rabbit --name some-rabbit rabbitmq:3-management

Or to install RabbitMQ as standalone and then configure it:

    rabbitmq-server
    rabbitmqctl add_user cameltest cameltest
    rabbitmqctl set_permissions -p / cameltest ".*" ".*" ".*"