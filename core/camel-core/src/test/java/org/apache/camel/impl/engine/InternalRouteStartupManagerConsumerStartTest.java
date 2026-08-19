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
package org.apache.camel.impl.engine;

import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link InternalRouteStartupManager} wraps consumer startup failures in a
 * {@link FailedToStartRouteException} with a meaningful (non-null) message, even when the root cause carries no message
 * (e.g. a bare {@link NullPointerException}).
 *
 * <p>
 * Before the fix, the two {@code throw e} sites in {@code doStartOrResumeRouteConsumers()} re-threw the raw exception
 * without any wrapping, causing a bare NPE to escape directly to the caller instead of a proper
 * {@link FailedToStartRouteException}.
 *
 * <p>
 * Reproducer: use a consumer whose {@code start()} throws a message-less {@link NullPointerException}, matching the
 * real-world scenario where e.g. {@code FileConsumer.doStart()} throws NPE and it propagates through
 * {@code BaseService.start()} to {@code InternalRouteStartupManager.doStartOrResumeRouteConsumers()} line 429.
 */
public class InternalRouteStartupManagerConsumerStartTest {

    /**
     * A bare {@link NullPointerException} (no message) thrown when the consumer's {@code start()} is called by
     * {@link InternalRouteStartupManager} must be wrapped in a {@link FailedToStartRouteException} with a non-null
     * message, not re-thrown as a raw NPE.
     */
    @Test
    public void testConsumerStartNullMessageProducesFailedToStartRouteException() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        context.addComponent("failstart", new ConsumerStartFailComponent(new NullPointerException()));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("failstart:trigger").routeId("consumer-start-route").to("direct:out");
            }
        });

        FailedToStartRouteException caught = null;
        try {
            context.start();
        } catch (FailedToStartRouteException e) {
            caught = e;
        } finally {
            try {
                context.stop();
            } catch (Exception ignored) {
            }
        }

        assertNotNull(caught, "Expected FailedToStartRouteException from InternalRouteStartupManager — "
                              + "raw NPE must not escape unwrapped");
        String message = caught.getMessage();
        assertNotNull(message, "FailedToStartRouteException message must not be null");
        assertFalse(message.contains("because: null"),
                "Message must not contain 'because: null' - was: " + message);
        assertTrue(message.contains("consumer-start-route"),
                "Message must contain the route id - was: " + message);
    }

    /**
     * When the consumer startup exception has no message but a cause does, the cause message must be surfaced in the
     * {@link FailedToStartRouteException} rather than falling back to a generic class name.
     */
    @Test
    public void testConsumerStartWalksCauseChainForMessage() throws Exception {
        String expectedFragment = "real cause from consumer start";
        RuntimeException chainedException = new RuntimeException(new IllegalStateException(expectedFragment));

        DefaultCamelContext context = new DefaultCamelContext();
        context.addComponent("failstart", new ConsumerStartFailComponent(chainedException));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("failstart:trigger").routeId("consumer-chain-route").to("direct:out");
            }
        });

        FailedToStartRouteException caught = null;
        try {
            context.start();
        } catch (FailedToStartRouteException e) {
            caught = e;
        } finally {
            try {
                context.stop();
            } catch (Exception ignored) {
            }
        }

        assertNotNull(caught, "Expected FailedToStartRouteException");
        assertTrue(caught.getMessage().contains(expectedFragment),
                "Message should surface cause-chain message - was: " + caught.getMessage());
    }

    // ---- helpers ----

    /**
     * Component whose consumer throws the given {@link RuntimeException} from {@link Consumer#start()}, simulating a
     * consumer that fails during startup (e.g. FileConsumer throwing NPE from doStart which propagates through
     * BaseService.start to InternalRouteStartupManager line 429).
     */
    private static class ConsumerStartFailComponent extends DefaultComponent {
        private final RuntimeException toThrow;

        ConsumerStartFailComponent(RuntimeException toThrow) {
            this.toThrow = toThrow;
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new FailStartEndpoint(uri, this, toThrow);
        }

        private static class FailStartEndpoint extends DefaultEndpoint {
            private final RuntimeException toThrow;

            FailStartEndpoint(String uri, ConsumerStartFailComponent component, RuntimeException toThrow) {
                super(uri, component);
                this.toThrow = toThrow;
            }

            @Override
            public Consumer createConsumer(Processor processor) {
                return new DefaultConsumer(this, processor) {
                    @Override
                    public void start() {
                        // Override start() directly (not doStart()) so the exception is not swallowed
                        // by BaseService's catch-and-fail mechanism. This matches the real-world scenario
                        // where an exception propagates through BaseService.start() to
                        // InternalRouteStartupManager.doStartOrResumeRouteConsumers() line 429.
                        throw toThrow;
                    }
                };
            }

            @Override
            public Producer createProducer() {
                return new DefaultProducer(this) {
                    @Override
                    public void process(Exchange exchange) {
                    }
                };
            }

            @Override
            public boolean isSingleton() {
                return true;
            }
        }
    }
}
