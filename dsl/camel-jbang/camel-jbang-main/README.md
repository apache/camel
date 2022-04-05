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

## To run the AWS Secrets Manager example:

You need to first export the credentials as enviroment variables:

[source,sh]
----
export CAMEL_VAULT_AWS_REGION=<region>
export CAMEL_VAULT_AWS_SECRET_KEY=<secretKey>
export CAMEL_VAULT_AWS_ACCESS_KEY=<accessKey>
----

or you can specify them as properties in a separated file:

[source,sh]
----
camel.aws.vault.access.key=<accessKey>
camel.aws.vault.secret.key=<secretKey>
camel.aws.vault.region=<region>
----

Create the secret on AWS Secrets Manager. 

Login into your AWS Account and create a secret called "finnhub_token" with the Finnhub.io token value, you retrieved from finnhub_token.

If you're using the env properties approach:

Then you should be able to add

```
jbang --fresh -Dcamel.jbang.version=3.17.0-SNAPSHOT  camel@apache/camel run --modeline=true  ../examples/aws-secrets-manager-properties.yaml
```

If you're using the properties file approach:

Then you should be able to add

```
jbang --fresh -Dcamel.jbang.version=3.17.0-SNAPSHOT  camel@apache/camel run --modeline=true --properties=<path>  ../examples/aws-secrets-manager-properties.yaml
```

And you should see the following output:

