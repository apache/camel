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
package org.apache.camel.itest.jms;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Header;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.itest.utils.extensions.JmsServiceExtension;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsPerformanceTest extends CamelTestSupport {
    @RegisterExtension
    public static JmsServiceExtension jmsServiceExtension = JmsServiceExtension.createExtension();

    private static final Logger LOG = LoggerFactory.getLogger(JmsPerformanceTest.class);

    private List<Integer> receivedHeaders = new ArrayList<>(getMessageCount());
    private List<Object> receivedMessages = new ArrayList<>(getMessageCount());

    @Test
    void testSendingAndReceivingMessages() throws Exception {
        LOG.info("Sending {} messages", getMessageCount());

        sendLoop(getMessageCount());

        LOG.info("Sending {} messages completed, now will assert on their content as well as the order of their receipt",
                getMessageCount());

        // should wait a bit to make sure all messages have been received by the MyBean#onMessage() method
        // as this happens asynchronously, that's not inside the 'main' thread
        Thread.sleep(3000);

        assertExpectedMessagesReceived();
    }

    protected int getMessageCount() {
        return 100;
    }

    protected void sendLoop(int messageCount) {
        for (int i = 1; i <= messageCount; i++) {
            sendMessage(i);
        }
    }

    protected void sendMessage(int messageCount) {
        template.sendBodyAndHeader("activemq:" + getQueueName(), "Hello:" + messageCount, "counter", messageCount);
    }

    protected String getQueueName() {
        return "testSendingAndReceivingMessages";
    }

    protected void assertExpectedMessagesReceived() throws InterruptedException {
        // assert on the expected message count
        assertEquals(getMessageCount(), receivedMessages.size(), "The expected message count does not match!");

        // assert on the expected message order
        List<Integer> expectedHeaders = new ArrayList<>(getMessageCount());
        for (int i = 1; i <= getMessageCount(); i++) {
            expectedHeaders.add(i);
        }

        List<Object> expectedMessages = new ArrayList<>(getMessageCount());
        for (int i = 1; i <= getMessageCount(); i++) {
            expectedMessages.add("Hello:" + i);
        }

        assertEquals(expectedHeaders, receivedHeaders, "The expected header order does not match!");
        assertEquals(expectedMessages, receivedMessages, "The expected message order does not match!");
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        // add AMQ client and make use of connection pooling we depend on because of the (large) number
        // of the JMS messages we do produce
        // add ActiveMQ with embedded broker
        JmsComponent amq = jmsServiceExtension.getComponent();

        amq.setCamelContext(context);

        registry.bind("myBean", new MyBean());
        registry.bind("activemq", amq);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("activemq:" + getQueueName()).to("bean:myBean");
            }
        };
    }

    protected class MyBean {
        public void onMessage(@Header("counter") int counter, Object body) {
            // the invocation of this method happens inside the same thread so no need for a thread-safe list here
            receivedHeaders.add(counter);
            receivedMessages.add(body);
        }
    }
}
