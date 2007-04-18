/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor;

import junit.framework.TestCase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.processor.idempotent.MemoryMessageIdRepository;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.ProducerCache;
import org.apache.camel.impl.DefaultCamelContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @version $Revision: 1.1 $
 */
public class IdempotentConsumerTest extends TestCase {
    private static final transient Log log = LogFactory.getLog(IdempotentConsumerTest.class);

    protected CamelContext container = new DefaultCamelContext();
    protected CountDownLatch latch = new CountDownLatch(3);
    protected Endpoint<Exchange> endpoint;
    protected ProducerCache<Exchange> client = new ProducerCache<Exchange>();
    protected List<String> receivedBodies = new ArrayList<String>();

    public void testDuplicateMessagesAreFilteredOut() throws Exception {
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("3", "three");

        // lets wait on the message being received
        boolean received = latch.await(20, TimeUnit.SECONDS);
        assertTrue("Did not receive the message!", received);

        assertEquals("Should have received 3 responses: " + receivedBodies, 3, receivedBodies.size());

        assertEquals("received bodies", Arrays.asList(new Object[] { "one", "two", "three"}), receivedBodies);

        log.debug("Received bodies: " + receivedBodies);
    }

    protected void sendMessage(final Object messageId, final Object body) {
        client.send(endpoint, new Processor<Exchange>() {
            public void process(Exchange exchange) {
                // now lets fire in a message
                Message in = exchange.getIn();
                in.setBody(body);
                in.setHeader("messageId", messageId);
            }
        });
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
        final String endpointUri = "queue:test.a";

        // lets add some routes
        container.addRoutes(createRouteBuilder(endpointUri, processor));
        endpoint = container.resolveEndpoint(endpointUri);
        assertNotNull("No endpoint found for URI: " + endpointUri, endpoint);

        container.start();
    }

    protected RouteBuilder createRouteBuilder(final String endpointUri, final Processor<Exchange> processor) {
        return new RouteBuilder() {
            public void configure() {
                from(endpointUri).idempotentConsumer(header("messageId"), MemoryMessageIdRepository.memoryMessageIdRepository()).process(processor);
            }
        };
    }

    @Override
    protected void tearDown() throws Exception {
        client.stop();
        container.stop();
    }
}
