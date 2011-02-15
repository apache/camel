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
package org.apache.camel.itest.jms;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.ConnectionFactory;
import javax.naming.Context;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.jndi.JndiContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version $Revision:520964 $
 */
public class JmsPerformanceTest extends CamelTestSupport {
    private static final transient Logger LOG = LoggerFactory.getLogger(JmsPerformanceTest.class);
    protected MyBean myBean = new MyBean();
    protected int messageCount = 1000;
    protected CountDownLatch receivedCountDown = new CountDownLatch(messageCount);
    protected long consumerSleep;
    protected int expectedMessageCount;
    protected ClassPathXmlApplicationContext applicationContext;
    protected boolean useLocalBroker = true;
    private int consumedMessageCount;

    protected String getActiveMQFileName() {
        // using different port number to avoid clash
        return "activemq7.xml";
    }

    @Test
    public void testSendingAndReceivingMessages() throws Exception {
        setExpectedMessageCount(messageCount);

        timedSendLoop(0, messageCount);

        assertExpectedMessagesReceived();
    }


    protected void sendLoop(int startIndex, int endIndex) {
        for (int i = startIndex; i < endIndex; i++) {
            sendMessage(i);
        }
    }

    protected void timedSendLoop(int startIndex, int endIndex) {
        StopWatch watch = new StopWatch("Sending");
        for (int i = startIndex; i < endIndex; i++) {
            watch.start();
            sendMessage(i);
            watch.stop();
        }
    }

    protected void sendMessage(int messageCount) {
        template.sendBodyAndHeader("activemq:" + getQueueName(), "Hello:" + messageCount, "counter", messageCount);
    }

    public String getQueueName() {
        return "testSendingAndReceivingMessages";
    }

    protected void assertExpectedMessagesReceived() throws InterruptedException {
        receivedCountDown.await(50, TimeUnit.SECONDS);

        assertEquals("Received message count", expectedMessageCount, consumedMessageCount);

        // TODO assert that messages are received in order
    }

    @Override
    @Before
    public void setUp() throws Exception {
        if (useLocalBroker) {
            applicationContext = new ClassPathXmlApplicationContext(getActiveMQFileName());
            applicationContext.start();
        }

        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        if (applicationContext != null) {
            applicationContext.stop();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("activemq:" + getQueueName()).to("bean:myBean");
            }
        };
    }

    @Override
    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();
        answer.bind("myBean", myBean);

        // add ActiveMQ client
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61617");
        answer.bind("activemq", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

        return answer;
    }

    public int getExpectedMessageCount() {
        return expectedMessageCount;
    }

    public void setExpectedMessageCount(int expectedMessageCount) {
        this.expectedMessageCount = expectedMessageCount;
        receivedCountDown = new CountDownLatch(expectedMessageCount);
    }

    protected class MyBean {
        public void onMessage(String body) {
            if (consumerSleep > 0) {
                try {
                    Thread.sleep(consumerSleep);
                } catch (InterruptedException e) {
                    LOG.warn("Caught: " + e, e);
                }
            }
            consumedMessageCount++;
            receivedCountDown.countDown();            
        }
    }
}
