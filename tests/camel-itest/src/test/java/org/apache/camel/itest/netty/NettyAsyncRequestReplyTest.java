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
package org.apache.camel.itest.netty;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.itest.utils.extensions.JmsServiceExtension;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Doing request/reply over Netty with async processing.
 */
public class NettyAsyncRequestReplyTest extends CamelTestSupport {
    @RegisterExtension
    public static JmsServiceExtension jmsServiceExtension = JmsServiceExtension.createExtension();

    private static final Logger LOG = LoggerFactory.getLogger(NettyAsyncRequestReplyTest.class);

    private int port;

    @Test
    void testNetty() {
        String out = template.requestBody("netty:tcp://localhost:" + port + "?textline=true&sync=true", "World", String.class);
        assertEquals("Bye World", out);

        String out2 = template.requestBody("netty:tcp://localhost:" + port + "?textline=true&sync=true", "Camel", String.class);
        assertEquals("Bye Camel", out2);
    }

    @Disabled("TODO: investigate for Camel 3.0")
    @Test
    void testConcurrent() throws Exception {
        int size = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(20);
        // we access the responses Map below only inside the main thread,
        // so no need for a thread-safe Map implementation
        Map<Integer, Future<String>> responses = new HashMap<>();
        for (int i = 0; i < size; i++) {
            final int index = i;
            Future<String> out = executor.submit(() -> {
                String reply = template.requestBody("netty:tcp://localhost:" + port + "?textline=true&sync=true", index,
                        String.class);
                LOG.info("Sent {} received {}", index, reply);
                assertEquals("Bye " + index, reply);
                return reply;
            });
            responses.put(index, out);
        }

        // get all responses
        Set<String> unique = new HashSet<>();
        for (Future<String> future : responses.values()) {
            String reply = future.get(120, TimeUnit.SECONDS);
            assertNotNull(reply, "Should get a reply");
            unique.add(reply);
        }

        // should be 1000 unique responses
        assertEquals(size, unique.size(), "Should be " + size + " unique responses");
        executor.shutdownNow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                port = AvailablePortFinder.getNextAvailable();

                from("netty:tcp://localhost:" + port + "?textline=true&sync=true&reuseAddress=true&synchronous=false")
                        .to("activemq:queue:NettyAsyncRequestReplyTest")
                        .log("Writing reply ${body}");

                from("activemq:queue:NettyAsyncRequestReplyTest")
                        .transform(simple("Bye ${body}"));
            }
        };
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        // add ActiveMQ with embedded broker
        JmsComponent amq = jmsServiceExtension.getComponent();

        amq.setCamelContext(context);

        registry.bind("activemq", amq);
    }

}
