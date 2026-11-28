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
package org.apache.camel.spring;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.event.EventComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.support.GenericApplicationContext;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * SpringCamelContext passes the Spring events to the registered spring-event component, also when that component was
 * added before the context was built rather than created by the context.
 */
class SpringCamelContextEventComponentTest {

    private GenericApplicationContext applicationContext;
    private SpringCamelContext context;

    @BeforeEach
    void setUp() {
        applicationContext = new GenericApplicationContext();
        applicationContext.refresh();
        context = new SpringCamelContext(applicationContext);
    }

    @AfterEach
    void tearDown() {
        context.stop();
        applicationContext.close();
    }

    @Test
    void aSpringEventComponentAddedBeforeTheBuildGetsTheEvents() throws Exception {
        EventComponent component = new EventComponent(applicationContext);
        context.addComponent("spring-event", component);

        startWithEventRoute();

        assertSame(component, context.getComponent("spring-event"), "the added component was replaced");
        assertEventIsRouted();
    }

    @Test
    void theSpringEventComponentOfTheContextGetsTheEvents() throws Exception {
        startWithEventRoute();

        assertInstanceOf(EventComponent.class, context.getComponent("spring-event"));
        assertEventIsRouted();
    }

    private void startWithEventRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("spring-event:default").to("mock:events");
            }
        });
        context.start();
    }

    private void assertEventIsRouted() throws Exception {
        MockEndpoint mock = context.getEndpoint("mock:events", MockEndpoint.class);
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(PayloadApplicationEvent.class);

        context.onApplicationEvent(new PayloadApplicationEvent<>(this, "hello"));

        mock.assertIsSatisfied();
    }
}
