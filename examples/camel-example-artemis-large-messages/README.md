# Camel Artemis Large Messages

### Introduction

This example shows how to send large messages between Apache Camel and ActiveMQ Artemis.
When we say large messages we refer to messages with sizes of GB.

You should be able to run Camel and Artemis in JVMs with lower memory such as 256/512mb etc, and
still be able to send messages in GB of sizes between them.

This works by spool big messages to disk. Artemis spool large messages to its `data/large-messages`
directory, and Camel uses stream caching to spool to a temporary directory during routing.

### Build

The example is run using Maven.

First compile the example by entering:

    mvn compile

### Install ActiveMQ Artemis

You download and unzip Apache ActiveMQ Artemis from: http://activemq.apache.org/artemis/download.html

After unzipping the download, you can then create a new broker with the name `mybroker`:

    $ cd apache-artemis-2.4.0 
    $ bin/artemis create mybroker

### Run ActiveMQ Artemis

You start ActiveMQ in a shell by running:

    $ cd mybroker
    $ bin/artemis run

Which startup Artemis in the foreground and keeps it running until you hit <kbd>ctrl</kbd>+<kbd>c</kbd>
to shutdown Artemis.

### Run Camel

Before running this example, then ensure the JVM has limited memory by executing

    export MAVEN_OPTS="-Xmx256m"

And then start the Camel application:

    mvn camel:run

You can then copy files to `target/inbox` folder which is send to Artemis, and then
back again to Camel and written to the `target/outbox` folder.

This should work for small and big files such as files with sizes of GB.
The JVM should not run out of memory.

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>.  If you restart it and resume
entering numbers you should see that it remembered previously entered values, as it
uses a persistent store.

### ActiveMQ Artemis web console

You can browse the Artemis web console: <http://localhost:8161/console> 
to see activity such as number of consumers and producers.
You can also delete all messages from queues which is a handy operation.


### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!


The Camel riders!
