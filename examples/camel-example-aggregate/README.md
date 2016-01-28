# Camel Persistent Aggregate

### Introduction

This example shows how to use Camel Aggregator EIP which offers (since Camel 2.3)
database persistence.

It's an interactive example where you can type in some numbers which then are aggregated
(summed, per this sample's aggregation strategy) whenever the user types STOP.  
The user can then enter more numbers to do another aggregation.

#### Camel component used in this example

* camel-core
* camel-hawtdb
* camel-spring
* camel-stream

### Build

The example is run using Maven.

First compile the example by entering:

	mvn compile

### Run

To run the example type:

	mvn camel:run

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>.  If you restart it and resume
entering numbers you should see that it remembered previously entered values, as it
uses a persistent store.

### Documentation

This example is documented at
  [http://camel.apache.org/aggregate-example.html](http://camel.apache.org/aggregate-example.html)

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!


The Camel riders!
