# Embedded ActiveMQ Broker with Camel running in Apache Tomcat

### Introduction
This example shows how you can embed Apache ActiveMQ Broker and Camel in a web application, which can run on Apache Tomcat or other web containers.

This example embeds ActiveMQ Broker and a Camel application which will continuously send a message per second to an inbox queue.
Then another Camel route will route messages from the inbox to the outbox queue.

#### Camel component used in this example

* camel-core
* camel-jms
* camel-spring

### Build

You will need to build this example first:

	mvn install

### Run

Which will create a `.war` file in the target directly.

You can then deploy this `.war` file in any web container such as
Apache Tomcat, by copying the `.war` file to its `/webapp` directory.

This example embeds ActiveMQ Broker and a Camel application
which will continuously send a message per second to an inbox queue.
Then another Camel route will route messages from the inbox
to the outbox queue.

### Configuration

The ActiveMQ broker is configured in the `src/main/resources/broker.xml` file.
And the Camel application in the `src/main/resources/camel-context.xml` file.


### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
  <http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!


The Camel riders!
