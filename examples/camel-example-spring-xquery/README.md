# Spring XQuery Example

### Introduction
This example shows how to

 * work with files and JMS
 * transform messages using XQuery
 * use Spring XML to configure all routing rules and components

The example consumes messages from a directory, transforms them, then sends
them to a queue.

### Build
You will need to compile this example first:

	mvn compile

### Run
To run the example using Maven, type

	mvn camel:run

You can see the routing rules by looking at the the Spring XML configuration
at `src/main/resources/META-INF/spring`

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>


### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!



The Camel riders!
