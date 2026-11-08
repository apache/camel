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
package org.apache.camel.component.vertx.mqtt;

import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.mosquitto.services.MosquittoLocalContainerService;
import org.apache.camel.test.infra.mosquitto.services.MosquittoService;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The scenarios of PahoMqtt5ReconnectAfterFailureIT, plus a broker that goes away while the route runs. Unlike Paho the
 * consumer needs no supervising route controller: its route starts while the broker is down and the component keeps
 * trying. The broker runs on a fixed port, so that it can be started late and started again.
 */
public class VertxMqttReconnectAfterFailureIT extends CamelTestSupport {

    private static final String ROUTE = "consumer";

    @RegisterExtension
    static AvailablePortFinder.Port mqttPort = AvailablePortFinder.find();

    @EndpointInject("mock:received")
    MockEndpoint received;

    private MosquittoService broker;

    private static String uri(String topic) {
        return "vertx-mqtt:" + topic + "?brokerUrl=tcp://localhost:" + mqttPort.getPort()
               + "&qos=1&reconnectDelay=100&maxReconnectDelay=500&connectTimeout=5000";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(uri("reconnect/in")).routeId(ROUTE).to("mock:received");
                from("direct:send").to(uri("reconnect/in"));
            }
        };
    }

    @AfterEach
    void stopBroker() {
        if (broker != null) {
            broker.shutdown();
        }
    }

    private void startBroker() {
        broker = new MosquittoLocalContainerService(mqttPort.getPort());
        broker.initialize();
    }

    /**
     * Sends until the consumer received one message, since the test cannot see when the subscription is in place.
     */
    private void awaitDelivery(String body) {
        await().atMost(30, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).until(() -> {
            try {
                template.sendBody("direct:send", body);
            } catch (Exception e) {
                // not connected yet
            }
            return received.getReceivedExchanges().stream()
                    .anyMatch(exchange -> body.equals(exchange.getMessage().getBody(String.class)));
        });
    }

    @Test
    void theConsumerStartsWithoutTheBrokerAndReceivesOnceItIsThere() {
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus(ROUTE),
                "the route starts while the broker is down");

        startBroker();

        awaitDelivery("after start");
    }

    @Test
    void theProducerFailsWithoutTheBrokerAndSendsOnceItIsThere() {
        assertThrows(Exception.class, () -> template.sendBody("direct:send", "not sent"));

        startBroker();

        awaitDelivery("after start");
    }

    @Test
    void consumerAndProducerRecoverWhenTheBrokerComesBack() {
        startBroker();
        awaitDelivery("before restart");

        broker.shutdown();
        assertThrows(Exception.class, () -> template.sendBody("direct:send", "while down"));
        startBroker();

        awaitDelivery("after restart");
    }
}
