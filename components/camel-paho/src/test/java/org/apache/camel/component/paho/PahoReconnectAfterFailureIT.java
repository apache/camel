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
package org.apache.camel.component.paho;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.artemis.services.ArtemisMQTTService;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.TransientCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.ConfigurableContext;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class PahoReconnectAfterFailureIT implements ConfigurableRoute, ConfigurableContext {

    public static final String TESTING_ROUTE_ID = "testingRoute";

    @Order(1)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new TransientCamelContextExtension();

    ArtemisMQTTService broker;

    CountDownLatch routeStartedLatch = new CountDownLatch(1);
    int port = AvailablePortFinder.getNextAvailable();

    @EndpointInject("mock:test")
    MockEndpoint mock;

    @ContextFixture
    @Override
    public void configureContext(CamelContext context) {
        // Setup supervisor to restart routes because paho consumer
        // is not able to recover automatically on startup
        SupervisingRouteController supervising = context.getRouteController().supervising();
        supervising.setBackOffDelay(500);
        supervising.setIncludeRoutes("paho:*");
    }

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        final RouteBuilder routeBuilder = createRouteBuilder();

        if (routeBuilder != null) {
            context.addRoutes(routeBuilder);
        }
    }

    @AfterEach
    public void tearDown() {
        if (broker != null) {
            broker.shutdown();
        }
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                from("direct:test").to("paho:queue?lazyStartProducer=true&brokerUrl=tcp://localhost:" + port);
                from("paho:queue?brokerUrl=tcp://localhost:" + port)
                        .id(TESTING_ROUTE_ID)
                        .routePolicy(new RoutePolicySupport() {
                            @Override
                            public void onStart(Route route) {
                                routeStartedLatch.countDown();
                            }
                        })
                        .to("mock:test");
            }
        };
    }

    @Test
    public void startConsumerShouldReconnectMqttClientAfterFailures() throws Exception {
        RouteController routeController = camelContextExtension.getContext().getRouteController();

        assertNotEquals(ServiceStatus.Started, routeController.getRouteStatus(TESTING_ROUTE_ID),
                "Broker down, expecting  route not to be started");

        // Start broker and wait for supervisor to restart route
        // consumer should now connect
        startBroker();
        routeStartedLatch.await(5, TimeUnit.SECONDS);
        assertEquals(ServiceStatus.Started, routeController.getRouteStatus(TESTING_ROUTE_ID),
                "Expecting consumer connected to broker and route started");

        // Given
        String msg = "msg";
        mock.expectedBodiesReceived(msg);

        // When
        ProducerTemplate template = camelContextExtension.getProducerTemplate();
        template.sendBody("paho:queue?lazyStartProducer=true&brokerUrl=tcp://localhost:" + port, msg);

        // Then
        mock.assertIsSatisfied();
    }

    @Test
    public void startProducerShouldReconnectMqttClientAfterFailures() throws Exception {
        String msg = "msg";
        mock.expectedBodiesReceived(msg);

        ProducerTemplate template = camelContextExtension.getProducerTemplate();
        try {
            template.sendBody("direct:test", "notSentMessage");
            fail("Broker is down, paho producer should fail");
        } catch (Exception e) {
            // ignore
        }

        startBroker();
        routeStartedLatch.await(5, TimeUnit.SECONDS);

        template.sendBody("direct:test", msg);

        mock.assertIsSatisfied(10000);
    }

    private void startBroker() {
        broker = new ArtemisMQTTService(port);
        broker.initialize();
    }

}
