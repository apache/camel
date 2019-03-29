# Camel Console Example

### Introduction

This is a simple example that shows how to get started with Camel.

This is a beginner's example that demonstrates how to get started with Apache Camel.
In this example we integrate with the console using the Stream component. 
The example is interactive - it reads input from the console, and then transforms the input to upper case and prints it back to the console.

This is implemented with a Camel route defined in the Spring XML 

### Build

You will need to compile this example first:

	mvn compile

### Run

To run the example type

	mvn camel:run

You can see the routing rules by looking at the XML in the directory:
  `src/main/resources/META-INF/spring`

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>

You can also run the example from your editor such as Eclipse, IDEA etc,
by opening the org.apache.camel.example.console.CamelConsoleMain class
and then right click, and chose run java application.

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!


The Camel riders!
