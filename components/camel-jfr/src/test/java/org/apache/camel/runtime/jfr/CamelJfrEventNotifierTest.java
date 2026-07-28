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

import jdk.jfr.consumer.RecordedEvent;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CamelJfrEventNotifierTest extends JfrRecordingTestSupport {

    private static final Class<?>[] EVENTS = {
            CamelExchangeEvent.class, CamelExchangeSendEvent.class,
            CamelExchangeFailedEvent.class, CamelRedeliveryEvent.class
    };

    @Test
    void exchangeAndSendEventsAreEmitted() throws Exception {
        List<RecordedEvent> events = recordAndRun(EVENTS, () -> {
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

        assertThat(events).anySatisfy(e -> assertThat(e.getEventType().getName()).isEqualTo("org.apache.camel.exchange"));
        assertThat(events)
                .filteredOn(e -> "org.apache.camel.exchange.send".equals(e.getEventType().getName()))
                .anySatisfy(e -> assertThat(e.getString("endpointUri")).contains("mock://out"));
    }

    @Test
    void endpointUriIsSanitized() throws Exception {
        List<RecordedEvent> events = recordAndRun(new Class<?>[] { CamelExchangeSendEvent.class }, () -> {
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

        assertThat(events)
                .filteredOn(e -> "org.apache.camel.exchange.send".equals(e.getEventType().getName()))
                .isNotEmpty()
                .allSatisfy(e -> assertThat(e.getString("endpointUri")).doesNotContain("secret"));
    }
}
