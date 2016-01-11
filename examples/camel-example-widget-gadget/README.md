# Widget and Gadget Example

### Introduction

This example shows the Widget and Gadget use-case from the Enterprise Integration Patterns book.

The example provides a simple order system, where incoming orders, is routed to either a widget or gadget inventory system,
for further processing. The example uses the most famous pattern from the EIP book, which is the Content Based Router.

The example is implemented in plain Java without using any kind of _application server_ but just a plain old _Java Main_.

#### Camel component used in this example

* camel-core
* camel-jms
* activemq-camel

### Build 

You will need to build this example first:

    mvn install

### Run 

This example requires an existing Apache ActiveMQ broker running.

This application connects to the remote broker on url: tcp://localhost:61616.
The url can be changed in the `WidgetMain.java` source code.

When the ActiveMQ broker is running, then you can run this example using

    mvn compile exec:java

When the Camel application runs, you should see 2 orders being processed and logged to the console.

You can access the ActiveMQ web console using [http://localhost:8161/admin/](http://localhost:8161/admin/)
and then browse the queues. You should see the three queues:

 - newOrder
 - widget
 - gadget


### Configuration

The Camel application is configured in the `src/main/java/org/apache/camel/example/widget/WidgetMain.java` file.

### Documentation

This example is documented at
	[http://camel.apache.org/widget-gadget.html](http://camel.apache.org/widget-gadget.html)

### Forum, Help, etc 

If you hit an problems please let us know on the Camel Forums
  [http://camel.apache.org/discussion-forums.html](http://camel.apache.org/discussion-forums.html)

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!
