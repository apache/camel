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

class CamelJfrRoutePolicyTest extends JfrRecordingTestSupport {

    @Test
    void nestedRoutesEmitTwoRouteEvents() throws Exception {
        List<RecordedEvent> events = recordAndRun(() -> {
            try (DefaultCamelContext context = new DefaultCamelContext()) {
                context.addRoutePolicyFactory(new CamelJfrRoutePolicyFactory());
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("direct:a").routeId("a").to("direct:b");
                        from("direct:b").routeId("b").to("mock:out");
                    }
                });
                context.start();
                context.createProducerTemplate().sendBody("direct:a", "hi");
            }
        });

        assertThat(eventsOfType(events, CamelJfrEvents.ROUTE))
                .extracting(e -> e.getString("routeId"))
                .contains("a", "b");
    }

    @Test
    void parallelMulticastKeepsOneRouteEventPerBranch() throws Exception {
        // each branch of a parallel multicast runs on its own exchange copy, and a copy shares the property *values*
        // with its parent. A mutable stack in a property would be shared across branches, so the nested route events
        // would be popped by the wrong branch and either mis-attributed or lost.
        List<RecordedEvent> events = recordAndRun(() -> {
            try (DefaultCamelContext context = new DefaultCamelContext()) {
                context.addRoutePolicyFactory(new CamelJfrRoutePolicyFactory());
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("direct:a").routeId("a")
                                .multicast().parallelProcessing()
                                .to("direct:b", "direct:c")
                                .end();
                        from("direct:b").routeId("b").to("mock:b");
                        from("direct:c").routeId("c").to("mock:c");
                    }
                });
                context.start();
                MockEndpoint b = context.getEndpoint("mock:b", MockEndpoint.class);
                MockEndpoint c = context.getEndpoint("mock:c", MockEndpoint.class);
                b.expectedMessageCount(1);
                c.expectedMessageCount(1);
                context.createProducerTemplate().sendBody("direct:a", "hi");
                MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
            }
        });

        assertThat(eventsOfType(events, CamelJfrEvents.ROUTE))
                .extracting(e -> e.getString("routeId"))
                .containsExactlyInAnyOrder("a", "b", "c");
    }
}
