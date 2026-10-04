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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Consumer;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsConsumer;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A suspended sjms route must not consume, and must consume again when resumed. The route policies (throttling inflight
 * / exception, master, scheduled) suspend and resume the consumer, and JMX can stop and start the same consumer
 * instance.
 */
class SjmsConsumerRestartTest extends JmsTestSupport {

    private static final String QUEUE = "sjms:queue:SjmsConsumerRestartTest.";

    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger processed = new AtomicInteger();

    @Test
    void testSuspendResumeRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:suspend");
        mock.expectedBodiesReceived("Hello World");
        template.sendBody(QUEUE + "suspend", "Hello World");
        mock.assertIsSatisfied();

        context.getRouteController().suspendRoute("suspend");
        assertEquals(ServiceStatus.Suspended, context.getRouteController().getRouteStatus("suspend"));
        // the connection is stopped by another thread
        awaitSuspendResume("suspend");

        mock.reset();
        mock.expectedMessageCount(0);
        mock.setAssertPeriod(1000);
        template.sendBody(QUEUE + "suspend", "Bye World");
        mock.assertIsSatisfied();

        mock.reset();
        mock.expectedBodiesReceived("Bye World");
        context.getRouteController().resumeRoute("suspend");
        mock.assertIsSatisfied();
    }

    @Test
    void testSuspendResumeConsumer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:policy");

        // what RoutePolicySupport.suspendOrStopConsumer and resumeOrStartConsumer do
        Consumer consumer = context.getRoute("policy").getConsumer();
        ServiceHelper.suspendService(consumer);
        awaitSuspendResume("policy");

        mock.expectedMessageCount(0);
        mock.setAssertPeriod(1000);
        template.sendBody(QUEUE + "policy", "Hello World");
        mock.assertIsSatisfied();

        mock.reset();
        mock.expectedBodiesReceived("Hello World", "Bye World");
        ServiceHelper.resumeService(consumer);
        template.sendBody(QUEUE + "policy", "Bye World");
        mock.assertIsSatisfied();
    }

    @Test
    void testStopStartConsumer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:restart");

        // the same consumer instance, as with the stop and start operations of the managed consumer (JMX)
        Consumer consumer = context.getRoute("restart").getConsumer();
        for (int i = 0; i < 2; i++) {
            ServiceHelper.stopService(consumer);
            ServiceHelper.startService(consumer);
        }

        mock.expectedBodiesReceived("Hello World", "Bye World");
        template.sendBody(QUEUE + "restart", "Hello World");
        template.sendBody(QUEUE + "restart", "Bye World");
        mock.assertIsSatisfied();
    }

    @Test
    void testGracefulStopTakesNoNewMessage() throws Exception {
        // the graceful shutdown suspends the consumer, then waits for the inflight exchange
        template.sendBody(QUEUE + "shutdown", "A");
        assertTrue(entered.await(20, TimeUnit.SECONDS));

        CompletableFuture<Void> stop = CompletableFuture.runAsync(() -> {
            try {
                context.getRouteController().stopRoute("shutdown", 20, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ServiceSupport consumer = (ServiceSupport) context.getRoute("shutdown").getConsumer();
        SimpleMessageListenerContainer container = container("shutdown");
        // the stop of the connection begins (and then waits for the listener of A)
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> consumer.isSuspendingOrSuspended() && !container.isConnectionStarted());
        template.sendBody(QUEUE + "shutdown", "B");
        release.countDown();
        stop.get(30, TimeUnit.SECONDS);

        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("shutdown"));
        assertEquals(1, processed.get(), "A message sent after the route began to stop must not be consumed");
    }

    @Test
    void testGracefulStopAsyncReply() throws Exception {
        // with asyncConsumer the message listener returns before the exchange is done, and the reply is sent with the
        // session of the consumer when it is done: suspending the consumer must keep that session open
        CompletableFuture<Object> reply = template.asyncRequestBody(QUEUE + "async?requestTimeout=10000", "A");
        assertTrue(entered.await(20, TimeUnit.SECONDS));

        CompletableFuture<Void> stop = CompletableFuture.runAsync(() -> {
            try {
                context.getRouteController().stopRoute("async", 20, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ServiceSupport consumer = (ServiceSupport) context.getRoute("async").getConsumer();
        await().atMost(20, TimeUnit.SECONDS).until(consumer::isSuspendingOrSuspended);
        release.countDown();

        assertEquals("Bye A", reply.get(30, TimeUnit.SECONDS));
        stop.get(30, TimeUnit.SECONDS);
    }

    @Test
    void testStopStartRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:route");

        // a route restart creates a new consumer
        context.getRouteController().stopRoute("route");
        context.getRouteController().startRoute("route");

        mock.expectedBodiesReceived("Hello World");
        template.sendBody(QUEUE + "route", "Hello World");
        mock.assertIsSatisfied();
    }

    private SimpleMessageListenerContainer container(String routeId) {
        return (SimpleMessageListenerContainer) ((SjmsConsumer) context.getRoute(routeId).getConsumer())
                .getListenerContainer();
    }

    private void awaitSuspendResume(String routeId) {
        SimpleMessageListenerContainer container = container(routeId);
        await().atMost(20, TimeUnit.SECONDS).until(() -> !container.isSuspendResumePending());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(QUEUE + "suspend").routeId("suspend").to("mock:suspend");
                from(QUEUE + "policy").routeId("policy").to("mock:policy");
                from(QUEUE + "restart").routeId("restart").to("mock:restart");
                from(QUEUE + "route").routeId("route").to("mock:route");
                from(QUEUE + "async?asyncConsumer=true").routeId("async")
                        .threads(1)
                        .process(e -> {
                            entered.countDown();
                            release.await(20, TimeUnit.SECONDS);
                        })
                        .transform(body().prepend("Bye "));
                from(QUEUE + "shutdown").routeId("shutdown")
                        .process(e -> {
                            processed.incrementAndGet();
                            entered.countDown();
                            release.await(20, TimeUnit.SECONDS);
                        });
            }
        };
    }

}
