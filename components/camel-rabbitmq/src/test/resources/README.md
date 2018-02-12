How to configure RabbitMQ server for integration tests
-----------------------
rabbitmq-server
rabbitmqctl add_user cameltest cameltest
rabbitmqctl set_permissions -p / cameltest ".*" ".*" ".*"