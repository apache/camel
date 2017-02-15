# Loan Broker Example with Apache CXF

### Introduction
This example shows how to use Camel to implement the EIP's loan broker example,
from the EIP book (http://www.enterpriseintegrationpatterns.com/SystemManagementExample.html).

The example use web services for exchanging messages between
the client, credit agency, and the banks.

### Build

You will need to compile this example first:

	mvn compile

### Run

	mvn exec:java -PWS.LoanBroker
	mvn exec:java -PWS.Client

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>


### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!
