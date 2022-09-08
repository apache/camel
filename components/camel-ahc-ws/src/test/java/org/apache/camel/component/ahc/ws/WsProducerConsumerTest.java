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
package org.apache.camel.component.ahc.ws;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.jetty.services.JettyConfiguration;
import org.apache.camel.test.infra.jetty.services.JettyConfigurationBuilder;
import org.apache.camel.test.infra.jetty.services.JettyEmbeddedService;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
public class WsProducerConsumerTest extends CamelTestSupport {

    protected static final String TEST_MESSAGE = "Hello World!";
    protected static final String TEST_CONNECTED_MESSAGE = "Connected!";

    private static final Logger LOG = LoggerFactory.getLogger(WsProducerConsumerTest.class);

    private static final JettyConfiguration JETTY_CONFIGURATION = JettyConfigurationBuilder
            .emptyTemplate()
            .withPort(AvailablePortFinder.getNextAvailable())
            .withContextPath(JettyConfiguration.ROOT_CONTEXT_PATH)
            .addServletConfiguration(new JettyConfiguration.ServletConfiguration(
                    TestServletFactory.class.getName(), JettyConfiguration.ServletConfiguration.ROOT_PATH_SPEC))
            .build();

    public JettyEmbeddedService service = new JettyEmbeddedService(JETTY_CONFIGURATION);

    protected List<Object> messages;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        service.initialize();
        super.setUp();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        service.shutdown();
    }

    @Test
    public void testTwoRoutes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(TEST_MESSAGE);

        template.sendBody("direct:input", TEST_MESSAGE);

        mock.assertIsSatisfied();
    }

    @Test
    public void testTwoRoutesRestartConsumer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(TEST_MESSAGE);

        template.sendBody("direct:input", TEST_MESSAGE);

        mock.assertIsSatisfied();

        resetMocks();

        LOG.info("Restarting bar route");

        boolean stopped = context.getRouteController().stopRoute("bar", 500, TimeUnit.MILLISECONDS, true);
        assertTrue(stopped, "The route should have stopped within the specified time");
        context.getRouteController().startRoute("bar");

        mock.expectedBodiesReceived(TEST_MESSAGE);

        template.sendBody("direct:input", TEST_MESSAGE);

        mock.assertIsSatisfied();
    }

    @Test
    public void testTwoRoutesRestartProducer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(TEST_MESSAGE);

        template.sendBody("direct:input", TEST_MESSAGE);

        mock.assertIsSatisfied();

        resetMocks();

        LOG.info("Restarting foo route");

        boolean stopped = context.getRouteController().stopRoute("foo", 500, TimeUnit.MILLISECONDS, true);
        assertTrue(stopped, "The route should have stopped within the specified time");

        context.getRouteController().startRoute("foo");

        mock.expectedBodiesReceived(TEST_MESSAGE);

        template.sendBody("direct:input", TEST_MESSAGE);

        mock.assertIsSatisfied();
    }

    @Disabled("The reconnect logic on WsEndpoint has a bug and this component is deprecated - CAMEL-17667")
    @Test
    public void testRestartServer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:restart-result");
        mock.expectedBodiesReceived(TEST_CONNECTED_MESSAGE);

        mock.assertIsSatisfied();
        resetMocks();

        LOG.info("Restarting Test Server");
        service.shutdown();
        service.initialize();

        mock.expectedBodiesReceived(TEST_CONNECTED_MESSAGE);

        mock.assertIsSatisfied(10000);
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() {
        RouteBuilder[] rbs = new RouteBuilder[3];
        rbs[0] = new RouteBuilder() {
            public void configure() {
                from("direct:input").routeId("foo")
                        .to("ahc-ws://localhost:" + service.getPort());
            }
        };
        rbs[1] = new RouteBuilder() {
            public void configure() {
                from("ahc-ws://localhost:" + service.getPort()).routeId("bar")
                        .to("mock:result");
            }
        };
        rbs[2] = new RouteBuilder() {
            public void configure() {
                from("ahc-ws://localhost:" + service.getPort() + "/restart").routeId("restart")
                        .to("mock:restart-result");
            }
        };
        return rbs;
    }
}
