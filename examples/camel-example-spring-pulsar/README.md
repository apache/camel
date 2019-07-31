# Spring & Pulsar Example

### Introduction
This example shows how to work with the Camel-Pulsar Component.

The example consumes messages from a topic and invoke the bean
with the received message.

You can run the Pulsar cluster through docker:

```
docker run -it \
  -p 6650:6650 \
  -p 8080:8080 \
  -v $PWD/data:/pulsar/data \
  apachepulsar/pulsar:2.0.1-incubating \
  bin/pulsar standalone
```

The Server is required to be running when you try the clients.

And for the Client we have a total of three flavors: NB only 1st one working at the moment
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
	Below don't work yet
	mvn exec:java -PCamelClientRemoting
	mvn exec:java -PCamelClientEndpoint

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>

### Documentation
