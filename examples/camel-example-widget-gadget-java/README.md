# Widget and Gadget Example - Plain Java

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

To setup and run Apache ActiveMQ then download it from the []ActiveMQ website](http://activemq.apache.org/).

Then extract the download such as (the .tar)

    tar xf ~/Downloads/apache-activemq-5.13.0-bin.tar.gz

Then the broker can be started with

    cd apache-activemq-5.13.0
    bin/activemq console

And then Broker is running (you can press <kbd>ctrl</kbd>+<kbd>c</kbd> in the shell
to stop the broker).

The Camel application connects to the remote broker on url: `tcp://localhost:61616`.
The url can be changed in the `WidgetMain.java` source code.

When the ActiveMQ broker is running, then you can run this example using:

    mvn compile exec:java

When the Camel application runs, you should see 2 orders being processed and logged to the console, such as:
```
2016-01-11 12:53:53,978 [sumer[newOrder]] INFO  widget                         - Exchange[ExchangePattern: InOnly, BodyType: byte[], Body: <order>  <customerId>123</customerId>  <product>widget</product>  <amount>2</amount></order>]
2016-01-11 12:53:54,005 [sumer[newOrder]] INFO  gadget                         - Exchange[ExchangePattern: InOnly, BodyType: byte[], Body: <order>  <customerId>456</customerId>  <product>gadget</product>  <amount>3</amount></order>]
```

You can access the ActiveMQ web console using [http://localhost:8161/admin/](http://localhost:8161/admin/)
and then browse the queues. You should see the three queues:

 - newOrder
 - widget
 - gadget

The Camel application can be stopped pressing <kbd>ctrl</kbd>+<kbd>c</kbd> in the shell.

### Configuration

The Camel application is configured in the `src/main/java/org/apache/camel/example/widget/WidgetMain.java` file.

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
    <http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

The Camel riders!
