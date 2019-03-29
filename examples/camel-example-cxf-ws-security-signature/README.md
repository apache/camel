CXF WS-SECURITY OSGi HTTP WEB SERVICE
=========================

### Introduction

Create a web service with CXF using WS-SECURITY Signature action and expose it through the OSGi HTTP
Service, the main purpose is to demonstrate how to use signaturePropRefId WSS4J configuration in
OSGi container.


### Explanation

The web service is a simple JAX-WS web service with ws-security Signature and UsernameToken action called HelloWorldSecurity. The 
interface and the implementation are located in the src/main/java/org/
apache/camel/example/cxf/ws directory of this example.

The camel-context.xml file, located in the src/main/resources/META-INF/spring
directory:


1. Configures the web service endpoint as follows:

    <jaxws:endpoint id="helloWorld"
        implementor="org.apache.camel.example.cxf.ws.HelloWorldImpl"
        address="/HelloWorldSecurity">
        <jaxws:inInterceptors>
            <bean class="org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor">
                <constructor-arg>
                    <map>
                        <entry key="action" value="UsernameToken Signature"/>
                        <entry key="passwordType" value="PasswordText"/>
                        <entry key="passwordCallbackRef">
                            <ref bean="myPasswordCallback"/>
                        </entry>
                        <entry key="signaturePropRefId" value="wsCryptoProperties"/>
                        <entry key="wsCryptoProperties" value-ref="wsCryptoProperties"/>
                    </map>
                </constructor-arg>
            </bean>
            <bean class="org.apache.cxf.binding.soap.saaj.SAAJInInterceptor" />
        </jaxws:inInterceptors>
    </jaxws:endpoint>
    
    <util:properties id="wsCryptoProperties">

       <prop
            key="org.apache.ws.security.crypto.provider">org.apache.ws.security.components.crypto.Merlin</prop>

       <prop
            key="org.apache.ws.security.crypto.merlin.keystore.type">jks</prop>

       <prop
           key="org.apache.ws.security.crypto.merlin.keystore.password">storepassword</prop>

       <prop
           key="org.apache.ws.security.crypto.merlin.keystore.file">server-truststore.jks</prop>
    </util:properties>

### Build
You will need to compile this example first:

    mvn install

### Run

To run the example on Apache Karaf 4.x or newer

#### Step 1: Karaf

Launch the server

    karaf / karaf.bat

#### Step 2: Add features

Add features required

    feature:install cxf
    feature:install camel
    feature:install camel-cxf

#### Step 3: Deploy

Deploy the example

    install -s mvn:org.apache.camel.example/camel-example-cxf-ws-security-signature/${version}

To view the service WSDL, open your browser and go to the following
URL:

  http://localhost:8181/cxf/HelloWorldSecurity?wsdl


### Running a Client

To run the java code client:

1. Change to the <camel_home>/examples/camel-example-cxf-ws-security-signature
   directory.

2. Run the following command:

   `mvn compile exec:java`

   If the client request is successful, it will print out:
       <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body><ns2:sayHelloResponse xmlns:ns2="http://cxf.apache.org/wsse/handler/helloworld"><return>Hello CXF</return></ns2:sayHelloResponse></soap:Body></soap:Envelope>
       
### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!


The Camel riders!
