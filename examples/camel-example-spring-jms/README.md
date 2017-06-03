# Spring & JMS Example

### Introduction
This example shows how to work with the Camel-JMS Component.

The example consumes messages from a queue and invoke the bean
with the received message.

The Server is required to be running when you try the clients.

And for the Client we have a total of three flavors:
- Normal use the ProducerTemplate ala Spring Template style
- Using Spring Remoting for powerful "Client doesn't know at all its a remote call"
- And using the Message Endpoint pattern using the neutral Camel API

### Build
You will need to compile this example first:

	mvn compile

### Run
The example should run if you type:

#### Step 1: Run Server
	mvn exec:java -PCamelServer

#### Step 2: Run Clients
	mvn exec:java -PCamelClient
	mvn exec:java -PCamelClientRemoting
	mvn exec:java -PCamelClientEndpoint

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!



The Camel riders!
