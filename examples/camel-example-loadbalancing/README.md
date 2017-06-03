# Load balancing with MINA Example


### Introduction

This example shows how you can easily use the Camel-MINA component to design a solution
allowing for distributing message workload onto several servers.
These servers are simple TCP/IP servers created by the Apache MINA framework and run in
separate JVMs. The load balancer pattern of Camel which is used on top of them allows for
sending in Round Robin mode the messages created from a Camel Bean component
alternatively between each server running on localhost:9991 and localhost:9992.

Within this demo every ten seconds, a Report object is created from the Camel load balancer server.
This object is sent by the Camel load balancer to a MINA server where the object is then serialized.
One of the two MINA servers (localhost:9991 and localhost:9992) receives the object and enriches
the message by setting the field reply of the Report object. The reply is sent back by the MINA
server to the client, which then logs the reply on the console.

If any of the two MINA servers is not running, then the load balancer will automatic failover
to the next server.

### Build

To compile and install the project in your maven repo, execute the following
command on the root of the project

	mvn clean install

### Run

To run the example, then execute the following command in the respective folder:

>mina1

	mvn exec:java -Pmina1

>mina2

	mvn exec:java -Pmina2

>loadbalancing

	mvn exec:java -Ploadbalancer

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!
