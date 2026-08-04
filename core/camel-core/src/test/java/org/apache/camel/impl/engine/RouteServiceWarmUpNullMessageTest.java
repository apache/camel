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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that {@link RouteService#warmUp()} and {@link RouteService#setUp()} wrap startup failures in a
 * {@link FailedToStartRouteException} whose message is always meaningful — even when the root cause exception carries a
 * {@code null} message (e.g. a bare {@link NullPointerException}).
 *
 * <p>
 * Before the fix, {@code RouteService} passed {@code e.getLocalizedMessage()} directly to the
 * {@link FailedToStartRouteException} constructor, which calls {@code Objects.requireNonNull} on that argument. A
 * message-less exception therefore caused a secondary {@link NullPointerException} to be thrown from inside the
 * exception constructor rather than a proper {@link FailedToStartRouteException}.
 *
 * <p>
 * The tests trigger the failure during endpoint initialisation (inside {@code doSetup()}), which is the code path
 * covered by the {@code RouteService} fix.
 */
class RouteServiceWarmUpNullMessageTest {

    /**
     * When the endpoint throws a message-less {@link NullPointerException} during route setup, the result must be a
     * {@link FailedToStartRouteException}, not a raw NPE.
     */
    @Test
    void testSetUpNullMessageExceptionProducesFailedToStartRouteException() {
        CamelContext context = new DefaultCamelContext();
        context.addComponent("fail", new NullMessageFailComponent());

        try {
            assertThatThrownBy(() -> {
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("fail:trigger").routeId("test-route").to("direct:out");
                    }
                });
                context.start();
            }).isInstanceOf(FailedToStartRouteException.class);
        } finally {
            try {
                context.stop();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * The {@link FailedToStartRouteException} message must contain the route id and must not use the literal string
     * "null" as the failure description.
     */
    @Test
    void testFailedToStartMessageIsNonNullAndMeaningful() {
        CamelContext context = new DefaultCamelContext();
        context.addComponent("fail", new NullMessageFailComponent());

        try {
            assertThatThrownBy(() -> {
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("fail:trigger").routeId("meaningful-route").to("direct:out");
                    }
                });
                context.start();
            }).isInstanceOf(FailedToStartRouteException.class)
                    .hasMessageContaining("meaningful-route")
                    .hasMessageNotContaining("because: null");
        } finally {
            try {
                context.stop();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * When the endpoint throws a message-less outer exception wrapping an inner exception that has a message, the inner
     * message must be surfaced in the {@link FailedToStartRouteException}.
     */
    @Test
    void testSetUpWalksCauseChainForMessage() {
        CamelContext context = new DefaultCamelContext();
        String expectedFragment = "real cause message from chain";
        context.addComponent("fail", new ChainedNullMessageFailComponent(expectedFragment));

        try {
            assertThatThrownBy(() -> {
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("fail:trigger").routeId("chain-route").to("direct:out");
                    }
                });
                context.start();
            }).isInstanceOf(FailedToStartRouteException.class)
                    .hasMessageContaining(expectedFragment);
        } finally {
            try {
                context.stop();
            } catch (Exception ignored) {
            }
        }
    }

    // ---- helpers ----

    /**
     * A component whose endpoint throws a message-less {@link NullPointerException} during its own {@code doStart()} —
     * which is invoked by {@code RouteService.doSetup()} via {@code ServiceHelper.initService(endpoint)}, exercising
     * the {@code setUp()} fix.
     */
    private static class NullMessageFailComponent extends DefaultComponent {
        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new NullMessageFailEndpoint(uri, this);
        }
    }

    private static class NullMessageFailEndpoint extends DefaultEndpoint {
        NullMessageFailEndpoint(String uri, NullMessageFailComponent component) {
            super(uri, component);
        }

        @Override
        protected void doStart() {
            throw new NullPointerException();
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            return new DefaultConsumer(this, processor) {
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

    /**
     * A component whose endpoint throws a message-less outer exception wrapping an inner exception that does have a
     * message — used to test cause-chain walking in {@code extractUsefulMessage}.
     */
    private static class ChainedNullMessageFailComponent extends DefaultComponent {
        private final String causeMessage;

        ChainedNullMessageFailComponent(String causeMessage) {
            this.causeMessage = causeMessage;
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new ChainedNullMessageFailEndpoint(uri, this, causeMessage);
        }
    }

    private static class ChainedNullMessageFailEndpoint extends DefaultEndpoint {
        private final String causeMessage;

        ChainedNullMessageFailEndpoint(String uri, ChainedNullMessageFailComponent component, String causeMessage) {
            super(uri, component);
            this.causeMessage = causeMessage;
        }

        @Override
        protected void doStart() {
            // Outer NPE has no message; initCause sets the cause without supplying a message to
            // the outer exception — forces extractUsefulMessage to walk the chain to find causeMessage.
            NullPointerException outer = new NullPointerException();
            outer.initCause(new IllegalStateException(causeMessage));
            throw outer;
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            return new DefaultConsumer(this, processor) {
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
