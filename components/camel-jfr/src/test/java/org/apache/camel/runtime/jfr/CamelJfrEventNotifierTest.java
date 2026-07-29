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
package org.apache.camel.runtime.jfr;

import java.util.List;
import java.util.concurrent.TimeUnit;

import jdk.jfr.consumer.RecordedEvent;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CamelJfrEventNotifierTest extends JfrRecordingTestSupport {

    @Test
    void exchangeAndSendEventsAreEmitted() throws Exception {
        List<RecordedEvent> events = recordAndRun(() -> {
            try (DefaultCamelContext context = new DefaultCamelContext()) {
                CamelJfrEventNotifier notifier = new CamelJfrEventNotifier();
                notifier.setCamelContext(context);
                context.getManagementStrategy().addEventNotifier(notifier);
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("direct:start").routeId("main").to("mock:out");
                    }
                });
                context.start();
                MockEndpoint mock = context.getEndpoint("mock:out", MockEndpoint.class);
                mock.expectedMessageCount(1);
                context.createProducerTemplate().sendBody("direct:start", "hello");
                mock.assertIsSatisfied();
            }
        });

        assertThat(eventsOfType(events, CamelJfrEvents.EXCHANGE)).isNotEmpty();
        assertThat(eventsOfType(events, CamelJfrEvents.SEND))
                .anySatisfy(e -> assertThat(e.getString("endpointUri")).contains("mock://out"));
    }

    @Test
    void endpointUriIsSanitized() throws Exception {
        List<RecordedEvent> events = recordAndRun(() -> {
            try (DefaultCamelContext context = new DefaultCamelContext()) {
                CamelJfrEventNotifier notifier = new CamelJfrEventNotifier();
                notifier.setCamelContext(context);
                context.getManagementStrategy().addEventNotifier(notifier);
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("direct:start").routeId("main").to("mock:out?password=secret");
                    }
                });
                context.start();
                MockEndpoint mock = context.getEndpoint("mock:out?password=secret", MockEndpoint.class);
                mock.expectedMessageCount(1);
                context.createProducerTemplate().sendBody("direct:start", "hi");
                mock.assertIsSatisfied();
            }
        });

        assertThat(eventsOfType(events, CamelJfrEvents.SEND))
                .isNotEmpty()
                .allSatisfy(e -> assertThat(e.getString("endpointUri")).doesNotContain("secret"));
    }

    @Test
    void parallelMulticastPairsEachSendWithItsOwnEndpoint() throws Exception {
        // a parallel multicast copies the exchange, and a copy shares the property *values* with its parent.
        // A mutable stack stored in a property would therefore be shared across branches, and the send events
        // would pair up with the wrong endpoint (or pop an empty stack and be dropped entirely).
        List<RecordedEvent> events = recordAndRun(() -> {
            try (DefaultCamelContext context = new DefaultCamelContext()) {
                CamelJfrEventNotifier notifier = new CamelJfrEventNotifier();
                notifier.setCamelContext(context);
                context.getManagementStrategy().addEventNotifier(notifier);
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("direct:start").routeId("main")
                                .multicast().parallelProcessing()
                                .to("mock:one", "mock:two", "mock:three")
                                .end();
                    }
                });
                context.start();
                for (String name : List.of("mock:one", "mock:two", "mock:three")) {
                    context.getEndpoint(name, MockEndpoint.class).expectedMessageCount(1);
                }
                context.createProducerTemplate().sendBody("direct:start", "hi");
                MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
            }
        });

        assertThat(eventsOfType(events, CamelJfrEvents.SEND))
                .extracting(e -> e.getString("endpointUri"))
                .contains("mock://one", "mock://two", "mock://three");
    }

    @Test
    void redeliveryEventsCarryTheAttemptCount() throws Exception {
        List<RecordedEvent> events = recordAndRun(() -> {
            try (DefaultCamelContext context = new DefaultCamelContext()) {
                CamelJfrEventNotifier notifier = new CamelJfrEventNotifier();
                notifier.setCamelContext(context);
                context.getManagementStrategy().addEventNotifier(notifier);
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0));
                        from("direct:start").routeId("main")
                                .process(e -> {
                                    throw new IllegalStateException("boom");
                                });
                    }
                });
                context.start();
                try {
                    context.createProducerTemplate().sendBody("direct:start", "hi");
                } catch (Exception expected) {
                    // the exchange is meant to fail after exhausting its redeliveries
                }
            }
        });

        assertThat(eventsOfType(events, CamelJfrEvents.REDELIVERY))
                .isNotEmpty()
                .allSatisfy(e -> {
                    assertThat(e.getInt("attempt")).isPositive();
                    assertThat(e.getInt("maxAttempts")).isEqualTo(2);
                });
        assertThat(eventsOfType(events, CamelJfrEvents.FAILED))
                .isNotEmpty()
                .allSatisfy(e -> assertThat(e.getString("exceptionType")).isEqualTo(IllegalStateException.class.getName()));
    }

    @Test
    void oversizedExceptionMessageIsTruncated() throws Exception {
        // an exception message is attacker-influenced in many routes, so an unbounded copy of it would let a single
        // failing exchange blow up the recording
        String longMessage = "x".repeat(1000);
        List<RecordedEvent> events = recordAndRun(() -> {
            try (DefaultCamelContext context = new DefaultCamelContext()) {
                CamelJfrEventNotifier notifier = new CamelJfrEventNotifier();
                notifier.setCamelContext(context);
                context.getManagementStrategy().addEventNotifier(notifier);
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("direct:start").routeId("main")
                                .process(e -> {
                                    throw new IllegalStateException(longMessage);
                                });
                    }
                });
                context.start();
                try {
                    context.createProducerTemplate().sendBody("direct:start", "hi");
                } catch (Exception expected) {
                    // the exchange is meant to fail
                }
            }
        });

        assertThat(eventsOfType(events, CamelJfrEvents.FAILED))
                .isNotEmpty()
                .allSatisfy(e -> assertThat(e.getString("exceptionMessage")).isEqualTo("x".repeat(256)));
    }
}