```
[jbang] Resolving dependencies...
[jbang] Loading MavenCoordinate [org.apache.camel:camel-bom:pom:3.17.0-SNAPSHOT]
[jbang]     Resolving org.apache.camel:camel-jbang-core:3.17.0-SNAPSHOT...Done
[jbang]     Resolving org.apache.camel.kamelets:camel-kamelets:0.7.1...Done
[jbang]     Resolving org.apache.camel.kamelets:camel-kamelets-utils:0.7.1...Done
[jbang] Dependencies resolved
[jbang] Building jar...
A new lock file was created, delete the file to stop running:
/home/oscerd/workspace/apache-camel/camel/dsl/camel-jbang/camel-jbang-main/dist/./.run3788379828636362676.camel.lock
Starting CamelJBang
2022-02-15 16:49:07.108  INFO 31844 --- [           main] e.camel.impl.engine.AbstractCamelContext : Detected: camel-debug JAR (enabling Camel Debugging)
2022-02-15 16:49:07.272  INFO 31844 --- [           main] org.apache.camel.main.BaseMainSupport    : Auto-configuration summary
2022-02-15 16:49:07.273  INFO 31844 --- [           main] org.apache.camel.main.BaseMainSupport    :     camel.main.name=CamelJBang
2022-02-15 16:49:07.273  INFO 31844 --- [           main] org.apache.camel.main.BaseMainSupport    :     camel.main.shutdownTimeout=5
2022-02-15 16:49:07.273  INFO 31844 --- [           main] org.apache.camel.main.BaseMainSupport    :     camel.main.routesReloadEnabled=false
2022-02-15 16:49:07.273  INFO 31844 --- [           main] org.apache.camel.main.BaseMainSupport    :     camel.main.sourceLocationEnabled=true
2022-02-15 16:49:07.273  INFO 31844 --- [           main] org.apache.camel.main.BaseMainSupport    :     camel.main.tracing=false
2022-02-15 16:49:07.274  INFO 31844 --- [           main] org.apache.camel.main.BaseMainSupport    :     camel.main.modeline=true
2022-02-15 16:49:07.274  INFO 31844 --- [           main] org.apache.camel.main.BaseMainSupport    :     camel.main.routesIncludePattern=file:../examples/aws-secrets-manager-properties.yaml
2022-02-15 16:49:07.274  INFO 31844 --- [           main] org.apache.camel.main.BaseMainSupport    :     camel.component.kamelet.location=classpath:/kamelets,github:apache:camel-kamelets/kamelets
2022-02-15 16:49:08.359  INFO 31844 --- [           main] org.apache.camel.main.DownloaderHelper   : Downloaded dependency: org.apache.camel:camel-aws-secrets-manager:3.17.0-SNAPSHOT took: 1s14ms
2022-02-15 16:49:08.419  INFO 31844 --- [           main] e.camel.management.JmxManagementStrategy : JMX is enabled
2022-02-15 16:49:09.928  INFO 31844 --- [           main] e.camel.impl.engine.AbstractCamelContext : Routes startup (total:3 started:3)
2022-02-15 16:49:09.928  INFO 31844 --- [           main] e.camel.impl.engine.AbstractCamelContext :     Started route1 (kamelet://timer-source)
2022-02-15 16:49:09.928  INFO 31844 --- [           main] e.camel.impl.engine.AbstractCamelContext :     Started timer-source-1 (timer://tick)
2022-02-15 16:49:09.928  INFO 31844 --- [           main] e.camel.impl.engine.AbstractCamelContext :     Started log-sink-2 (kamelet://source)
2022-02-15 16:49:09.928  INFO 31844 --- [           main] e.camel.impl.engine.AbstractCamelContext : Apache Camel 3.17.0-SNAPSHOT (CamelJBang) started in 1s683ms (build:149ms init:1s368ms start:166ms)
2022-02-15 16:49:09.929  INFO 31844 --- [           main] ache.camel.impl.debugger.BacklogDebugger : Enabling Camel debugger
2022-02-15 16:49:11.417  INFO 31844 --- [ - timer://tick] info                                     : Exchange[ExchangePattern: InOnly, BodyType: org.apache.camel.converter.stream.CachedOutputStream.WrappedInputStream, Body: {"c":131.25,"d":1.1,"dp":0.8452,"h":131.68,"l":130.42,"o":130.64,"pc":130.15,"t":1644940124}]
2022-02-15 16:49:16.123  INFO 31844 --- [ - timer://tick] info                                     : Exchange[ExchangePattern: InOnly, BodyType: org.apache.camel.converter.stream.CachedOutputStream.WrappedInputStream, Body: {"c":131.25,"d":1.1,"dp":0.8452,"h":131.68,"l":130.42,"o":130.64,"pc":130.15,"t":1644940124}]
2022-02-15 16:49:21.139  INFO 31844 --- [ - timer://tick] info                                     : Exchange[ExchangePattern: InOnly, BodyType: org.apache.camel.converter.stream.CachedOutputStream.WrappedInputStream, Body: {"c":131.25,"d":1.1,"dp":0.8452,"h":131.68,"l":130.42,"o":130.64,"pc":130.15,"t":1644940124}]
^C2022-02-15 16:49:24.140  INFO 31844 --- [           main] e.camel.impl.engine.AbstractCamelContext : Apache Camel 3.17.0-SNAPSHOT (CamelJBang) shutting down (timeout:5s)
2022-02-15 16:49:24.154  INFO 31844 --- [           main] e.camel.impl.engine.AbstractCamelContext : Routes stopped (total:3 stopped:3)
2022-02-15 16:49:24.154  INFO 31844 --- [           main] e.camel.impl.engine.AbstractCamelContext :     Stopped log-sink-2 (kamelet://source)
2022-02-15 16:49:24.154  INFO 31844 --- [           main] e.camel.impl.engine.AbstractCamelContext :     Stopped timer-source-1 (timer://tick)
2022-02-15 16:49:24.154  INFO 31844 --- [           main] e.camel.impl.engine.AbstractCamelContext :     Stopped route1 (kamelet://timer-source)
2022-02-15 16:49:24.159  INFO 31844 --- [           main] ache.camel.impl.debugger.BacklogDebugger : Disabling Camel debugger
2022-02-15 16:49:24.161  INFO 31844 --- [           main] e.camel.impl.engine.AbstractCamelContext : Apache Camel 3.17.0-SNAPSHOT (CamelJBang) shutdown in 21ms (uptime:14s399ms)

```

## Development

### Handling Dependencies Versions

If needed for development and debugging purposes, dependencies can be referenced by correctly resolving the parameterized variables on the command line. Such as: 

```
jbang -Dcamel.jbang.version=3.17.0-SNAPSHOT CamelJBang.java
```

### Checkstyle

Because the first line of the script requires a she-bang like line, it violates the default checkstyle used by the 
Apache Camel project. As such, the checkstyle is skipped for this module. Nonetheless, the usual Apache Camel coding
should be observed and followed when contributing to this module.

If needed, the checkstyle plugin can be forcely run using the following command: 

```
mvn -Dcheckstyle.skip=false clean verify
```
