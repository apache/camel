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
package org.apache.camel.component.jms;

import java.util.concurrent.Executors;
import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * @version 
 */
public class JmsPollingConsumerTest extends CamelTestSupport {

    @Test
    public void testJmsPollingConsumerWait() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello Claus");

        // use another thread for polling consumer to demonstrate that we can wait before
        // the message is sent to the queue
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            public void run() {
                String body = consumer.receiveBody("activemq:queue.start", String.class);
                template.sendBody("activemq:queue.foo", body + " Claus");
            }
        });

        // wait a little to demonstrate we can start poll before we have a msg on the queue
        Thread.sleep(500);

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testJmsPollingConsumerNoWait() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello Claus");

        // use another thread for polling consumer to demonstrate that we can wait before
        // the message is sent to the queue
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            public void run() {
                String body = consumer.receiveBodyNoWait("activemq:queue.start", String.class);
                assertNull("Should be null", body);

                template.sendBody("activemq:queue.foo", "Hello Claus");
            }
        });

        // wait a little to demonstrate we can start poll before we have a msg on the queue
        Thread.sleep(500);

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testJmsPollingConsumerLowTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello Claus");

        // use another thread for polling consumer to demonstrate that we can wait before
        // the message is sent to the queue
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            public void run() {
                String body = consumer.receiveBody("activemq:queue.start", 100, String.class);
                assertNull("Should be null", body);

                template.sendBody("activemq:queue.foo", "Hello Claus");
            }
        });

        // wait a little to demonstrate we can start poll before we have a msg on the queue
        Thread.sleep(500);

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testJmsPollingConsumerHighTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello Claus");

        // use another thread for polling consumer to demonstrate that we can wait before
        // the message is sent to the queue
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            public void run() {
                String body = consumer.receiveBody("activemq:queue.start", 3000, String.class);
                template.sendBody("activemq:queue.foo", body + " Claus");
            }
        });

        // wait a little to demonstrate we can start poll before we have a msg on the queue
        Thread.sleep(500);

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();
    }


    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("activemq:queue.start");

                from("activemq:queue.foo").to("mock:result");
            }
        };
    }
}
