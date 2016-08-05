# Camel FTP example

### Introduction

An example which shows how to integrate Camel with an FTP server.

This example requires that an existing FTP server is available.
You can configure the location of this FTP server in the file:
  `src/main/resources/ftp.properties`

### Build

You will need to compile this example first:

	mvn compile

### Run

This example can either run as a Camel client or server.

* The client will upload files from the `target/upload` directory
  to the FTP server.

* The server will download files from the FTP server to the local
  file system in the `target/download` directory.

To run the client you type:

	mvn compile exec:java -Pclient

... and instructions will be printed on the console.

To run the server you type:

	mvn compile exec:java -Pserver

... and instructions will be printed on the console.


### Documentation

This example is documented at [http://camel.apache.org/ftp-example.html](http://camel.apache.org/ftp-example.html)

You can enable verbose logging by adjustung the `src/main/resources/log4j.properties` file as documented in the file.

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!


The Camel riders!
