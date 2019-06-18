# Cafe Example with Endpoint DSL

### Introduction

This example uses the Camel Endpoint DSL to configure Camel endpoints using a type-safe Java endpoint DSL
(eg fluent builder style).

This example shows how to work with splitter and aggregator to implement a Cafe demo.

First it uses the splitter to dispatch the order, then sends the orders to barista by checking 
if the coffee is hot or cold. When the coffee is ready, we use a aggregate to gather the drinks for waiter to deliver.


### Build

You will need to compile this example first:

	mvn compile

### Run

To run the example type

	mvn camel:run

You can see the routing rules by looking at the java code in the
`src/main/java` directory.

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!


The Camel riders!
