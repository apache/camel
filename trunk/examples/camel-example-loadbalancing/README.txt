Load balancing with MINA Example
================================

This example show how you can easily use the camel-mina component to design a solution allowing to distribute message 
workload on several servers. Those servers are simple TCP/IP servers created by the Apache MINA framework and running in
separate Java Virtual Machine. The loadbalancer pattern of Camel which is used top of them allows to send in a Round Robin model
mode the messages created from a camel Bean component respectively to each server running on localhost:9999 and localhost:9998.
MINA has been configured to send over the wire objects serialized and this is what is showed also in this example.
The advantage of this approach is that you don't need to use CORBA or Java RMI for the communication between the different JVMs.
The example has been configured to use InOut EIP pattern.

The demo starts when every one minute, a Report object is created from the camel loadbalancer server. This object is send by the 
camel loadbalancer to a MINA server and object is serialized. One of the two MINA servers (localhost:9999 and localhost:9998) receives
the object and enrich it by setting the field reply of the Report object. The reply is send back by the MINA server to the camel loadbalancer 
who will display in its log the content of the Report object. 


For the latest & greatest documentation on how to use this example please see 
  http://camel.apache.org/loadbalancing-mina-example.html


1. Description of the routes 
============================

1) Loadbalancer

<beans xmlns="http://www.springframework.org/schema/beans"
 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 xmlns:camel="http://camel.apache.org/schema/spring"
 xsi:schemaLocation="
 http://www.springframework.org/schema/beans
 http://www.springframework.org/schema/beans/spring-beans-2.5.xsd
 http://camel.apache.org/schema/spring
 http://camel.apache.org/schema/spring/camel-spring.xsd ">
 
<bean id="service" class="org.apache.camel.example.service.Generator"/> 

<camelContext xmlns="http://camel.apache.org/schema/spring" trace="false">

    <route id="sendMessage">
    	<from uri="timer://org.apache.camel.example.loadbalancer?fixedRate=true&amp;period=60000"/>
    	<bean ref="service" method="createReport"/>
    	<to uri="direct:loadbalance"/>
    </route>
    
    <route id="loadbalancer">
        <from uri="direct:loadbalance"/>
        <loadBalance>
            <roundRobin/>
            <to uri="mina:tcp://localhost:9999?sync=true&amp;allowDefaultCodec=true"/>
            <to uri="mina:tcp://localhost:9998?sync=true&amp;allowDefaultCodec=true"/>
        </loadBalance>
        <to uri="log:org.apache.camel.example?level=INFO"/>
    </route>
</camelContext>

</beans>

2) MINA 1

<beans xmlns="http://www.springframework.org/schema/beans"
 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 xmlns:camel="http://camel.apache.org/schema/spring"
 xsi:schemaLocation="
 http://www.springframework.org/schema/beans
 http://www.springframework.org/schema/beans/spring-beans-2.5.xsd
 http://camel.apache.org/schema/spring
 http://camel.apache.org/schema/spring/camel-spring.xsd ">
 
<bean id="service" class="org.apache.camel.example.service.Reporting"/> 

<camelContext xmlns="http://camel.apache.org/schema/spring" trace="false">
    <route id="mina1">
        <from uri="mina:tcp://localhost:9999"/>
        <setHeader headerName="minaServer"><constant>localhost:9999</constant></setHeader>
		<bean ref="service" method="updateReport"/>
    </route>
</camelContext>

</beans>

2) MINA 2

<beans xmlns="http://www.springframework.org/schema/beans"
 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 xmlns:camel="http://camel.apache.org/schema/spring"
 xsi:schemaLocation="
 http://www.springframework.org/schema/beans
 http://www.springframework.org/schema/beans/spring-beans-2.5.xsd
 http://camel.apache.org/schema/spring
 http://camel.apache.org/schema/spring/camel-spring.xsd ">
 
<bean id="service" class="org.apache.camel.example.service.Reporting"/> 

<camelContext xmlns="http://camel.apache.org/schema/spring" trace="false">
    <route id="mina1">
        <from uri="mina:tcp://localhost:9998"/>
        <setHeader headerName="minaServer"><constant>localhost:9999</constant></setHeader>
		<bean ref="service" method="updateReport"/>
    </route>
</camelContext>

</beans>

2. Test the example
===================

To compile and install the project in your maven repo, execute the following command on the 
root of the project

mvn clean install 

To run the example, execute now the following command in the respective folder:

>mina1
mvn exec:java -Pmina1

>mina2
mvn exec:java -Pmina2 

>loadbalancing
mvn exec:java -Ploadbalancer

and check the result in the log of loadbalancer

[pache.camel.spring.Main.main()] MainSupport                    INFO  Apache Camel 2.2.0 starting
[pache.camel.spring.Main.main()] CamelNamespaceHandler          INFO  camel-osgi.jar/camel-spring-osgi.jar not detected in classpath
[pache.camel.spring.Main.main()] DefaultCamelContext            INFO  Apache Camel 2.2.0 (CamelContext:camelContext) is starting
[pache.camel.spring.Main.main()] DefaultCamelContext            INFO  JMX enabled. Using DefaultManagedLifecycleStrategy.
[pache.camel.spring.Main.main()] DefaultCamelContext            INFO  Started 2 routes
[pache.camel.spring.Main.main()] DefaultCamelContext            INFO  Apache Camel 2.2.0 (CamelContext:camelContext) started
[che.camel.example.loadbalancer] example                        INFO  Exchange[BodyType:org.apache.camel.example.model.Report, Body:>> ***************
********************************
>> Report id : 1
>> Report title : Report Title : 1
>> Report content : This is a dummy report
>> Report reply : Report updated from MINA server running on : localhost:9999
>> ***********************************************
]
[che.camel.example.loadbalancer] example                        INFO  Exchange[BodyType:org.apache.camel.example.model.Report, Body:>> ***************
********************************
>> Report id : 2
>> Report title : Report Title : 2
>> Report content : This is a dummy report
>> Report reply : Report updated from MINA server running on : localhost:9998
>> ***********************************************
]
[che.camel.example.loadbalancer] example                        INFO  Exchange[BodyType:org.apache.camel.example.model.Report, Body:>> ***************
********************************
>> Report id : 3
>> Report title : Report Title : 3
>> Report content : This is a dummy report
>> Report reply : Report updated from MINA server running on : localhost:9999
>> ***********************************************
]
...


This example is documented at
  http://camel.apache.org/loadbalancing-mina-example.html

If you hit an problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!
