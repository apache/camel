# Loan Broker Example

### Introduction
This example shows how to use Camel to implement the EIP's loan broker example,
from the EIP book (http://www.enterpriseintegrationpatterns.com/SystemManagementExample.html).

The example has two versions (JMS queues, and web services),
that uses a different transport for exchanging messages between
the client, credit agency, and the banks.

### Build

You will need to compile this example first:

	mvn compile

### Run

The example of JMS queue version should run if you type

	mvn exec:java -PQueue.LoanBroker
	mvn exec:java -PQueue.Client

The example of web services version

	mvn exec:java -PWS.LoanBroker
	mvn exec:java -PWS.Client

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>

### Documentation

This example is documented at
  <http://camel.apache.org/loan-broker-example.html>

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!
