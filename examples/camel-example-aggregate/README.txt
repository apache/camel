Camel Persistent Aggregate
==========================

This example shows how to use Camel Aggregator EIP which offers (since Camel 2.3)
database persistence.

It's an interactive example where you can type in some numbers which then are aggregated
(summed, per this sample's aggregation strategy) whenever the user types STOP.  
The user can then enter more numbers to do another aggregation.

The example is run using Maven.

First compile the example by entering:
  mvn compile

To run the example type:
  mvn camel:run

To stop the example hit Ctrl-C.  If you restart it and resume entering numbers
you should see that it remembered previously entered values, as it
uses a persistent store.

If you hit any problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

This example is documented at
  http://camel.apache.org/aggregate-example.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel Riders!
