Camel XMPP
==========

Welcome to the Camel XMPP transport for communicating with Jabber servers.

For more details see

  http://activemq.apache.org/camel/xmpp.html


Running the Integration Tests
-----------------------------

To run the intergration tests you need a Jabber server to communicate with such as Jive Software's WildFire


To enable the integration tests set the maven property

  xmpp.enable = true

You may also want to overload the default value of the server to connect with via

  xmpp.url = xmpp://camel@localhost/?login=false&room=

