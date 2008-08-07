Camel XMPP
==========

Welcome to the Camel XMPP transport for communicating with Jabber servers.

For more details see

  http://activemq.apache.org/camel/xmpp.html


Using Spring based xml configuration in 'activemq.xml'
------------------------------------------------------

In this example an xmpp user called 'bot' will propagate any messages to the queue 'gimme_an_a' to a muc 
called 'monitor'. The conference subdomain is part of openfire muc jid implementation (i guess). Notice 
the quoted ampersands.

<camelContext id="camel" xmlns="http://activemq.apache.org/camel/schema/spring">
    <route>
        <from uri="activemq:gimme_an_a"/>
        <to uri="xmpp://bot@freetwix.hh/?port=5222&amp;password=meapassword&amp;room=monitor@conference.freetwix.hh"/>
    </route>
</camelContext>


Running the Integration Tests
-----------------------------

To run the intergration tests you need a Jabber server to communicate with such as Jive Software's WildFire


To enable the integration tests set the maven property

  xmpp.enable = true

You may also want to overload the default value of the server to connect with via

  xmpp.url = xmpp://camel@localhost/?login=false&room=


