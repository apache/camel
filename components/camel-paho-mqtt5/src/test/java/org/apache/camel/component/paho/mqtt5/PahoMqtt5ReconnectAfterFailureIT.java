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
package org.apache.camel.component.paho.mqtt5;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.mosquitto.services.MosquittoLocalContainerService;
import org.apache.camel.test.infra.mosquitto.services.MosquittoRemoteService;
import org.apache.camel.test.infra.mosquitto.services.MosquittoService;
import org.apache.camel.test.infra.mosquitto.services.MosquittoServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class PahoMqtt5ReconnectAfterFailureIT extends CamelTestSupport {

    public static final String TESTING_ROUTE_ID = "testingRoute";
    private static int mqttPort = AvailablePortFinder.getNextAvailable();

    MosquittoService service;
    CountDownLatch routeStartedLatch = new CountDownLatch(1);

    @EndpointInject("mock:test")
    MockEndpoint mock;

    @Override
    protected boolean useJmx() {
        return false;
    }

    private static MosquittoService createLocalService() {
        return new MosquittoLocalContainerService(mqttPort);
    }

    private static MosquittoService createRemoteService() {
        return new MosquittoRemoteService(mqttPort);
    }

    @Override
    public void doPreSetup() throws Exception {
        super.doPreSetup();

        // Broker will be started later, after camel context is started,
        // to ensure first consumer connection fails
        service = MosquittoServiceFactory
                .builder()
                .addLocalMapping(PahoMqtt5ReconnectAfterFailureIT::createLocalService)
                .addRemoteMapping(PahoMqtt5ReconnectAfterFailureIT::createRemoteService)
                .build();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // Setup supervisor to restart routes because paho consumer
        // is not able to recover automatically on startup
        SupervisingRouteController supervising = context.getRouteController().supervising();
        supervising.setBackOffDelay(500);
        supervising.setIncludeRoutes("paho-mqtt5:*");
        return context;
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        service.shutdown();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                from("direct:test").to("paho-mqtt5:queue?lazyStartProducer=true&brokerUrl=tcp://localhost:" + mqttPort);
                from("paho-mqtt5:queue?brokerUrl=tcp://localhost:" + mqttPort)
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
        RouteController routeController = context.getRouteController();

        assertNotEquals(ServiceStatus.Started, routeController.getRouteStatus(TESTING_ROUTE_ID),
                "Broker down, expecting  route not to be started");

        // Start broker and wait for supervisor to restart route
        // consumer should now connect
        startBroker();
        routeStartedLatch.await(30, TimeUnit.SECONDS);
        assertEquals(ServiceStatus.Started, routeController.getRouteStatus(TESTING_ROUTE_ID),
                "Expecting consumer connected to broker and route started");

        // Given
        String msg = "msg";
        mock.expectedBodiesReceived(msg);

        // When
        template.sendBody("paho-mqtt5:queue?lazyStartProducer=true&brokerUrl=tcp://localhost:" + mqttPort, msg);

        // Then
        mock.assertIsSatisfied();

    }

    @Test
    public void startProducerShouldReconnectMqttClientAfterFailures() throws Exception {
        String msg = "msg";
        mock.expectedBodiesReceived(msg);

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
        service.initialize();
    }
}
