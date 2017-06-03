Camel CDI Example
=================

This example shows how to work with Camel in the Java Container using CDI to configure components,
endpoints and beans.

The example generates messages using timer trigger, writes them to the standard output and the mock
endpoint (for testing purposes).

You will need to compile this example first:
  mvn install

To run the example type
  mvn camel:run

You will see the message printed to the console every 5th second.

To stop the example hit ctrl + c

For more help see the Apache Camel documentation

    http://camel.apache.org/

