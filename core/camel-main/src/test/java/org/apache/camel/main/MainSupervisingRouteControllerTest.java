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
import org.junit.Assert;
import org.junit.Test;

public class MainSupervisingRouteControllerTest extends Assert {

    @Test
    public void testMain() throws Exception {
        // lets make a simple route
        Main main = new Main();
        main.configure().addRoutesBuilder(new MyRoute());
        main.configure().setRouteControllerEnabled(true);
        main.configure().setRouteControllerBackOffDelay(250);
        main.configure().setRouteControllerBackOffMaxAttempts(3);
        main.configure().setRouteControllerInitialDelay(1000);

        main.start();

        MockEndpoint mock = main.getCamelContext().getEndpoint("mock:foo", MockEndpoint.class);
        mock.expectedMinimumMessageCount(3);

        MockEndpoint mock2 = main.getCamelContext().getEndpoint("mock:cheese", MockEndpoint.class);
        mock2.expectedMessageCount(0);

        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, mock, mock2);

        assertEquals("Started", main.camelContext.getRouteController().getRouteStatus("foo").toString());
        // cheese was not able to start
        assertEquals("Stopped", main.camelContext.getRouteController().getRouteStatus("cheese").toString());

        main.stop();
    }

    private class MyRoute extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            getContext().addComponent("jms", new MyJmsComponent());

            from("timer:foo").to("mock:foo").routeId("foo");

            from("jms:cheese").to("mock:cheese").routeId("cheese");
        }
    }

    private class MyJmsComponent extends SedaComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            return new MyJmsEndpoint();
        }
    }

    private class MyJmsEndpoint extends SedaEndpoint {

        public MyJmsEndpoint() {
            super();
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            return new MyJmsConsumer(this, processor);
        }

        @Override
        protected String createEndpointUri() {
            return "jms:cheese";
        }
    }

    private class MyJmsConsumer extends SedaConsumer {

        public MyJmsConsumer(SedaEndpoint endpoint, Processor processor) {
            super(endpoint, processor);
        }

        @Override
        protected void doStart() throws Exception {
            throw new IllegalArgumentException("Cannot start");
        }
    }

}
