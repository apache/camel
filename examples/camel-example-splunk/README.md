# Camel Splunk example

### Introduction
An example which shows how to integrate Camel with Splunk.

This example requires that an Splunk Server is up and running.
(For this example, sample data provide by Splunk was used. Steps
for loading this data into splunk are documented here:
   <http://docs.splunk.com/Documentation/Splunk/latest/SearchTutorial/GetthetutorialdataintoSplunk>)


This project consists of the following examples:
	-Random search query on Splunk
	-Saved search query on Splunk
	-Publish an event to Splunk


### Build

You will need to compile this example first:

	mvn compile

### Run

To run the random search client you type:

	mvn compile exec:java -Psearch-client

... and response data will be printed on the console.

To run the saved search client you type:

	mvn compile exec:java -Psaved-search-client

... and response data will be printed on the console.

To run the saved search client you type:

	mvn compile exec:java -Ppublish-event-client

... and logs will be printed on the console.


### Configuration

You can configure the details of the Splunk server in the file:
  `src/main/resources/application.properties`

You can enable verbose logging by adjusting the `src/main/resources/log4j.properties`
  file as documented in the file.


### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!



The Camel riders!
