Twitter and Websocket Blueprint Example
=======================================

The example is demonstrating how to poll a constant feed of twitter searches
and publish results in real time using web socket to a web page.

This example is already configured using a testing purpose twitter account named 'cameltweet'.
And therefore the example is ready to run out of the box.

This account is only for testing purpose, and should not be used in your custom applications.
For that you need to setup and use your own twitter account.

We have described this in more details at the Camel twitter documentation:
  http://camel.apache.org/twitter

You will need to install this example first to your local maven repository with:
  mvn install

This example requires running in Apache Karaf / ServiceMix

To install Apache Camel in Karaf you type in the shell (we use version 2.12.0):

  features:chooseurl camel 2.12.0
  features:install camel

First you need to install the following features in Karaf/ServiceMix with:

  features:install camel-twitter
  features:install camel-websocket

Then you can install the Camel example:

  osgi:install -s mvn:org.apache.camel/camel-example-twitter-websocket-blueprint/2.12.0

Then open a browser to see live twitter updates in the web page
  http://localhost:9090

To stop the example run from Karaf/ServiceMix shell:
  stop <bundle id>

eg if the bundle id is 99 then type:
  stop 99

This example is documented at
  http://camel.apache.org/twitter-websocket-blueprint-example.html

There is a regular (non OSGi Blueprint) example as well documented at:
  http://camel.apache.org/twitter-websocket-example.html

If you hit any problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!
