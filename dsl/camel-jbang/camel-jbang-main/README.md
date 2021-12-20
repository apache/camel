# Camel JBang

A JBang-based Camel app for running Kamelets

## Usage

```
./CamelJBang.java run path/to/route-binding.yaml
```

To show the help:

```
./CamelJBang.java --help
```

After launching, the scripts will run indefinitely. If you want to stop them, you can either use Ctrl + C or remove the
run locks:

```
rm .run*.lock
```

## Running the other examples

To run the Earthquake example:

```
./CamelJBang.java run examples/earthquake.yaml
```

To run the JMS examples, you will need an instance of the Apache Artemis broker. If you don't have one available, you can build a container from the image used in Camel tests:

```
docker build -f ../../test-infra/camel-test-infra-artemis/src/test/resources/org/apache/camel/test/infra/artemis/services/Dockerfile --build-arg FROMIMAGE=fedora:33 -t apache-artemis:latest .
```

Then you can launch the container using:


```
docker run --rm -p 61616:61616 -p 5672:5672 -p 8161:8161 apache-artemis:latest
```

And, lastly, to run the examples, first open a terminal to run the source binding:

```
./CamelJBang.java run examples/jms-apache-artemis-source-binding.yaml
```

Then, open another one to run the sink binding:

```
./CamelJBang.java run examples/jms-amqp-10-sink-binding.yaml
```

The Kafka example follow the same pattern, but you will need a Kafka broker instance. There are several container images 
available for Kafka, such as the Confluent one and the Strimzi project one.


## Development

### Handling Dependencies Versions

If needed for development and debugging purposes, dependencies can be referenced by correctly resolving the parameterized variables on the command line. Such as: 

```
jbang -Dcamel.jbang.version=3.15.0-SNAPSHOT CamelJBang.java
```

### Checkstyle

Because the first line of the script requires a she-bang like line, it violates the default checkstyle used by the 
Apache Camel project. As such, the checkstyle is skipped for this module. Nonetheless, the usual Apache Camel coding
should be observed and followed when contributing to this module.

If needed, the checkstyle plugin can be forcely run using the following command: 

```
mvn -Dcheckstyle.skip=false clean verify
```
