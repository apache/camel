/*
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

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class JmsInOnlyDisableTimeToLiveTest extends CamelTestSupport {

    private String urlTimeout = "activemq:queue.in?timeToLive=2000";
    private String urlTimeToLiveDisabled = "activemq:queue.in?timeToLive=2000&disableTimeToLive=true";

    @Test
    public void testInOnlyExpired() throws Exception {
        MyCoolBean cool = new MyCoolBean();
        cool.setProducer(template);
        cool.setConsumer(consumer);

        getMockEndpoint("mock:result").expectedBodiesReceived("World 1");

        // setup a message that will timeout to prove the ttl is getting set
        // and that the disableTimeToLive is defaulting to false
        template.sendBody("direct:timeout", "World 1");

        assertMockEndpointsSatisfied();

        // wait after the msg has expired
        Thread.sleep(2500);

        resetMocks();
        getMockEndpoint("mock:end").expectedMessageCount(0);

        cool.someBusinessLogic();

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyDisabledTimeToLive() throws Exception {
        MyCoolBean cool = new MyCoolBean();
        cool.setProducer(template);
        cool.setConsumer(consumer);

        getMockEndpoint("mock:result").expectedBodiesReceived("World 2");

        // send a message that sets the requestTimeout to 2 secs with a
        //      disableTimeToLive set to true, this should timeout
        //      but leave the message on the queue to be processed
        //      by the CoolBean
        template.sendBody("direct:disable", "World 2");

        assertMockEndpointsSatisfied();

        // wait after the msg has expired
        Thread.sleep(2500);

        resetMocks();
        getMockEndpoint("mock:end").expectedBodiesReceived("Hello World 2");

        cool.someBusinessLogic();

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:timeout")
                        .to(urlTimeout)
                        .to("mock:result");

                from("direct:disable")
                        .to(urlTimeToLiveDisabled)
                        .to("mock:result");

                from("activemq:queue.out")
                        .to("mock:end");
            }
        };
    }

    public static class MyCoolBean {
        private int count;
        private ConsumerTemplate consumer;
        private ProducerTemplate producer;

        public void setConsumer(ConsumerTemplate consumer) {
            this.consumer = consumer;
        }

        public void setProducer(ProducerTemplate producer) {
            this.producer = producer;
        }

        public void someBusinessLogic() {
            // loop to empty queue
            while (true) {
                // receive the message from the queue, wait at most 2 sec
                String msg = consumer.receiveBody("activemq:queue.in", 2000, String.class);
                if (msg == null) {
                    // no more messages in queue
                    break;
                }

                // do something with body
                msg = "Hello " + msg;

                // send it to the next queue
                producer.sendBodyAndHeader("activemq:queue.out", msg, "number", count++);
            }
        }
    }

}
