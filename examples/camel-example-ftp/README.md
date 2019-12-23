# Camel FTP example

### Introduction

An example which shows how to integrate Camel with an FTP server.

This example requires that an existing FTP server is available.
You can configure the location of this FTP server in the file:
  `src/main/resources/ftp.properties`

### Implementation

This example is implemented in Java code, and there is a client and a server application.
The client is used for uploading files from the local file system (from target/upload) to the FTP server.
The server is used for downloading files from the FTP server to the local file system (to target/download).
You can see the Java implementation by opening the src/main/java/org/apache/camel/example/ftp/MyFtpClientRouteBuilder.java for the client Java route.
And the server example is implemented in the src/main/java/org/apache/camel/example/ftp/MyFtpServerRouteBuilder.java file.

### Prerequisites

An existing FTP server should be running.

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

You can enable verbose logging by adjustung the `src/main/resources/log4j.properties` file as documented in the file.

### Help and contributions

If you hit any problem using Camel or have some feedback, 
then please [let us know](https://camel.apache.org/support.html).

We also love contributors, 
so [get involved](https://camel.apache.org/contributing.html) :-)

The Camel riders!
