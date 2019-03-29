# Camel-RSS to Camel-IRC Example

### Introduction
This example shows how to work with the Camel-RSS and Camel-irc components.

The example creates a route that pulls from the Jira RSS feed for Camel,
extracts the title then sends that to the irc endpoint which posts it in an IRC channel.

There are 2 examples, one using XML configuration and the other using the Camel Java DSL.

### Build

You will need to compile this example first:
  mvn compile

### Run

To run the Java DSL example type

	cd javadsl
	mvn camel:run

To run the XML Configuration example type

	cd xmlconf
	mvn camel:run

You can see the routing rules by looking at the java code in the
`src/main/java` directory and the Spring XML configuration lives in
`src/main/resources/META-INF/spring` in each module

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!



The Camel riders!
