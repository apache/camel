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

This module aims to be both simple and practical, as such, there are a few recommendations to follow when contributing 
to the code on
this particular module. 

The module leverages JBang's ability to [parameterize dependencies](https://www.jbang.dev/documentation/guide/latest/dependencies.html#system-properties-and-environment-variables) along with Antrun's
ability to process source code on the fly and perform string substitution to replace strings on files. 

During the build, the *perfectly valid* Java-based JBang script code is copied to the `dist` directory. Then the antrun 
plugin replaces the references to parameterized dependencies. The parameterized dependencies are in the format `${name}`. 

In order to minimize the problems caused by CVEs in dependencies and ensure the correct alignment of dependencies within 
Camel, all references to dependencies should use parameterized ones. Such as: 

```
//DEPS org.apache.camel:camel-bom:${camel.jbang.version}@pom
```

After being processed by antrun, during the build, the aforementioned line would be transformed to something like (for 
a Camel 3.12.0-SNAPSHOT build):

```
//DEPS org.apache.camel:camel-bom:3.12.0-SNAPSHOT@pom
```

The same applies to Camel version references, which should rely on the `${camel.jbang.version}` parameter (such 
as when displaying help information).

Despite this transformation, the code in `src/main/jbang` should be valid nonetheless and can be executed for development 
and debugging purposes by correctly resolving the parameterized variables on the command line. Such as: 

```
jbang -Dcamel.jbang.version=3.12.0-SNAPSHOT -Dcamel.jbang.log4j2.version=2.13.3 -Dcamel.jbang.picocli.version=4.5.0 CamelJBang.java
```

Alternatively, it is possible to just build the module and then execute the post-processed script on the `dist` directory.

```mvn clean package && ./dist/Camel```

### Checkstyle

Because the first line of the script requires a she-bang like line, it violates the default checkstyle used by the 
Apache Camel project. As such, the checkstyle is skipped for this module. Nonetheless, the usual Apache Camel coding
should be observed and followed when contributing to this module.

If needed, the checkstyle plugin can be forcely run using the following command: 

```
mvn -Dcheckstyle.skip=false clean verify
```
