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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.seda.SedaComponent;
import org.apache.camel.component.seda.SedaConsumer;
import org.apache.camel.component.seda.SedaEndpoint;
import org.junit.jupiter.api.Test;

public class MainSupervisingRouteControllerFilterFailToStartRouteTest {

    @Test
    public void testMain() {
        // lets make a simple route
        Main main = new Main();
        main.configure().addRoutesBuilder(new MyRoute());
        main.configure().routeControllerConfig().setEnabled(true);
        main.configure().routeControllerConfig().setBackOffDelay(250);
        main.configure().routeControllerConfig().setBackOffMaxAttempts(3);
        main.configure().routeControllerConfig().setInitialDelay(1000);
        main.configure().routeControllerConfig().setThreadPoolSize(2);
        main.configure().routeControllerConfig().setExcludeRoutes("inbox");

        try {
            main.start();
            fail("Should fail");
        } catch (FailedToStartRouteException e) {
            assertEquals("inbox", e.getRouteId());
        }

        main.stop();
    }

    private static class MyRoute extends RouteBuilder {
        @Override
        public void configure() {
            getContext().addComponent("jms", new MyJmsComponent());

            from("timer:foo").to("mock:foo").routeId("foo");

            from("jms:cheese").to("mock:cheese").routeId("cheese");

            from("jms:cake").to("mock:cake").routeId("cake");

            from("jms:inbox").routeId("inbox").to("mock:inbox");
        }
    }

    private static class MyJmsComponent extends SedaComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new MyJmsEndpoint();
        }
    }

    private static class MyJmsEndpoint extends SedaEndpoint {

        public MyJmsEndpoint() {}

        @Override
        public Consumer createConsumer(Processor processor) {
            return new MyJmsConsumer(this, processor);
        }

        @Override
        protected String createEndpointUri() {
            return "jms:cheese";
        }
    }

    private static class MyJmsConsumer extends SedaConsumer {

        public MyJmsConsumer(SedaEndpoint endpoint, Processor processor) {
            super(endpoint, processor);
        }

        @Override
        protected void doStart() {
            throw new IllegalArgumentException("Cannot start");
        }
    }
}
