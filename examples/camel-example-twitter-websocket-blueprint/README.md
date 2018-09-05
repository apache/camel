# Twitter and Websocket Blueprint Example

### Introduction
The example is demonstrating how to poll a constant feed of twitter searches
and publish results in real time using web socket to a web page.

This example is already configured using a testing purpose twitter account named 'cameltweet'.
And therefore the example is ready to run out of the box.

This account is only for testing purpose, and should not be used in your custom applications.
For that you need to setup and use your own twitter account.

### Build
You will need to install this example first to your local maven repository with:
  mvn install

### Run with Karaf
This example requires running in Apache Karaf / ServiceMix

	karaf

To install Apache Camel in Karaf you type in the shell (as an example here we make use of
Camel version 2.18.1):

	feature:repo-add camel ${version}

First you need to install the following features in Karaf/ServiceMix with:

  feature:install camel
	feature:install camel-twitter
	feature:install camel-websocket

Then you can install the Camel example:

	install -s mvn:org.apache.camel.example/camel-example-twitter-websocket-blueprint/${version}

Then open a browser to see live twitter updates in the web page

	http://localhost:9090
<http://localhost:9090>
To stop the example run from Karaf/ServiceMix shell:
  stop <bundle id>

eg if the bundle id is 99 then type:
  stop 99

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
  <http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!



The Camel riders!
