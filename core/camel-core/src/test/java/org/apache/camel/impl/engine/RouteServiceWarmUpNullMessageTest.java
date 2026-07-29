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

import org.apache.camel.CamelContext;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link RouteService#warmUp()} wraps startup failures in a {@link FailedToStartRouteException}
 * whose message is always meaningful — even when the root cause exception itself carries a {@code null} message
 * (e.g. a bare {@link NullPointerException}).
 *
 * <p>Before the fix, {@code RouteService} passed {@code e.getLocalizedMessage()} directly to the
 * {@link FailedToStartRouteException} constructor, which calls {@code Objects.requireNonNull} on that
 * argument. A message-less exception therefore caused a secondary {@link NullPointerException} to be thrown
 * from inside the exception constructor rather than a proper {@link FailedToStartRouteException}.
 */
public class RouteServiceWarmUpNullMessageTest {

    /**
     * When a route's consumer throws a {@link NullPointerException} with no message during warm-up, the
     * resulting {@link FailedToStartRouteException} must still carry a non-null, non-empty message.
     */
    @Test
    public void testWarmUpNullMessageExceptionProducesUsefulFailedToStartMessage() {
        CamelContext context = new DefaultCamelContext();
        // Register a component whose endpoint start throws a message-less NullPointerException
        context.addComponent("fail", new NullMessageFailComponent());

        assertThrows(FailedToStartRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("fail:trigger").routeId("test-route").to("direct:out");
                }
            });
            context.start();
        });
    }

    /**
     * The {@link FailedToStartRouteException} message must not be null, must contain the route id, and must
     * not use the literal string "null" as the failure description.
     */
    @Test
    public void testFailedToStartMessageIsNonNullAndMeaningful() {
        CamelContext context = new DefaultCamelContext();
        context.addComponent("fail", new NullMessageFailComponent());

        FailedToStartRouteException caught = null;
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("fail:trigger").routeId("meaningful-route").to("direct:out");
                }
            });
            context.start();
        } catch (FailedToStartRouteException e) {
            caught = e;
        } catch (Exception e) {
            Throwable t = e;
            while (t != null) {
                if (t instanceof FailedToStartRouteException) {
                    caught = (FailedToStartRouteException) t;
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

        assertNotNull(caught, "Expected FailedToStartRouteException");
        String message = caught.getMessage();
        assertNotNull(message, "FailedToStartRouteException message must not be null");
        assertTrue(message.contains("meaningful-route"), "Message must contain the route id");
        // The key regression guard: must NOT say "because: null"
        assertFalse(message.contains("because: null"),
                "Message must not contain 'because: null' — was: " + message);
        // The fallback must surface something useful (class name at minimum)
        assertTrue(message.length() > "Failed to start route: meaningful-route because: ".length(),
                "Message must have a non-empty failure description — was: " + message);
    }

    /**
     * Verifies that when the root exception has a null message but its cause has a real message,
     * the cause's message is surfaced in the {@link FailedToStartRouteException}.
     */
    @Test
    public void testWarmUpWalksCauseChainForMessage() {
        CamelContext context = new DefaultCamelContext();
        String expectedFragment = "real cause message from chain";
        context.addComponent("fail", new ChainedNullMessageFailComponent(expectedFragment));

        FailedToStartRouteException caught = null;
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("fail:trigger").routeId("chain-route").to("direct:out");
                }
            });
            context.start();
        } catch (FailedToStartRouteException e) {
            caught = e;
        } catch (Exception e) {
            Throwable t = e;
            while (t != null) {
                if (t instanceof FailedToStartRouteException) {
                    caught = (FailedToStartRouteException) t;
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

        assertNotNull(caught, "Expected FailedToStartRouteException");
        assertTrue(caught.getMessage().contains(expectedFragment),
                "Message should surface cause chain message — was: " + caught.getMessage());
    }

    // ---- helpers ----

    /** A component whose endpoint throws a message-less {@link NullPointerException} on start. */
    private static class NullMessageFailComponent extends DefaultComponent {
        @Override
        protected org.apache.camel.Endpoint createEndpoint(String uri, String remaining,
                java.util.Map<String, Object> parameters) {
            return new FailOnStartEndpoint(uri, this);
        }

        private static class FailOnStartEndpoint extends DefaultEndpoint {
            FailOnStartEndpoint(String uri, NullMessageFailComponent component) {
                super(uri, component);
            }

            @Override
            public org.apache.camel.Consumer createConsumer(org.apache.camel.Processor processor) {
                return new org.apache.camel.support.DefaultConsumer(this, processor) {
                    @Override
                    protected void doStart() {
                        // Throw a NullPointerException with no message — the classic null-message case
                        throw new NullPointerException();
                    }
                };
            }

            @Override
            public org.apache.camel.Producer createProducer() {
                return new org.apache.camel.support.DefaultProducer(this) {
                    @Override
                    public void process(org.apache.camel.Exchange exchange) {
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
     * A component whose endpoint throws a message-less outer exception wrapping an inner exception that
     * does have a message — used to test cause-chain walking.
     */
    private static class ChainedNullMessageFailComponent extends DefaultComponent {
        private final String causeMessage;

        ChainedNullMessageFailComponent(String causeMessage) {
            this.causeMessage = causeMessage;
        }

        @Override
        protected org.apache.camel.Endpoint createEndpoint(String uri, String remaining,
                java.util.Map<String, Object> parameters) {
            return new FailOnStartEndpoint(uri, this, causeMessage);
        }

        private static class FailOnStartEndpoint extends DefaultEndpoint {
            private final String causeMessage;

            FailOnStartEndpoint(String uri, ChainedNullMessageFailComponent component, String causeMessage) {
                super(uri, component);
                this.causeMessage = causeMessage;
            }

            @Override
            public org.apache.camel.Consumer createConsumer(org.apache.camel.Processor processor) {
                return new org.apache.camel.support.DefaultConsumer(this, processor) {
                    @Override
                    protected void doStart() {
                        // Outer exception has no message; inner cause has the real message
                        throw new RuntimeException(new IllegalStateException(causeMessage));
                    }
                };
            }

            @Override
            public org.apache.camel.Producer createProducer() {
                return new org.apache.camel.support.DefaultProducer(this) {
                    @Override
                    public void process(org.apache.camel.Exchange exchange) {
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
