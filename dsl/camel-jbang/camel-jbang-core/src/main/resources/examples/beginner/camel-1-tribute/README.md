## Camel 1.0 Tribute - JMS to File

A tribute to the very first Apache Camel example ever written.

When Apache Camel 1.0 was released in June 2007, the project shipped with just two examples.
The first and most iconic was `camel-example-jms-file` - a simple route that consumed messages
from a JMS queue and saved them to the file system.

Back then, Camel was still part of the Apache ActiveMQ project. The README was signed
_"The Apache ActiveMQ team"_, the website lived at `activemq.apache.org/camel`,
and classes like `CamelTemplate` (later renamed to `ProducerTemplate`) were brand new.

The original example looked like this:

```java
CamelContext context = new DefaultCamelContext();

ConnectionFactory connectionFactory =
    new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
context.addComponent("test-jms",
    JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

context.addRoutes(new RouteBuilder() {
    public void configure() {
        from("test-jms:queue:test.queue").to("file://test");
    }
});

CamelTemplate template = new CamelTemplate(context);
context.start();

for (int i = 0; i < 10; i++) {
    template.sendBody("test-jms:queue:test.queue", "Test Message: " + i);
}
```

That was 40+ lines of Java, a Maven project, ActiveMQ embedded in-process,
and manual component wiring.

Today, the same example is a single YAML file and one command.

### Running the example

Start an Apache ActiveMQ Artemis broker:

```sh
$ camel infra run artemis
```

Or with Docker manually:

```sh
$ docker run --detach --name mycontainer -p 61616:61616 -p 8161:8161 --rm apache/activemq-artemis:latest-alpine
```

Then run the example:

```sh
$ camel run *
```

Camel will send 10 test messages to the `test.queue` JMS queue (just like the original)
and consume them back, saving each message as a file in the `test` directory.

### What changed in 19 years

| | Camel 1.0 (2007) | Camel CLI (today) |
|---|---|---|
| **Language** | Java (40+ lines) | YAML (25 lines) |
| **Build** | Maven project with pom.xml | No build needed |
| **Broker** | Embedded ActiveMQ (in-process) | Apache ActiveMQ Artemis (container) |
| **Component setup** | Manual `ConnectionFactory` wiring | Auto-configured via properties |
| **Run command** | `mvn camel:run` | `camel run *` |
| **Dependencies** | Declared in pom.xml | Auto-downloaded |

What stayed the same: `from("jms:queue:test.queue").to("file://test")` - the core routing idea
that made Camel what it is today.

### Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
