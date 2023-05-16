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
package org.apache.camel.main;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.seda.SedaComponent;
import org.apache.camel.component.seda.SedaConsumer;
import org.apache.camel.component.seda.SedaEndpoint;
import org.apache.camel.spi.SupervisingRouteController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainSupervisingRouteControllerTest {

    @Test
    public void testMain() throws Exception {
        // lets make a simple route
        Main main = new Main();
        main.configure().addRoutesBuilder(new MyRoute());
        main.configure()
                .withRouteControllerSuperviseEnabled(true)
                .withRouteControllerBackOffDelay(25)
                .withRouteControllerBackOffMaxAttempts(3)
                .withRouteControllerInitialDelay(100)
                .withRouteControllerThreadPoolSize(2)
                .withRouteControllerExcludeRoutes("timer*");
        main.start();

        MockEndpoint mock = main.getCamelContext().getEndpoint("mock:foo", MockEndpoint.class);
        mock.expectedMinimumMessageCount(3);

        MockEndpoint mock2 = main.getCamelContext().getEndpoint("mock:cheese", MockEndpoint.class);
        mock2.expectedMessageCount(0);

        MockEndpoint mock3 = main.getCamelContext().getEndpoint("mock:cake", MockEndpoint.class);
        mock3.expectedMessageCount(0);

        MockEndpoint mock4 = main.getCamelContext().getEndpoint("mock:bar", MockEndpoint.class);
        mock4.expectedMessageCount(0);

        MockEndpoint.assertIsSatisfied(5, TimeUnit.SECONDS, mock, mock2, mock3, mock4);

        assertEquals("Started", main.camelContext.getRouteController().getRouteStatus("foo").toString());
        // cheese was not able to start
        assertEquals("Stopped", main.camelContext.getRouteController().getRouteStatus("cheese").toString());
        // cake was not able to start
        assertEquals("Stopped", main.camelContext.getRouteController().getRouteStatus("cake").toString());

        SupervisingRouteController src = (SupervisingRouteController) main.camelContext.getRouteController();
        Throwable e = src.getRestartException("cake");
        assertNotNull(e);
        assertEquals("Cannot start", e.getMessage());
        assertTrue(e instanceof IllegalArgumentException);

        // bar is no auto startup
        assertEquals("Stopped", main.camelContext.getRouteController().getRouteStatus("bar").toString());

        main.stop();
    }

    @Test
    public void testMainOk() throws Exception {
        // lets make a simple route
        Main main = new Main();
        main.configure().addRoutesBuilder(new MyRoute());
        main.configure().setRouteControllerSuperviseEnabled(true);
        main.configure().setRouteControllerBackOffDelay(25);
        main.configure().setRouteControllerBackOffMaxAttempts(10);
        main.configure().setRouteControllerInitialDelay(100);
        main.configure().setRouteControllerThreadPoolSize(2);

        main.start();

        MockEndpoint mock = main.getCamelContext().getEndpoint("mock:foo", MockEndpoint.class);
        mock.expectedMinimumMessageCount(3);

        MockEndpoint mock2 = main.getCamelContext().getEndpoint("mock:cheese", MockEndpoint.class);
        mock2.expectedMessageCount(0);

        MockEndpoint mock3 = main.getCamelContext().getEndpoint("mock:cake", MockEndpoint.class);
        mock3.expectedMessageCount(0);

        MockEndpoint mock4 = main.getCamelContext().getEndpoint("mock:bar", MockEndpoint.class);
        mock4.expectedMessageCount(0);

        MockEndpoint.assertIsSatisfied(5, TimeUnit.SECONDS, mock, mock2, mock3, mock4);

        // these should all start
        assertEquals("Started", main.camelContext.getRouteController().getRouteStatus("foo").toString());
        assertEquals("Started", main.camelContext.getRouteController().getRouteStatus("cheese").toString());
        assertEquals("Started", main.camelContext.getRouteController().getRouteStatus("cake").toString());
        // bar is no auto startup
        assertEquals("Stopped", main.camelContext.getRouteController().getRouteStatus("bar").toString());

        main.stop();
    }

    private class MyRoute extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            getContext().addComponent("jms", new MyJmsComponent());

            from("timer:foo").to("mock:foo").routeId("foo");

            from("jms:cheese").to("mock:cheese").routeId("cheese");

            from("jms:cake").to("mock:cake").routeId("cake");

            from("seda:bar").routeId("bar").noAutoStartup().to("mock:bar");
        }
    }

    private class MyJmsComponent extends SedaComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            return new MyJmsEndpoint(remaining);
        }
    }

    private class MyJmsEndpoint extends SedaEndpoint {

        private String name;

        public MyJmsEndpoint(String name) {
            this.name = name;
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
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
        protected void doStart() throws Exception {
            if (counter++ < 5) {
                throw new IllegalArgumentException("Cannot start");
            }
        }
    }

}
