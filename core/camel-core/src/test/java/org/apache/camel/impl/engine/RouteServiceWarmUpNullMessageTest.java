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

import org.apache.camel.CamelContext;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that {@link RouteService#warmUp()} wraps startup failures in a {@link FailedToStartRouteException} whose
 * message is always meaningful — even when the root cause exception itself carries a {@code null} message (e.g. a bare
 * {@link NullPointerException}).
 *
 * <p>
 * Before the fix, {@code RouteService} passed {@code e.getLocalizedMessage()} directly to the
 * {@link FailedToStartRouteException} constructor, which calls {@code Objects.requireNonNull} on that argument. A
 * message-less exception therefore caused a secondary {@link NullPointerException} to be thrown from inside the
 * exception constructor rather than a proper {@link FailedToStartRouteException}.
 */
class RouteServiceWarmUpNullMessageTest {

    /**
     * When a route's consumer throws a {@link NullPointerException} with no message during warm-up, the resulting
     * {@link FailedToStartRouteException} must still carry a non-null, non-empty message.
     */
    @Test
    void testWarmUpNullMessageExceptionProducesUsefulFailedToStartMessage() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addComponent("fail", new NullMessageFailComponent());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("fail:trigger").routeId("test-route").to("direct:out");
            }
        });

        assertThatThrownBy(context::start)
                .isInstanceOf(FailedToStartRouteException.class);
    }

    /**
     * The {@link FailedToStartRouteException} message must not be null, must contain the route id, and must not use the
     * literal string "null" as the failure description.
     */
    @Test
    void testFailedToStartMessageIsNonNullAndMeaningful() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addComponent("fail", new NullMessageFailComponent());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("fail:trigger").routeId("meaningful-route").to("direct:out");
            }
        });

        FailedToStartRouteException caught = null;
        try {
            context.start();
        } catch (FailedToStartRouteException e) {
            caught = e;
        } catch (Exception e) {
            Throwable t = e;
            while (t != null) {
                if (t instanceof FailedToStartRouteException ftsre) {
                    caught = ftsre;
                    break;
                }
                t = t.getCause();
            }
        } finally {
            try {
                context.stop();
            } catch (Exception ignored) {
            }
        }

        assertThat(caught).as("Expected FailedToStartRouteException").isNotNull();
        assertThat(caught.getMessage())
                .as("FailedToStartRouteException message must not be null")
                .isNotNull()
                .as("Message must contain the route id")
                .contains("meaningful-route")
                .as("Message must not contain 'because: null'")
                .doesNotContain("because: null");
    }

    /**
     * Verifies that when the root exception has a null message but its cause has a real message, the cause's message is
     * surfaced in the {@link FailedToStartRouteException}.
     */
    @Test
    void testWarmUpWalksCauseChainForMessage() throws Exception {
        CamelContext context = new DefaultCamelContext();
        String expectedFragment = "real cause message from chain";
        context.addComponent("fail", new ChainedNullMessageFailComponent(expectedFragment));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("fail:trigger").routeId("chain-route").to("direct:out");
            }
        });

        FailedToStartRouteException caught = null;
        try {
            context.start();
        } catch (FailedToStartRouteException e) {
            caught = e;
        } catch (Exception e) {
            Throwable t = e;
            while (t != null) {
                if (t instanceof FailedToStartRouteException ftsre) {
                    caught = ftsre;
                    break;
                }
                t = t.getCause();
            }
        } finally {
            try {
                context.stop();
            } catch (Exception ignored) {
            }
        }

        assertThat(caught).as("Expected FailedToStartRouteException").isNotNull();
        assertThat(caught.getMessage())
                .as("Message should surface cause chain message")
                .contains(expectedFragment);
    }

    // ---- helpers ----

    /** A component whose endpoint throws a message-less {@link NullPointerException} on start. */
    private static class NullMessageFailComponent extends DefaultComponent {
        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new FailOnStartEndpoint(uri, this);
        }

        private static class FailOnStartEndpoint extends DefaultEndpoint {
            FailOnStartEndpoint(String uri, NullMessageFailComponent component) {
                super(uri, component);
            }

            @Override
            public Consumer createConsumer(Processor processor) {
                return new DefaultConsumer(this, processor) {
                    @Override
                    protected void doStart() {
                        throw new NullPointerException();
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

    /**
     * A component whose endpoint throws a message-less outer exception wrapping an inner exception that does have a
     * message — used to test cause-chain walking.
     */
    private static class ChainedNullMessageFailComponent extends DefaultComponent {
        private final String causeMessage;

        ChainedNullMessageFailComponent(String causeMessage) {
            this.causeMessage = causeMessage;
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new FailOnStartEndpoint(uri, this, causeMessage);
        }

        private static class FailOnStartEndpoint extends DefaultEndpoint {
            private final String causeMessage;

            FailOnStartEndpoint(String uri, ChainedNullMessageFailComponent component, String causeMessage) {
                super(uri, component);
                this.causeMessage = causeMessage;
            }

            @Override
            public Consumer createConsumer(Processor processor) {
                return new DefaultConsumer(this, processor) {
                    @Override
                    protected void doStart() {
                        throw new RuntimeException(new IllegalStateException(causeMessage));
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
