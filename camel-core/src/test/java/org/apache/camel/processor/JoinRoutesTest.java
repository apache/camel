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
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.ProducerCache;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @version $Revision: 1.1 $
 */
public class JoinRoutesTest extends TestCase {
    private static final transient Log log = LogFactory.getLog(JoinRoutesTest.class);

    protected CamelContext container = new DefaultCamelContext();
    protected CountDownLatch latch = new CountDownLatch(3);
    protected Endpoint<Exchange> endpoint;
    protected ProducerCache<Exchange> client = new ProducerCache<Exchange>();
    protected List<String> receivedBodies = new ArrayList<String>();

    public void testMessagesThroughDifferentRoutes() throws Exception {
        sendMessage("bar", "one");
        sendMessage("cheese", "two");
        sendMessage("somethingUndefined", "three");

        // now lets wait for the results
        latch.await(10, TimeUnit.SECONDS);

        assertEquals("Number of receives: " + receivedBodies, 3, receivedBodies.size());
        assertEquals("Received bodies", Arrays.asList(new Object[]{"one", "two", "three"}), receivedBodies);

        log.debug("Received on queue:e the bodies: " + receivedBodies);
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

                from("queue:b").to("queue:e");
                from("queue:c").to("queue:e");
                from("queue:d").to("queue:e");

                from("queue:e").process(processor);
            }
        };
    }

    @Override
    protected void tearDown() throws Exception {
        client.stop();
        container.stop();
    }
}
