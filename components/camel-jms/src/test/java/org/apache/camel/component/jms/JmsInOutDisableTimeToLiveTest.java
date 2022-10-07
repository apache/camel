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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class JmsInOutDisableTimeToLiveTest extends AbstractJMSTest {

    private final String urlTimeout = "activemq:JmsInOutDisableTimeToLiveTest.in?requestTimeout=2000";
    private final String urlTimeToLiveDisabled
            = "activemq:JmsInOutDisableTimeToLiveTest.in?requestTimeout=2000&disableTimeToLive=true";

    @Test
    public void testInOutExpired() throws Exception {
        MyCoolBean cool = new MyCoolBean();
        cool.setProducer(template);
        cool.setConsumer(consumer);

        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:end").expectedMessageCount(0);

        // setup a message that will timeout to prove the ttl is getting set
        //      and that the disableTimeToLive is defaulting to false
        try {
            template.requestBody("direct:timeout", "World 1");
            fail("Should not get here, timeout expected");
        } catch (CamelExecutionException e) {
            cool.someBusinessLogic();
        }

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInOutDisableTimeToLive() throws Exception {
        MyCoolBean cool = new MyCoolBean();
        cool.setProducer(template);
        cool.setConsumer(consumer);

        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:end").expectedBodiesReceived("Hello World 2");

        // send a message that sets the requestTimeout to 2 secs with a
        //      disableTimeToLive set to true, this should timeout
        //      but leave the message on the queue to be processed
        //      by the CoolBean
        try {
            template.requestBody("direct:disable", "World 2");
            fail("Should not get here, timeout expected");
        } catch (CamelExecutionException e) {
            cool.someBusinessLogic();
        }

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:timeout")
                        .to(urlTimeout)
                        .to("mock:result");

                from("direct:disable")
                        .to(urlTimeToLiveDisabled)
                        .to("mock:result");

                from("activemq:JmsInOutDisableTimeToLiveTest.out")
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
                String msg = consumer.receiveBody("activemq:JmsInOutDisableTimeToLiveTest.in", 2000, String.class);
                if (msg == null) {
                    // no more messages in queue
                    break;
                }

                // do something with body
                msg = "Hello " + msg;

                // send it to the next queue
                producer.sendBodyAndHeader("activemq:JmsInOutDisableTimeToLiveTest.out", msg, "number", count++);
            }
        }
    }

}
