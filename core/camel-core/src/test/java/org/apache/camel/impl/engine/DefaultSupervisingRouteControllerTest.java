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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.seda.SedaComponent;
import org.apache.camel.component.seda.SedaConsumer;
import org.apache.camel.component.seda.SedaEndpoint;
import org.apache.camel.impl.event.RouteRestartingEvent;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.RouteRestartingFailureEvent;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(architectures = { "s390x" },
              disabledReason = "This test does not run reliably on s390x (see CAMEL-21438)")
public class DefaultSupervisingRouteControllerTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testSupervising() throws Exception {
        // lets make a simple route
        context.addRoutes(new MyRoute());

        // configure supervising route controller
        SupervisingRouteController src = context.getRouteController().supervising();
        src.setBackOffDelay(25);
        src.setBackOffMaxAttempts(3);
        src.setInitialDelay(100);
        src.setThreadPoolSize(2);

        List<CamelEvent.RouteRestartingFailureEvent> failures = Collections.synchronizedList(new ArrayList<>());
        List<CamelEvent.RouteRestartingEvent> events = Collections.synchronizedList(new ArrayList<>());

        context.getManagementStrategy().addEventNotifier(new SimpleEventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) throws Exception {
                if (event instanceof CamelEvent.RouteRestartingFailureEvent rfe) {
                    failures.add(rfe);
                } else if (event instanceof RouteRestartingEvent rre) {
                    events.add(rre);
                }
            }
        });

        context.start();

        MockEndpoint mock = context.getEndpoint("mock:foo", MockEndpoint.class);
        mock.expectedMinimumMessageCount(3);

        MockEndpoint mock2 = context.getEndpoint("mock:cheese", MockEndpoint.class);
        mock2.expectedMessageCount(0);

        MockEndpoint mock3 = context.getEndpoint("mock:cake", MockEndpoint.class);
        mock3.expectedMessageCount(0);

        MockEndpoint mock4 = context.getEndpoint("mock:bar", MockEndpoint.class);
        mock4.expectedMessageCount(0);

        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, mock, mock2, mock3, mock4);

        assertEquals("Started", context.getRouteController().getRouteStatus("foo").toString());
        // cheese was not able to start
        assertEquals("Stopped", context.getRouteController().getRouteStatus("cheese").toString());
        // cake was not able to start
        assertEquals("Stopped", context.getRouteController().getRouteStatus("cake").toString());

        await("Await all exceptions and retries finished")
                .atMost(Duration.ofMillis(src.getInitialDelay() + src.getBackOffDelay() * (src.getBackOffMaxAttempts() + 1)))
                .untilAsserted(() -> assertNotNull(src.getRestartException("cake")));
        Throwable e = src.getRestartException("cake");
        assertEquals("Cannot start", e.getMessage());
        boolean b = e instanceof IllegalArgumentException;
        assertTrue(b);

        // bar is no auto startup
        assertEquals("Stopped", context.getRouteController().getRouteStatus("bar").toString());

        assertEquals(10, failures.size(),
                "There should have 2 x 1 initial + 2 x 3 restart failure + 2 x 1 exhausted failures.");

        assertEquals(6, events.size(), "There should have been 2 x 3 restart attempts.");

        assertEquals(2, failures.stream().filter(failure -> failure.isExhausted()).count(),
                "There should be 2 exhausted failure. Current state of failure list: " + getFailureStatus(failures));
    }

    private String getFailureStatus(List<RouteRestartingFailureEvent> failure) {
        StringBuilder sb = new StringBuilder();
        for (RouteRestartingFailureEvent routeRestartingFailureEvent : failure) {
            sb.append("\nAttempt: " + routeRestartingFailureEvent.getAttempt());
            sb.append(", Is exhausted: " + routeRestartingFailureEvent.isExhausted());
            sb.append(", Cause: " + routeRestartingFailureEvent.getCause() != null
                    ? routeRestartingFailureEvent.getCause().getMessage() : "No exception");
            sb.append(", timestamp: " + routeRestartingFailureEvent.getTimestamp());
        }
        return sb.toString();
    }

    @Test
    public void testSupervisingOk() throws Exception {
        // lets make a simple route
        context.addRoutes(new MyRoute());

        // configure supervising
        SupervisingRouteController src = context.getRouteController().supervising();
        src.setBackOffDelay(25);
        src.setBackOffMaxAttempts(10);
        src.setInitialDelay(100);
        src.setThreadPoolSize(2);

        List<CamelEvent.RouteRestartingFailureEvent> failure = Collections.synchronizedList(new ArrayList<>());
        List<CamelEvent.RouteRestartingEvent> events = Collections.synchronizedList(new ArrayList<>());

        context.getManagementStrategy().addEventNotifier(new SimpleEventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) throws Exception {
                if (event instanceof CamelEvent.RouteRestartingFailureEvent rfe) {
                    failure.add(rfe);
                } else if (event instanceof RouteRestartingEvent rre) {
                    events.add(rre);
                }
            }
        });

        context.start();

        MockEndpoint mock = context.getEndpoint("mock:foo", MockEndpoint.class);
        mock.expectedMinimumMessageCount(3);

        MockEndpoint mock2 = context.getEndpoint("mock:cheese", MockEndpoint.class);
        mock2.expectedMessageCount(0);

        MockEndpoint mock3 = context.getEndpoint("mock:cake", MockEndpoint.class);
        mock3.expectedMessageCount(0);

        MockEndpoint mock4 = context.getEndpoint("mock:bar", MockEndpoint.class);
        mock4.expectedMessageCount(0);

        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, mock, mock2, mock3, mock4);

        // these should all start
        assertEquals("Started", context.getRouteController().getRouteStatus("foo").toString());
        assertEquals("Started", context.getRouteController().getRouteStatus("cheese").toString());
        assertEquals("Started", context.getRouteController().getRouteStatus("cake").toString());
        // bar is no auto startup
        assertEquals("Stopped", context.getRouteController().getRouteStatus("bar").toString());

        // 2 x 1 initial + 2 x 4 restart failure attempts
        assertEquals(10, failure.size());
        // 2 x 5 restart attempts
        assertEquals(10, events.size());
    }

    private static class MyRoute extends RouteBuilder {
        @Override
        public void configure() {
            getContext().addComponent("jms", new MyJmsComponent());

            from("timer:foo").to("mock:foo").routeId("foo");

            from("jms:cheese").to("mock:cheese").routeId("cheese");

            from("jms:cake").to("mock:cake").routeId("cake");

            from("seda:bar").routeId("bar").autoStartup(false).to("mock:bar");
        }
    }

    private static class MyJmsComponent extends SedaComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new MyJmsEndpoint(remaining);
        }
    }

    private static class MyJmsEndpoint extends SedaEndpoint {

        private final String name;

        public MyJmsEndpoint(String name) {
            this.name = name;
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            return new MyJmsConsumer(this, processor);
        }

        @Override
        protected String createEndpointUri() {
            return "jms:" + name;
        }
    }

    private static class MyJmsConsumer extends SedaConsumer {

        private int counter;

        public MyJmsConsumer(SedaEndpoint endpoint, Processor processor) {
            super(endpoint, processor);
        }

        @Override
        protected void doStart() {
            if (counter++ < 5) {
                throw new IllegalArgumentException("Cannot start");
            }
        }
    }

}
