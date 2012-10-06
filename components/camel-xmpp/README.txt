Camel XMPP
==========

Welcome to the Camel XMPP transport for communicating with Jabber servers.

For more details see

  http://camel.apache.org/xmpp.html

  
About the XMPP unit / integration tests
------------------------------------------
Most of the tests in this module are configured to execute against an embedded version
of the Apache Vysper XMPP server (http://mina.apache.org/vysper/).  A small number of users and 
chat rooms are statically configured during the server setup and are re-used across the tests.  

@see org.apache.camel.component.xmpp.EmbeddedXmppTestServer.java in ./src/test/java 

A few of the tests in this module specifically require a GoogleTalk service.  These tests are
annotated with @Ignore by default. The tester must configure such tests with known gmail 
credentials prior to execution.
