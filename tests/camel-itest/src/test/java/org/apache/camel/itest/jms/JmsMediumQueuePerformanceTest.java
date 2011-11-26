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

import javax.jms.ConnectionFactory;
import javax.naming.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.util.jndi.JndiContext;
import org.junit.Test;

/**
 * @version 
 */
public class JmsMediumQueuePerformanceTest extends JmsPerformanceTest {
    private static final Logger LOG = LoggerFactory.getLogger(JmsMediumQueuePerformanceTest.class);
    
    protected int mediumQueueCount = 1000;

    @Override
    protected String getActiveMQFileName() {
        // using different port number to avoid clash
        return "activemq8.xml";
    }

    @Override
    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();
        answer.bind("myBean", myBean);

        // add ActiveMQ client
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://broker8");
        answer.bind("activemq", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

        return answer;
    }

    @Override
    @Test
    public void testSendingAndReceivingMessages() throws Exception {
        int expected = mediumQueueCount + messageCount;
        setExpectedMessageCount(expected);

        LOG.info("Sending {} messages first", mediumQueueCount);
        sendLoop(0, mediumQueueCount);
        LOG.info("Sent...");

        Thread.sleep(1000);

        LOG.info("Now testing");
        timedSendLoop(mediumQueueCount, expected);

        assertExpectedMessagesReceived();
    }
}
