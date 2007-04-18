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
package org.apache.camel.processor;

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.queue.QueueEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.ProducerCache;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

/**
 * @version $Revision: 1.1 $
 */
public class ChoiceTest extends TestCase {
    private static final transient Log log = LogFactory.getLog(ChoiceTest.class);

    protected CamelContext container = new DefaultCamelContext();
    protected CountDownLatch latch = new CountDownLatch(1);
    protected Endpoint<Exchange> endpoint;
    protected ProducerCache<Exchange> client = new ProducerCache<Exchange>();
    protected List<String> receivedBodies = new ArrayList<String>();

    public void testSendToFirstWhen() throws Exception {
        sendMessage("bar", "one");
        waitForMessageInQueue("b");
        assertQueueContains("b", "one");
        assertQueueEmpty("c");
        assertQueueEmpty("d");
    }

    public void testSendToSecondWhen() throws Exception {
        sendMessage("cheese", "two");
        waitForMessageInQueue("c");
        assertQueueEmpty("b");
        assertQueueContains("c", "two");
        assertQueueEmpty("d");
    }

    public void testSendToOtherwiseClause() throws Exception {
        sendMessage("somethingUndefined", "two");
        waitForMessageInQueue("d");
        assertQueueEmpty("b");
        assertQueueEmpty("c");
        assertQueueContains("d", "two");
    }

    protected void assertQueueContains(String name, String expectedBody) {
        QueueEndpoint endpoint = getQueue(name);
        BlockingQueue queue = endpoint.getQueue();
        assertEquals("Queue size for: " + name + " but was: " + queue, 1, queue.size());

        Exchange exchange = (Exchange) queue.peek();
        Object firstBody = exchange.getIn().getBody();
        assertEquals("First body", expectedBody, firstBody);
    }

    protected void assertQueueEmpty(String name) {
        QueueEndpoint queue = getQueue(name);
        assertEquals("Queue size for: " + name, 0, queue.getQueue().size());
    }


    protected void waitForMessageInQueue(String name) throws InterruptedException {
        // TODO we should replace with actual processors on each queue using a latch
        QueueEndpoint endpoint = getQueue(name);
        BlockingQueue queue = endpoint.getQueue();
        for (int i = 0; i < 100 && queue.isEmpty(); i++) {
            Thread.sleep(100);
        }
    }

    protected QueueEndpoint getQueue(String name) {
        return (QueueEndpoint) container.resolveEndpoint("queue:" + name);
    }


    protected void sendMessage(final Object headerValue, final Object body) throws Exception {
        client.send(endpoint, new Processor<Exchange>() {
            public void process(Exchange exchange) {
                // now lets fire in a message
                Message in = exchange.getIn();
                in.setBody(body);
                in.setHeader("foo", headerValue);
            }
        });

        // TODO we really should be using latches
        // for now lets wait a little bit
        Thread.sleep(3000);
    }

    @Override
    protected void setUp() throws Exception {
        final Processor<Exchange> processor = new Processor<Exchange>() {
            public void process(Exchange e) {
                Message in = e.getIn();
                String body = in.getBody(String.class);

                log.debug("Received body: " + body + " on exchange: " + e);

                receivedBodies.add(body);
                latch.countDown();
            }
        };
        final String endpointUri = "queue:a";

        // lets add some routes
        container.addRoutes(createRouteBuilder(endpointUri, processor));
        endpoint = container.resolveEndpoint(endpointUri);
        assertNotNull("No endpoint found for URI: " + endpointUri, endpoint);

        container.start();
    }

    protected RouteBuilder createRouteBuilder(final String endpointUri, final Processor<Exchange> processor) {
        return new RouteBuilder<Exchange>() {
            public void configure() {
                from("queue:a").choice()
                        .when(header("foo").isEqualTo("bar")).to("queue:b")
                        .when(header("foo").isEqualTo("cheese")).to("queue:c")
                        .otherwise().to("queue:d");
            }
        };
    }

    @Override
    protected void tearDown() throws Exception {
        client.stop();
        container.stop();
    }
}
