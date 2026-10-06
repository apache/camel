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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Consumer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsConsumer;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.throttling.ThrottlingExceptionRoutePolicy;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A route policy suspends the consumer when an exchange is done, which for a synchronous sjms consumer runs on the
 * thread of the JMS message listener, or on a thread the listener waits for. Connection.stop() waits for the message
 * listeners in progress and a message listener must not call it (the JMS provider throws an IllegalStateException), so
 * the consumer must stop receiving without the suspend waiting for the listener.
 */
class SjmsConsumerRoutePolicyTest extends JmsTestSupport {

    private static final String QUEUE = "sjms:queue:SjmsConsumerRoutePolicyTest";

    private final ThrottlingExceptionRoutePolicy policy = new ThrottlingExceptionRoutePolicy(1, 60000, 60000, null, false);
    private final ThrottlingExceptionRoutePolicy blockingPolicy
            = new ThrottlingExceptionRoutePolicy(1, 60000, 60000, null, false);
    private final ThrottlingExceptionRoutePolicy threadsPolicy
            = new ThrottlingExceptionRoutePolicy(1, 60000, 60000, null, false);
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @Test
    void testCircuitBreakerSuspendsConsumer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        Consumer consumer = context.getRoute("breaker").getConsumer();
        SimpleMessageListenerContainer container
                = (SimpleMessageListenerContainer) ((SjmsConsumer) consumer).getListenerContainer();

        // the failure opens the circuit on the thread of the message listener
        template.sendBody(QUEUE, "Kaboom");
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> ((ServiceSupport) consumer).isSuspended() && !container.isSuspendResumePending());

        mock.expectedMessageCount(0);
        mock.setAssertPeriod(1000);
        template.sendBody(QUEUE, "Hello World");
        mock.assertIsSatisfied();

        mock.reset();
        mock.expectedBodiesReceived("Hello World");
        ServiceHelper.resumeService(consumer);
        mock.assertIsSatisfied();
    }

    @Test
    void testSuspendWhileThePolicySuspends() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:blocking");
        Consumer consumer = context.getRoute("blocking").getConsumer();
        SimpleMessageListenerContainer container
                = (SimpleMessageListenerContainer) ((SjmsConsumer) consumer).getListenerContainer();

        template.sendBody(QUEUE + "Blocking", "Kaboom");
        assertTrue(entered.await(20, TimeUnit.SECONDS));

        // the consumer is suspended (graceful shutdown, JMX) while the exchange is in flight, then the exchange fails
        // and the policy opens the circuit, which suspends the consumer on the thread of the message listener
        CompletableFuture<Boolean> suspend = CompletableFuture.supplyAsync(() -> ServiceHelper.suspendService(consumer));
        await().atMost(20, TimeUnit.SECONDS).until(((ServiceSupport) consumer)::isSuspendingOrSuspended);
        release.countDown();
        // must not wait for the message listener, which waits for the lock of the consumer
        assertTrue(suspend.get(5, TimeUnit.SECONDS));
        await().atMost(20, TimeUnit.SECONDS).until(() -> !container.isSuspendResumePending());

        mock.expectedMessageCount(0);
        mock.setAssertPeriod(1000);
        template.sendBody(QUEUE + "Blocking", "Hello World");
        mock.assertIsSatisfied();

        mock.reset();
        mock.expectedBodiesReceived("Hello World");
        ServiceHelper.resumeService(consumer);
        mock.assertIsSatisfied();
    }

    @Test
    void testCircuitBreakerOnAnotherThread() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:threads");
        Consumer consumer = context.getRoute("threads").getConsumer();
        SimpleMessageListenerContainer container
                = (SimpleMessageListenerContainer) ((SjmsConsumer) consumer).getListenerContainer();

        // the route continues on another thread, which opens the circuit while the message listener waits for it
        template.sendBody(QUEUE + "Threads", "Kaboom");
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> ((ServiceSupport) consumer).isSuspended() && !container.isSuspendResumePending());

        mock.expectedMessageCount(0);
        mock.setAssertPeriod(1000);
        template.sendBody(QUEUE + "Threads", "Hello World");
        mock.assertIsSatisfied();

        mock.reset();
        mock.expectedBodiesReceived("Hello World");
        ServiceHelper.resumeService(consumer);
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(QUEUE).routeId("breaker").routePolicy(policy)
                        .process(e -> {
                            if ("Kaboom".equals(e.getMessage().getBody(String.class))) {
                                throw new IllegalArgumentException("Forced");
                            }
                        })
                        .to("mock:result");
                from(QUEUE + "Blocking").routeId("blocking").routePolicy(blockingPolicy)
                        .process(e -> {
                            if ("Kaboom".equals(e.getMessage().getBody(String.class))) {
                                entered.countDown();
                                release.await(20, TimeUnit.SECONDS);
                                throw new IllegalArgumentException("Forced");
                            }
                        })
                        .to("mock:blocking");
                from(QUEUE + "Threads").routeId("threads").routePolicy(threadsPolicy)
                        .threads(1)
                        .process(e -> {
                            if ("Kaboom".equals(e.getMessage().getBody(String.class))) {
                                throw new IllegalArgumentException("Forced");
                            }
                        })
                        .to("mock:threads");
            }
        };
    }

}
