# Declarative Transformer and Validator Demo using Spring XML


### Introduction

This example shows how to work with declarative transformation and validation by declaring data types.

### Build

You will need to compile this example first:

	mvn compile

### Run

To run the example type

	mvn exec:java

You can see the routing rules by looking at the Spring XML configuration lives in
`src/main/resources/META-INF/spring`

If you enable DEBUG level log for org.apache.camel.processor, you can see the details
of when/which transformers & validators are applied. Check the
`src/main/resources/log4j2.properties`


### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!


The Camel riders!
