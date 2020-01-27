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
package org.apache.camel.component.sjms.consumer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.Test;

/**
 * Concurrent consumer with JMSReply test.
 */
public class InOutConcurrentConsumerTest extends JmsTestSupport {

    @EndpointInject("mock:result")
    MockEndpoint result;

    @Test
    public void testConcurrent() throws Exception {
        doSendMessages(10, 5);
    }

    private void doSendMessages(int messages, int poolSize) throws Exception {

        result.expectedMessageCount(messages);
        result.expectsNoDuplicates(body());

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        final List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < messages; i++) {
            final int index = i;
            Future<String> out = executor.submit(() -> template.requestBody("direct:start", "Message " + index, String.class));
            futures.add(out);
        }

        assertMockEndpointsSatisfied();

        for (int i = 0; i < futures.size(); i++) {
            Object out = futures.get(i).get();
            assertEquals("Bye Message " + i, out);
        }
        executor.shutdownNow();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return super.createCamelContext();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start")
                    .to("sjms:a?consumerCount=5&exchangePattern=InOut&namedReplyTo=myResponse")
                    .to("mock:result");

                from("sjms:a?consumerCount=5&exchangePattern=InOut&namedReplyTo=myResponse")
                    .process(exchange -> {
                        String body = exchange.getIn().getBody(String.class);
                        // sleep a little to simulate heavy work and force concurrency processing
                        Thread.sleep(1000);
                        exchange.getMessage().setBody("Bye " + body);
                        exchange.getMessage().setHeader("threadName", Thread.currentThread().getName());
                    });
            }
        };
    }

}

