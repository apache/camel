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
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.RoutePolicySupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that {@link InternalRouteStartupManager} wraps consumer startup failures in a
 * {@link FailedToStartRouteException} with a meaningful (non-null) message, even when the root cause carries no message
 * (e.g. a bare {@link NullPointerException}).
 *
 * <p>
 * Before the fix, the two catch sites in {@code doStartOrResumeRouteConsumers()} re-threw the raw exception without any
 * wrapping, causing a bare NPE to escape directly to the caller instead of a proper
 * {@link FailedToStartRouteException}.
 */
class InternalRouteStartupManagerConsumerStartTest {

    /**
     * A bare {@link NullPointerException} (no message) thrown from the consumer's {@code start()} must be wrapped in a
     * {@link FailedToStartRouteException} with a non-null message containing the route id.
     */
    @Test
    void testConsumerStartNullMessageProducesFailedToStartRouteException() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        context.addComponent("failstart", new ConsumerStartFailComponent(new NullPointerException()));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("failstart:trigger").routeId("consumer-start-route").to("direct:out");
            }
        });

        assertThatThrownBy(() -> context.start())
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("consumer-start-route")
                .hasMessageNotContaining("because: null");

        context.stop();
    }

    /**
     * When the consumer startup exception has no message but a cause does, the cause message must be surfaced in the
     * {@link FailedToStartRouteException} rather than falling back to a generic class name.
     */
    @Test
    void testConsumerStartWalksCauseChainForMessage() throws Exception {
        String expectedFragment = "real cause from consumer start";
        RuntimeException chainedException = new RuntimeException((String) null, new IllegalStateException(expectedFragment));

        DefaultCamelContext context = new DefaultCamelContext();
        context.addComponent("failstart", new ConsumerStartFailComponent(chainedException));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("failstart:trigger").routeId("consumer-chain-route").to("direct:out");
            }
        });

        assertThatThrownBy(() -> context.start())
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining(expectedFragment);

        context.stop();
    }

    /**
     * A bare {@link NullPointerException} thrown from the consumer's {@code doStart()} (as opposed to {@code start()})
     * must also be wrapped in a {@link FailedToStartRouteException}. This exercises the first catch site via
     * {@code camelContext.startService(consumer)}, reaching the consumer through {@code BaseService.start()} →
     * {@code doStart()}.
     */
    @Test
    void testRouteServiceStartNullMessageProducesFailedToStartRouteException() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        context.addComponent("failstart", new RouteServiceStartFailComponent(new NullPointerException()));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("failstart:trigger").routeId("route-service-start-route").to("direct:out");
            }
        });

        assertThatThrownBy(() -> context.start())
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("route-service-start-route")
                .hasMessageNotContaining("because: null");

        context.stop();
    }

    /**
     * A {@link RoutePolicy#onStart} failure exercises the second catch site in {@code doStartOrResumeRouteConsumers()}
     * via {@code routeService.start()}, which calls {@code DefaultRoute.doStart()} →
     * {@code routePolicyCallback(RoutePolicy::onStart)}. The consumer starts successfully; only the route-level policy
     * callback throws, so this cannot be caught by the first catch site.
     */
    @Test
    void testRoutePolicyOnStartProducesFailedToStartRouteException() throws Exception {
        RuntimeException cause = new NullPointerException();

        DefaultCamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:trigger").routeId("policy-fail-route")
                        .routePolicy(new RoutePolicySupport() {
                            @Override
                            public void onStart(Route route) {
                                throw cause;
                            }
                        })
                        .to("mock:out");
            }
        });

        assertThatThrownBy(() -> context.start())
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("policy-fail-route")
                .hasMessageNotContaining("because: null");

        context.stop();
    }

    // ---- helpers ----

    /**
     * Component whose consumer throws from {@link Consumer#start()}, exercising the first catch site in
     * {@code doStartOrResumeRouteConsumers()}.
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

    /**
     * Component whose consumer throws from {@link org.apache.camel.support.service.BaseService#doStart()}, still
     * exercising the first catch site in {@code doStartOrResumeRouteConsumers()} via
     * {@code camelContext.startService(consumer)} → {@code BaseService.start()} → {@code doStart()}. Covers the
     * {@code doStart()} override path as distinct from the {@code start()} override in
     * {@link ConsumerStartFailComponent}.
     */
    private static class RouteServiceStartFailComponent extends DefaultComponent {
        private final RuntimeException toThrow;

        RouteServiceStartFailComponent(RuntimeException toThrow) {
            this.toThrow = toThrow;
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new FailStartEndpoint(uri, this, toThrow);
        }

        private static class FailStartEndpoint extends DefaultEndpoint {
            private final RuntimeException toThrow;

            FailStartEndpoint(String uri, RouteServiceStartFailComponent component, RuntimeException toThrow) {
                super(uri, component);
                this.toThrow = toThrow;
            }

            @Override
            public Consumer createConsumer(Processor processor) {
                return new DefaultConsumer(this, processor) {
                    @Override
                    protected void doStart() {
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
