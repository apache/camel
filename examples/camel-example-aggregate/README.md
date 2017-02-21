# Camel Persistent Aggregate

### Introduction

This example shows how to use Camel Aggregator EIP which offers (since Camel 2.3)
database persistence.

It's an interactive example where you can type in some numbers which then are aggregated
(summed, per this sample's aggregation strategy) whenever the user types `STOP`.  
The user can then enter more numbers to do another aggregation.

#### How it works

The example is an interactive example where it prompt on the console for you to enter a number and press `ENTER`. 
The numbers you enter will then be aggregated and persisted. That means you can at any time hit `ctrl + c` to shutdown Camel. 

Then you should be able to start the example again and resume where you left.
When you want to complete the aggregation you can enter `STOP` as input and Camel will show you the result, 
which is the sum of all the numbers entered.

The persistent datastore is located in the `data/hawtdb.dat` file. Its automatic created the first time.

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


### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!


The Camel riders!
