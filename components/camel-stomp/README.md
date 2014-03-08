camel-stomp
===========

Camel component used for communicating with [Stomp] (http://stomp.github.io/) compliant message brokers, like [Apache ActiveMQ](http://activemq.apache.org) or [ActiveMQ Apollo](http://activemq.apache.org/apollo/).

URI format
----------

    stomp:destination

Where destination is broker specific. With ActiveMQ you can use queues and topics in the form of

    stomp:queue:test

Samples
-------

    from("direct:foo").to("stomp:queue:test")


