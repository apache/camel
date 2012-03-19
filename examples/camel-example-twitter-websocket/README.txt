Twitter and Websocket Example
=============================

The example is demonstrating how to poll a constant feed of twitter searches
and publish results in real time using web socket to a web page.

To use twitter, you need a twitter account which have setup an application to be used.
For twitter users, you may be familiar that twitter requires you to grant applications
access to your twitter account, such as twitter for iphone etc.
The same applies for this example.

We have described this in more details at the Camel twitter documentation:
  http://camel.apache.org/twitter

When you have created an application, you get a number of details back from twitter
which you need to use the twitter component. Enter these details in the source code at:
  src/main/java/org/apache/camel/example/websocket/CamelTwitterWebSocketMain.java
in the constant fields, by replacing the values "INSERT HERE".

You will need to compile this example first:
  mvn compile

To run the example type
  mvn exec:java

Then open a browser to see live twitter updates in the web page
  http://localhost:9090

To stop the example hit ctrl + c

This example is documented at
  http://camel.apache.org/twitter-websocket-example.html

If you hit any problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!



