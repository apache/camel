# Camel JMX Management

### Introduction

This example shows how to manage Camel using JMX (with jconsole)

The example uses a timer to every 5th second to generate a file with 100 stock quotes.

Another route uses a file consumer to read the files and split the file and send every single
stock quote to a JMS queue.

Then a third route consumes from this JMS queue and simulate a little CPU heavy work (by delaying)
before the data is transformed and logged. The logger will log the progress by logging how
long time it takes to process 100 messages.

Now the idea is to use the Camel JMX management to be able to adjust this during runtime.
What it allows you to do is to improve the performance of this example.

At first there is a throttler that will throttle how fast Camel sends message to the JMS queue.
For starters you can change this at runtime from the default 10 msg/sec to 500 msg/sec etc.
This is done by changing the JMX attribute maximumRequestsPerPeriod on the throttler in the /producer group.

The next issue is that the JMS consumer now cannot catch up and you should see that the number of messages
on the JMS queue grows a little by little. You can find the queue from the ActiveMQ mbean and drill down under /queues.

If this goes a bit to slow you can increase the first route in Camel to produce files faster. This is done by
changing the period in the timer endpoint from 5000 to let say 2000. Before this takes effect you have to
restart the timer consumer. So find the timer consumer and invoke the stop and start JMX operation.

Now you should see the messages start to pile up in the JMS queue.
What we do next is to increase the number of concurrent consumers. To do that you have to set this on the JMS
endpoint. Set the concurrentConsumers from 1 to 20. And just as the timer consumer this only takes effect when
the JMS consumer is restarted. So do a stop and start operation.

What you should see is that Camel should be able to process the files much faster now and the logger should
output a higher throughput.

### Build

You will need to compile this example first:

	mvn compile


### Run

To run the example type

	mvn camel:run

To use jconsole type

	jconsole

And you should be able to see a process id in the connect to agent tab.
If its there then click in and you should be connected to the Camel application.
If its missing you can click on the advanced tab and type in JMX URL:
  `service:jmx:rmi:///jndi/rmi://localhost:1099/jmxrmi/camel`
  (some older versions of Java does not list the local processes)

You can see the routing rules by looking at the java code in the
`src/main/java directory` and the Spring XML configuration lives in
`src/main/resources/META-INF/spring`

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!
