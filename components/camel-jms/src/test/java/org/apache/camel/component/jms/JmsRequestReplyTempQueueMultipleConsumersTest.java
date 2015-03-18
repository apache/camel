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
package org.apache.camel.component.jms;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.pool.PooledConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * Reliability tests for JMS TempQueue Reply Manager with multiple consumers.
 */
public class JmsRequestReplyTempQueueMultipleConsumersTest extends CamelTestSupport {

    private final Map<String, AtomicInteger> msgsPerThread = new ConcurrentHashMap<String, AtomicInteger>();
    private PooledConnectionFactory connectionFactory;
    
    @Test
    public void testMultipleConsumingThreads() throws Exception {
        doSendMessages(1000, 5);
        assertTrue("Expected multiple consuming threads, but only found: " +  msgsPerThread.keySet().size(), 
                msgsPerThread.keySet().size() > 1);
    }
    
    @Test
    public void testTempQueueRefreshed() throws Exception {
        doSendMessages(500, 5);
        connectionFactory.clear();
        doSendMessages(100, 5);
        connectionFactory.clear();
        doSendMessages(100, 5);
        connectionFactory.clear();
        doSendMessages(100, 5);
    }

    private void doSendMessages(int files, int poolSize) throws Exception {
        resetMocks();
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(files);
        mockEndpoint.expectsNoDuplicates(body());

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        for (int i = 0; i < files; i++) {
            final int index = i;
            executor.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    template.sendBody("seda:start", "Message " + index);
                    return null;
                }
            });
        }

        assertMockEndpointsSatisfied(20, TimeUnit.SECONDS);
        executor.shutdownNow();
    }
    
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        
        connectionFactory = CamelJmsTestHelper.createPooledConnectionFactory();
        camelContext.addComponent("jms", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start").inOut("jms:queue:foo?replyToConcurrentConsumers=10&replyToMaxConcurrentConsumers=20&recoveryInterval=10").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        String threadName = Thread.currentThread().getName();
                        synchronized (msgsPerThread) {
                            AtomicInteger count = msgsPerThread.get(threadName);
                            if (count == null) {
                                count = new AtomicInteger(0);
                                msgsPerThread.put(threadName, count);
                            }
                            count.incrementAndGet();
                        }
                    }
                }).to("mock:result");

                from("jms:queue:foo?concurrentConsumers=20&recoveryInterval=10").setBody(simple("Reply >>> ${body}"));
            }
        };
    }
    
}
