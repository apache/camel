# AWS SQS component for SDK v2

# Running the tests

This component contains integration tests that can be executed against a LocalStack instance, or an actual AWS 
instance. The build determines the execution or not of the integration tests automatically by checking the `DOCKER_HOST`
environment variable or by the presence of the local unix socket. If either of the conditions is true , then the build 
will try to execute the integration test. 

*Note*: the `DOCKER_HOST` variable is usually the address of the local unix socket `unix:///var/run/docker.sock`. 

It is possible to run the tests on a remote docker server by overwriting the value of the DOCKER_HOST variable:


```
DOCKER_HOST=tcp://myhost:2375 mvn clean verify
```

# Running the tests against AWS

You can define the `aws-service.instance.type`, `aws.access.key` and `aws.secret.key` to switch the test execution from
using LocalStack and, instead, using AWS:

```
mvn -Paws2-sqs-tests-docker-env -Daws-service.instance.type=remote -Daws.access.key=you-access-key -Daws.secret.key=you-secret-key clean test verify
``` 
