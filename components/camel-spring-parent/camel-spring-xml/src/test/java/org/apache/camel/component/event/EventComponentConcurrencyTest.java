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
package org.apache.camel.component.event;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringCamelContext;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class EventComponentConcurrencyTest {

    @Test
    void stoppingRouteDuringEventDispatchMustNotThrow() throws Exception {
        try (AnnotationConfigApplicationContext appCtx = new AnnotationConfigApplicationContext()) {
            appCtx.refresh();

            SpringCamelContext camel = new SpringCamelContext(appCtx);
            appCtx.addApplicationListener(camel);

            camel.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("spring-event:first").routeId("first")
                            .process(e -> e.getContext().getRouteController().stopRoute("second"));
                    from("spring-event:second").routeId("second")
                            .to("log:second");
                }
            });
            camel.start();

            try {
                assertDoesNotThrow(() -> appCtx.publishEvent(new TestEvent(this)));
            } finally {
                camel.stop();
            }
        }
    }

    @Test
    void survivingEndpointStillReceivesEventsAfterAnotherIsStopped() throws Exception {
        try (AnnotationConfigApplicationContext appCtx = new AnnotationConfigApplicationContext()) {
            appCtx.refresh();

            SpringCamelContext camel = new SpringCamelContext(appCtx);
            appCtx.addApplicationListener(camel);

            camel.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("spring-event:endpointA").routeId("routeA")
                            .to("mock:a");
                    from("spring-event:endpointB").routeId("routeB")
                            .to("mock:b");
                }
            });
            camel.start();

            MockEndpoint mockA = camel.getEndpoint("mock:a", MockEndpoint.class);
            MockEndpoint mockB = camel.getEndpoint("mock:b", MockEndpoint.class);

            // both endpoints receive the first event
            mockA.expectedMinimumMessageCount(1);
            mockB.expectedMinimumMessageCount(1);
            appCtx.publishEvent(new TestEvent(this));
            MockEndpoint.assertIsSatisfied(camel);

            // stop routeA, routeB must still receive events
            camel.getRouteController().stopRoute("routeA");
            mockB.reset();
            mockB.expectedMinimumMessageCount(1);
            appCtx.publishEvent(new TestEvent(this));
            MockEndpoint.assertIsSatisfied(camel);

            camel.stop();
        }
    }

    static class TestEvent extends ApplicationEvent {
        TestEvent(Object source) {
            super(source);
        }
    }
}
