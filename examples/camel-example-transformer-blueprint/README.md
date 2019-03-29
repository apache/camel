# Declarative Transformer example using Blueprint XML


### Introduction

This example shows how to work with declarative transformation by declaring data types.

### Build

You will need to compile this example first:

	mvn compile

### Run without container

To run the example, type

	mvn camel:run

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>.

### Run on karaf container

To run the example on the karaf container

#### Step 1: Start karaf container

    karaf / karaf.bat

#### Step 2: Deploy

    feature:repo-add mvn:org.apache.camel.example/camel-example-transformer-blueprint/${version}/xml/features
    feature:install camel-example-transformer-blueprint

#### Step 3: Check the output

You will see the output by log:tail or in ${karaf}/data/karaf.log

You can see the routing rules by looking at the Blueprint XML configuration lives in
`src/main/resources/OSGI-INF/blueprint`

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!


The Camel riders!
