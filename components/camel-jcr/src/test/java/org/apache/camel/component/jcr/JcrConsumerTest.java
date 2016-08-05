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
package org.apache.camel.component.jcr;

import java.util.List;
import javax.jcr.Node;
import javax.jcr.Session;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JcrConsumerTest
 *
 * @version $Id$
 */
public class JcrConsumerTest extends JcrRouteTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(JcrConsumerTest.class);

    private String absPath = "/home/test";

    @Test
    public void testJcrConsumer() throws Exception {
        // start consumer thread first
        JcrConsumerThread consumerThread = new JcrConsumerThread();
        consumerThread.start();
        // wait until the consumer thread has tried to receive event at least once
        while (consumerThread.getReceiveTrialTimes() < 1) {
            Thread.sleep(10L);
        }

        // now create a node under the specified event node path

        Session session = openSession();

        try {
            Node folderNode = session.getRootNode();

            for (String folderNodeName : absPath.split("\\/")) {
                if (!"".equals(folderNodeName)) {
                    if (folderNode.hasNode(folderNodeName)) {
                        folderNode.getNode(folderNodeName).remove();
                    }

                    folderNode = folderNode.addNode(folderNodeName, "nt:unstructured");
                }
            }

            folderNode.addNode("node", "nt:unstructured");
            session.save();
        } finally {
            if (session != null && session.isLive()) {
                session.logout();
            }
        }

        // wait until the consumer thread captures an event
        consumerThread.join();

        Exchange exchange = consumerThread.getExchange();
        assertNotNull(exchange);

        Message message = exchange.getIn();
        assertNotNull(message);
        assertTrue(message instanceof JcrMessage);
        EventIterator eventIterator = ((JcrMessage)message).getEventIterator();
        assertNotNull(eventIterator);
        assertEquals(1, eventIterator.getSize());

        List<?> eventList = message.getBody(List.class);
        assertEquals(1, eventList.size());
        Event event = (Event) eventList.get(0);
        assertEquals(Event.NODE_ADDED, event.getType());
        assertNotNull(event.getPath());
        assertTrue(event.getPath().startsWith(absPath));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jcr://user:pass@repository/home/test?eventTypes=1&deep=true&noLocal=false")
                        .to("direct:a");
            }
        };
    }

    private class JcrConsumerThread extends Thread {

        private Exchange exchange;
        private int receiveTrialTimes;

        public void run() {
            while (exchange == null) {
                exchange = consumer.receive("direct:a", 10L);
                ++receiveTrialTimes;

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    break;
                }
            }

            LOG.debug("JcrConsumerThread receive exchange, {} after {} trials", exchange, receiveTrialTimes);
        }

        public Exchange getExchange() {
            return exchange;
        }

        public int getReceiveTrialTimes() {
            return receiveTrialTimes;
        }
    }
}
