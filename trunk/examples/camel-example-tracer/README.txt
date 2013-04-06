Tracer Example
==============

This example shows how to persist Camel trace event messages into a database using JPA.

You will need to compile this example first:
  mvn compile
  
For a background in tracer and JPA see
  http://camel.apache.org/tracer.html
  http://camel.apache.org/jpa.html

To run the example with Maven, type
  mvn camel:run

In the HSQL Database Explorer type
  select * from camel_messagetraced

to see the trace events of the Exchanges. Notice how the Exchange correlates with
fromNode/toNode so you exactly can see how a given Exchange was routed in Camel.

Using the query:
  select shortExchangeId, previousNode, toNode, body from camel_messagetraced order by id

is a bit more easier to read as it uses the fields we are most interested in to see how Exchanges
was routed in Camel.

In the console you can enter some words separated with space. Try to enter:
  nice beer
  beer whiskey
  camel nice day

This example will based on the input get some quotes from the input and select the best quote
to return as response in the console.

To stop the example hit ctrl + c

This example is documented at
  http://camel.apache.org/tracer-example.html

If you hit any problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!
