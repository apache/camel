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
package org.apache.camel.component.jms;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.apache.camel.test.junit5.TestSupport.body;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reliability tests for JMS TempQueue Reply Manager with multiple consumers.
 */
@Isolated("Creates multiple threads")
public class JmsRequestReplyTempQueueMultipleConsumersTest extends CamelTestSupport {

    @RegisterExtension
    public ArtemisService service = ArtemisServiceFactory.createVMService();

    private final Map<String, AtomicInteger> msgsPerThread = new ConcurrentHashMap<>();
    private JmsPoolConnectionFactory connectionFactory;
    private ExecutorService executorService;

    @Test
    public void testMultipleConsumingThreads() throws Exception {
        executorService = context.getExecutorServiceManager().newFixedThreadPool(this, "test", 5);

        doSendMessages(1000);

        assertTrue(msgsPerThread.keySet().size() > 1,
                "Expected multiple consuming threads, but only found: " + msgsPerThread.keySet().size());

        context.getExecutorServiceManager().shutdown(executorService);
    }

    @ParameterizedTest
    @ValueSource(ints = { 500, 100, 100 })
    @Disabled("This will spam logs with Session is closed")
    public void testTempQueueRefreshed(int numFiles) throws Exception {
        executorService = context.getExecutorServiceManager().newFixedThreadPool(this, "test", 5);

        doSendMessages(numFiles);
        connectionFactory.clear();

        context.getExecutorServiceManager().shutdown(executorService);
    }

    private void doSendMessages(int files) throws Exception {
        MockEndpoint.resetMocks(context);
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(files);
        mockEndpoint.expectsNoDuplicates(body());

        for (int i = 0; i < files; i++) {
            final int index = i;
            executorService.submit(() -> {
                template.sendBody("direct:start", "Message " + index);
                return null;
            });
        }

        MockEndpoint.assertIsSatisfied(context, 40, TimeUnit.SECONDS);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        connectionFactory = CamelJmsTestHelper.createPooledPersistentConnectionFactory(service.serviceAddress());
        camelContext.addComponent("jms", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(ExchangePattern.InOut,
                        "jms:queue:JmsRequestReplyTempQueueMultipleConsumersTest?replyToConcurrentConsumers=10&replyToMaxConcurrentConsumers=20&recoveryInterval=10")
                        .process(exchange -> {
                            String threadName = Thread.currentThread().getName();
                            synchronized (msgsPerThread) {
                                AtomicInteger count = msgsPerThread.get(threadName);
                                if (count == null) {
                                    count = new AtomicInteger();
                                    msgsPerThread.put(threadName, count);
                                }
                                count.incrementAndGet();
                            }
                        }).to("mock:result");

                from("jms:queue:JmsRequestReplyTempQueueMultipleConsumersTest?concurrentConsumers=10&recoveryInterval=10")
                        .setBody(simple("Reply >>> ${body}"));
            }
        };
    }
}
