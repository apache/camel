/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.amqp;

import javax.net.ssl.SSLContext;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.SslContext;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.PropertyPlaceholderDelegateRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.qpid.jms.transports.TransportSslOptions;
import org.apache.qpid.jms.transports.TransportSupport;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.apache.camel.component.amqp.AMQPComponent.amqpComponent;
import static org.apache.camel.component.amqp.AMQPConnectionDetails.AMQP_PORT;
import static org.apache.camel.component.amqp.AMQPConnectionDetails.discoverAMQPSsl;


public class AMQPSSLRouteTest extends CamelTestSupport {

    static int amqpPort = AvailablePortFinder.getNextAvailable();
    static BrokerService broker;

    private static final String KEYSTORE = "./src/test/resources/broker.ks";
    private static final String TRUSTSTORE = "./src/test/resources/broker.ks";
    private static final String PASSWORD = "password";
    

    @EndpointInject(uri = "mock:result")
    MockEndpoint resultEndpoint;

    String expectedBody = "Hi there!";

    @BeforeClass
    public static void beforeClass() throws Exception {
        System.setProperty("javax.net.ssl.trustStorePassword", PASSWORD);
        System.setProperty("javax.net.ssl.trustStore", TRUSTSTORE);
        System.setProperty("javax.net.ssl.keyStore", KEYSTORE);
        System.setProperty("javax.net.ssl.keyStorePassword", PASSWORD);
        System.setProperty(AMQP_PORT, amqpPort + "");

        broker = new BrokerService();
        broker.setPersistent(false);
        broker.setAdvisorySupport(false);
        broker.setDeleteAllMessagesOnStartup(true);
        broker.setUseJmx(false);
        broker.addConnector("amqp+ssl://0.0.0.0:" + amqpPort);

        TransportSslOptions sslOptions = new TransportSslOptions();
        sslOptions.setKeyStoreLocation(KEYSTORE);
        sslOptions.setKeyStorePassword(PASSWORD);
        sslOptions.setTrustStoreLocation(TRUSTSTORE);
        sslOptions.setTrustStorePassword(PASSWORD);

        SSLContext sslContext = TransportSupport.createSslContext(sslOptions);

        final SslContext brokerContext = new SslContext();
        brokerContext.setSSLContext(sslContext);

        broker.setSslContext(brokerContext);
        broker.start();

    }

    @AfterClass
    public static void afterClass() throws Exception {
        System.clearProperty("javax.net.ssl.trustStorePassword");
        System.clearProperty("javax.net.ssl.trustStore");
        System.clearProperty("javax.net.ssl.keyStore");
        System.clearProperty("javax.net.ssl.keyStorePassword");
        broker.stop();
    }

    @Test
    public void testJmsQueue() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).header("cheese").isEqualTo(123);
        template.sendBodyAndHeader("amqp-ssl:queue:ping", expectedBody, "cheese", 123);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testRequestReply() {
        String response = template.requestBody("amqp-ssl:queue:inOut", expectedBody, String.class);
        assertEquals("response", response);
    }

    @Test
    public void testJmsTopic() throws Exception {
        resultEndpoint.expectedMessageCount(2);
        resultEndpoint.message(0).header("cheese").isEqualTo(123);
        template.sendBodyAndHeader("amqp-ssl:topic:ping", expectedBody, "cheese", 123);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testPrefixWildcard() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        template.sendBody("amqp-ssl:wildcard.foo.bar", expectedBody);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testIncludeDestination() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).header("JMSDestination").isEqualTo("ping");
        template.sendBody("amqp-ssl:queue:ping", expectedBody);
        resultEndpoint.assertIsSatisfied();
    }
    

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        return registry;
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        JndiRegistry registry = (JndiRegistry)((PropertyPlaceholderDelegateRegistry)camelContext.getRegistry()).getRegistry();
        registry.bind("amqpConnection", discoverAMQPSsl(camelContext));        
        camelContext.addComponent("amqp-ssl", amqpComponent("amqps://localhost:" + amqpPort));
        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("amqp-ssl:queue:ping").to("log:routing").to("mock:result");

                from("amqp-ssl:queue:inOut").setBody().constant("response");

                from("amqp-ssl:topic:ping").to("log:routing").to("mock:result");

                from("amqp-ssl:topic:ping").to("log:routing").to("mock:result");

                from("amqp-ssl:queue:wildcard.>").to("log:routing").to("mock:result");

                from("amqp:queue:uriEndpoint").to("log:routing").to("mock:result");
            }
        };
    }

}
