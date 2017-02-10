# Camel Kafka example

### Introduction

An example which shows how to integrate Camel with Kakfa.

This example requires that Kafka Server is up and running.

You will need to create following topics before you run the examples.

bin\windows\kafka-topics.bat --create --zookeeper <zookeeper host ip>:<port> --replication-factor 1 --partitions 2 --topic TestLog
bin\windows\kafka-topics.bat --create --zookeeper <zookeeper host ip>:<port> --replication-factor 1 --partitions 1 --topic AccessLog

This project consists of the following examples:


	1. Send messages continuously by typing on the command line.
	2. Example of partitioner for a given producer.
	3. Topic is sent in the header as well as in the URL.



### Build

You will need to compile this example first:

	mvn compile

### Run

1. Run the consumer first in separate shell 

	mvn compile exec:java -Pkafka-consumer


2. Run the message producer in the seperate shell

	mvn compile exec:java -Pkafka-producer

   Initially, some messages are sent programmatically. 
   
   On the command prompt, type the messages. Each line is sent as one message to kafka
   
   Type Ctrl-C to exit.



### Configuration

You can configure the details in the file:
  `src/main/resources/application.properties`

You can enable verbose logging by adjusting the `src/main/resources/log4j.properties`
  file as documented in the file.


### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!



The Camel riders!
