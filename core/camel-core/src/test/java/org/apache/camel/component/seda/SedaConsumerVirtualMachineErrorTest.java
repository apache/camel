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
package org.apache.camel.component.seda;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that the SEDA consumer thread survives a {@link VirtualMachineError} thrown during exchange processing.
 * <p>
 * Before CAMEL-27471 the two {@code catch (Exception)} blocks in {@code SedaConsumer.doRun()} did not catch
 * {@code Error}. A {@code StackOverflowError} escaping from {@code processPolledExchange} was not caught, killed the
 * consumer thread, and left the queue permanently unread while the route stayed {@code Started}.
 * <p>
 * The test overrides {@code createNewConsumer} on the endpoint and {@code processPolledExchange} on the consumer to
 * throw {@code StackOverflowError} unconditionally on the first invocation. This directly exercises the
 * {@code catch (Error)} guards added to {@code doRun()}, bypassing the async routing layers that handle the callback
 * before rethrowing in a standard route (which would not expose the bug at this level).
 */
class SedaConsumerVirtualMachineErrorTest extends ContextTestSupport {

    private static final AtomicInteger processedCount = new AtomicInteger();
    private static final AtomicInteger callCount = new AtomicInteger();

    /**
     * Returns a {@link SedaEndpoint} whose {@link SedaEndpoint#createNewConsumer} injects our faulty consumer. The
     * endpoint uses a {@link LinkedBlockingQueueFactory} regardless of which {@code createEndpoint} variant was
     * triggered, so both paths (queue-factory and direct-queue) produce the same injectable endpoint.
     */
    private static SedaEndpoint faultyEndpoint(String uri, Component component, int consumers) {
        return new SedaEndpoint(uri, component, new LinkedBlockingQueueFactory<>(), consumers) {
            @Override
            protected SedaConsumer createNewConsumer(Processor processor) {
                return new SedaConsumer(this, processor) {
                    @Override
                    protected void processPolledExchange(Exchange exchange) {
                        if (callCount.getAndIncrement() == 0) {
                            // On the first exchange: throw StackOverflowError directly out of
                            // processPolledExchange so it escapes to the doRun() catch blocks.
                            // This reproduces the CAMEL-27471 scenario without depending on the
                            // async routing engine's callback-in-finally pattern.
                            throw new StackOverflowError("simulated StackOverflowError for CAMEL-27471");
                        }
                        processedCount.incrementAndGet();
                    }
                };
            }
        };
    }

    @Test
    void testConsumerSurvivesStackOverflowError() throws Exception {
        processedCount.set(0);
        callCount.set(0);

        // Send 5 messages; the consumer will throw StackOverflowError on the first and count the rest
        for (int i = 0; i < 5; i++) {
            template.sendBody("seda:in", "msg" + i);
        }

        // With the fix: the consumer thread catches the StackOverflowError thrown by processPolledExchange
        // on the first call and continues the polling loop. The remaining 4 exchanges are counted.
        // Without the fix: the thread dies after the StackOverflowError, processedCount stays 0 forever.
        await().atMost(10, TimeUnit.SECONDS).until(() -> processedCount.get() >= 4);

        assertEquals(4, processedCount.get(),
                "Consumer thread must survive StackOverflowError and drain the remaining 4 exchanges");
        assertEquals(0, context.getEndpoint("seda:in", SedaEndpoint.class).getQueue().size(),
                "SEDA queue must be empty after all exchanges are consumed");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext ctx = super.createCamelContext();
        // Replace the seda component to inject a faulty endpoint that overrides createNewConsumer.
        // Both createEndpoint variants are overridden because which one is called depends on
        // whether a 'queue' or 'queueFactory' parameter is present in the endpoint URI
        // (the default path goes through the queueFactory variant).
        ctx.addComponent("seda", new SedaComponent() {
            @Override
            protected SedaEndpoint createEndpoint(
                    String endpointUri, Component component,
                    BlockingQueueFactory<Exchange> queueFactory, int concurrentConsumers) {
                return faultyEndpoint(endpointUri, component, concurrentConsumers);
            }

            @Override
            protected SedaEndpoint createEndpoint(
                    String endpointUri, Component component,
                    BlockingQueue<Exchange> queue, int concurrentConsumers) {
                return faultyEndpoint(endpointUri, component, concurrentConsumers);
            }
        });
        return ctx;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:in").to("mock:result");
            }
        };
    }
}
