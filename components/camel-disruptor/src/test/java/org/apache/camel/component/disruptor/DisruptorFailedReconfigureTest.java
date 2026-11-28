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
package org.apache.camel.component.disruptor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.DefaultThreadPoolFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * When the new Disruptor of a reconfiguration fails to start (here the executor of the event handlers rejects them),
 * producers must fail instead of waiting forever for it, and the disruptor must work again once it is started.
 */
class DisruptorFailedReconfigureTest extends CamelTestSupport {

    private static final String URI = "disruptor:foo?multipleConsumers=true";

    private volatile boolean rejecting;
    private Thread producer;

    @AfterEach
    void stopRejecting() {
        rejecting = false;
        if (producer != null) {
            producer.interrupt();
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.getExecutorServiceManager().setThreadPoolFactory(new RejectingThreadPoolFactory());
        return camelContext;
    }

    @Test
    void producersFailWhileTheDisruptorCannotBeStarted() throws Exception {
        MockEndpoint a = getMockEndpoint("mock:a");
        a.expectedBodiesReceived("before", "after");
        template.sendBody(URI, "before");

        // adding a second consumer reconfigures the disruptor, whose new event handlers are rejected by the executor
        rejecting = true;
        assertThrows(Exception.class, () -> context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(URI).routeId("b").to("mock:b");
            }
        }));

        // a request/reply producer with a timeout fails instead of waiting for the disruptor forever
        AtomicReference<Exception> failure = new AtomicReference<>();
        producer = new Thread(() -> {
            try {
                template.requestBody(URI + "&timeout=1000", "during");
            } catch (Exception e) {
                failure.set(e);
            }
        }, "producer");
        producer.setDaemon(true);
        producer.start();
        await().atMost(20, TimeUnit.SECONDS).until(() -> !producer.isAlive());
        assertFalse(producer.isAlive());
        Exception producerFailure = failure.get();
        assertNotNull(producerFailure, "the producer did not fail");
        assertNotNull(ObjectHelper.getException(DisruptorNotStartedException.class, producerFailure),
                () -> "the producer did not fail with DisruptorNotStartedException: " + producerFailure);

        // the executor accepts the event handlers again: restarting the route starts the disruptor again
        rejecting = false;
        context.getRouteController().stopRoute("a");
        context.getRouteController().startRoute("a");
        template.sendBody(URI, "after");

        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(URI).routeId("a").to("mock:a");
            }
        };
    }

    /**
     * Creates thread pools that reject new tasks while {@link #rejecting} is set, as a pool that was shut down does.
     */
    private final class RejectingThreadPoolFactory extends DefaultThreadPoolFactory {

        @Override
        public ExecutorService newThreadPool(ThreadPoolProfile profile, ThreadFactory factory) {
            return new ThreadPoolExecutor(
                    profile.getPoolSize(), profile.getMaxPoolSize(), 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                    factory) {
                @Override
                public void execute(Runnable command) {
                    if (rejecting) {
                        throw new RejectedExecutionException("Rejected by the test");
                    }
                    super.execute(command);
                }
            };
        }
    }
}
