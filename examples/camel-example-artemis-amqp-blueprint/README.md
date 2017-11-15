# Camel Artemis AMQP Messaging

### Introduction

This example shows how to produce/consume messages between Apache Camel and ActiveMQ Artemis using the AMQP component based on the JMS 2.0 Apache Qpid Proton client library.

One benefit of using Camel's AMQP component is to allow connectivity to Qpid's Dispatch Router enabling flexible and scalable interconnect between any AMQP endpoints.

The example includes a JUnit to showcase how to embed an AMQP enabled Artemis Broker. This permits building and testing the code in complete isolation.

When deployed in a running environment, Camel will attempt to connect to an already existing Artemis broker using the default AMQP port (5672).

### Test with embedded Artemis

The JUnit with an included embedded Artemis Broker can be triggered using Maven.

    mvn test

### Install ActiveMQ Artemis

You download and unzip Apache ActiveMQ Artemis from: http://activemq.apache.org/artemis/download.html

After unzipping the download, you can then create a new broker with the name `mybroker`:

    $ cd apache-artemis-2.4.0 
    $ bin/artemis create mybroker

### Run ActiveMQ Artemis

You start ActiveMQ in a shell by running:

    $ cd mybroker
    $ bin/artemis run

Which startup Artemis in the foreground and keeps it running until you hit <kbd>ctrl</kbd>+<kbd>c</kbd>
to shutdown Artemis.

### Run Camel

Start the Camel application:

    mvn camel:run -DskipTests=true

Once started, Camel will be listening for HTTP requests (default port 8080) as a trigger mechanism to fire AMQP messages to Artemis.

From your prefered browser hit the following URL:

    http://localhost:8080/message
    
Or alternatively, using cURL:

    curl http://localhost:8080/message
    
The above actions should render on screen:

    Hello from Camel's AMQP example

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>.

### ActiveMQ Artemis web console

You can browse the Artemis web console: <http://localhost:8161/console> 
to see activity such as number of consumers and producers or acknoledged messages.

### Run with Karaf
You will need to install this example first to your local maven repository with:

	mvn install

Then you can install this example from the shell using this example's `features.xml`
for easy provisioning:

	feature:repo-add mvn:org.apache.camel.example/camel-example-artemis-amqp-blueprint/${version}/xml/features
	feature:install camel-example-artemis-amqp-blueprint

And you can see the application running by tailing the logs:

	log:tail

And you can use <kbd>ctrl</kbd>+<kbd>c</kbd> to stop tailing the log.

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!



The Camel riders!


