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
package org.apache.camel.itest.netty;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.jms.ConnectionFactory;
import javax.naming.Context;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.itest.CamelJmsTestHelper;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.jndi.JndiContext;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * Doing request/reply over Netty with async processing.
 */
public class NettyAsyncRequestReplyTest extends CamelTestSupport {

    private int port;

    @Test
    public void testNetty() throws Exception {
        String out = template.requestBody("netty:tcp://localhost:" + port + "?textline=true&sync=true", "World", String.class);
        assertEquals("Bye World", out);

        String out2 = template.requestBody("netty:tcp://localhost:" + port + "?textline=true&sync=true", "Camel", String.class);
        assertEquals("Bye Camel", out2);
    }

    @Test
    public void testConcurrent() throws Exception {
        int size = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(20);
        // we access the responses Map below only inside the main thread,
        // so no need for a thread-safe Map implementation
        Map<Integer, Future<String>> responses = new HashMap<Integer, Future<String>>();
        for (int i = 0; i < size; i++) {
            final int index = i;
            Future<String> out = executor.submit(new Callable<String>() {
                public String call() throws Exception {
                    String reply = template.requestBody("netty:tcp://localhost:" + port + "?textline=true&sync=true", index, String.class);
                    log.info("Sent {} received {}", index, reply);
                    assertEquals("Bye " + index, reply);
                    return reply;
                }
            });
            responses.put(index, out);
        }

        // get all responses
        Set<String> unique = new HashSet<String>();
        for (Future<String> future : responses.values()) {
            String reply = future.get(120, TimeUnit.SECONDS);
            assertNotNull("Should get a reply", reply);
            unique.add(reply);
        }

        // should be 1000 unique responses
        assertEquals("Should be " + size + " unique responses", size, unique.size());
        executor.shutdownNow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                port = AvailablePortFinder.getNextAvailable(8000);

                from("netty:tcp://localhost:" + port + "?textline=true&sync=true&reuseAddress=true&synchronous=false")
                    .to("activemq:queue:foo")
                    .log("Writing reply ${body}");

                from("activemq:queue:foo")
                    .transform(simple("Bye ${body}"));
            }
        };
    }

    @Override
    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();

        // add ActiveMQ with embedded broker
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        JmsComponent amq = jmsComponentAutoAcknowledge(connectionFactory);
        amq.setCamelContext(context);

        answer.bind("activemq", amq);
        return answer;
    }

}
