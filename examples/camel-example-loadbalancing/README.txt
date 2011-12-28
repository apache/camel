Load balancing with MINA Example
================================

This example show how you can easily use the camel-mina component to design a solution
allowing to distribute message workload on several servers.
These servers are simple TCP/IP servers created by the Apache MINA framework and running in
separate JVMs. The load balancer pattern of Camel which is used top of them allows to
send in a Round Robin model mode the messages created from a camel Bean component
respectively to each server running on localhost:9991 and localhost:9992.

The demo starts when every 5th seconds, a Report object is created from the camel load balancer server.
This object is send by the camel load balancer to a MINA server and object is serialized.
One of the two MINA servers (localhost:9991 and localhost:9992) receives the object and enrich the message
by setting the field reply of the Report object. The reply is send back by the MINA server to the client,
which then logs the reply on the console.


Running the example
===================

To compile and install the project in your maven repo, execute the following command on the 
root of the project

mvn clean install 

To run the example, execute now the following command in the respective folder:

>mina1
mvn exec:java -Pmina1

>mina2
mvn exec:java -Pmina2 

>loadbalancing
mvn exec:java -Ploadbalancer


This example is documented at
  http://camel.apache.org/loadbalancing-mina-example.html

If you hit an problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!
