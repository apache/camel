Integration testing
===================

The camel-rabbitmq component has both unit tests and integration tests.

The integration tests requires a running RabbitMQ broker.

The broker can be run via Docker:

    docker run -it -p 5672:5672 -e RABBITMQ_DEFAULT_USER=cameltest -e RABBITMQ_DEFAULT_PASS=cameltest --hostname my-rabbit --name some-rabbit rabbitmq:3

Or to install RabbitMQ as standalone and then configure it:

    rabbitmq-server
    rabbitmqctl add_user cameltest cameltest
    rabbitmqctl set_permissions -p / cameltest ".*" ".*" ".*"

The integration tests can be run via Maven:

    mvn test -P itest

