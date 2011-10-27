Loan Broker Example
====================

This example shows how to use Camel to implement the EIP's loan broker example.

The example has two version, one is queue version which leverages the message
queue to combine the credit agency and bank loan quote processing and it
uses the InOnly exchange pattern; the other is web service version which shows
how to integrate the credit agency and bank web services together and it uses
the InOut exchange pattern.

You will need to compile this example first:
  mvn compile

The example of queue version should run if you type
  mvn exec:java -PQueue.LoanBroker
  mvn exec:java -PQueue.Client

The exmple of WebServices version
  mvn exec:java -PWS.LoanBroker
  mvn exec:java -PWS.Client

To stop the example hit ctrl + c

This example is documented at
  http://camel.apache.org/loan-broker-example.html

If you hit an problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!



