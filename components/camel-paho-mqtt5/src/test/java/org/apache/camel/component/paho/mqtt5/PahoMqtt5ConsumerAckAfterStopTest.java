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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Synchronization;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * With manualAcksEnabled=true a message is acknowledged when its exchange completes. A route stop does not wait for the
 * exchanges in flight before it stops the consumer, which closes its own client, so an exchange can complete after the
 * consumer stopped, or after it started again with a new client.
 */
class PahoMqtt5ConsumerAckAfterStopTest {

    private final Deque<MqttClient> clients = new ArrayDeque<>();
    private CamelContext context;
    private PahoMqtt5Consumer consumer;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.start();
        PahoMqtt5Configuration configuration = new PahoMqtt5Configuration();
        configuration.setManualAcksEnabled(true);
        PahoMqtt5Endpoint endpoint = new PahoMqtt5Endpoint(
                "paho-mqtt5:test", "test", context.getComponent("paho-mqtt5", PahoMqtt5Component.class), configuration);
        consumer = new PahoMqtt5Consumer(endpoint, exchange -> {
        }) {
            @Override
            MqttClient createClient() {
                return clients.removeFirst();
            }
        };
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void exchangeCompletingWhileRunningIsAcknowledged() throws Exception {
        MqttClient client = addClient();
        consumer.start();
        Exchange exchange = consumer.createExchange(message(7), "test");

        complete(exchange);

        verify(client).messageArrivedComplete(7, 1);
        consumer.stop();
    }

    @Test
    void exchangeCompletingAfterStopIsNotAcknowledged() throws Exception {
        MqttClient client = addClient();
        consumer.start();
        Exchange exchange = consumer.createExchange(message(7), "test");
        consumer.stop();

        assertThatNoException().isThrownBy(() -> complete(exchange));

        verify(client, never()).messageArrivedComplete(anyInt(), anyInt());
    }

    @Test
    void exchangeCompletingAfterRestartIsNotAcknowledgedOnTheNewClient() throws Exception {
        addClient();
        MqttClient newClient = addClient();
        consumer.start();
        Exchange exchange = consumer.createExchange(message(7), "test");
        consumer.stop();
        consumer.start();

        complete(exchange);

        // packet id 7 of the closed client is not a message of the new client
        verify(newClient, never()).messageArrivedComplete(anyInt(), anyInt());
        consumer.stop();
    }

    @Test
    void exchangeFailingAfterRestartIsNotAcknowledgedOnTheNewClient() throws Exception {
        addClient();
        MqttClient newClient = addClient();
        consumer.start();
        Exchange exchange = consumer.createExchange(message(7), "test");
        consumer.stop();
        consumer.start();

        exchange.setException(new IllegalStateException("failed"));
        List<Synchronization> completions = exchange.getExchangeExtension().handoverCompletions();
        completions.forEach(completion -> completion.onFailure(exchange));

        verify(newClient, never()).messageArrivedComplete(anyInt(), anyInt());
        consumer.stop();
    }

    @Test
    void exchangeOfClientOptionCompletingAfterRestartIsAcknowledged() throws Exception {
        // a client given with the client option is kept across a stop and used again by the next start
        MqttClient client = mock(MqttClient.class);
        consumer.setClient(client);
        consumer.start();
        Exchange exchange = consumer.createExchange(message(7), "test");
        consumer.stop();
        consumer.start();

        complete(exchange);

        verify(client).messageArrivedComplete(7, 1);
        consumer.stop();
    }

    private MqttClient addClient() {
        MqttClient client = mock(MqttClient.class);
        clients.add(client);
        return client;
    }

    private static MqttMessage message(int id) {
        MqttMessage message = new MqttMessage("body".getBytes());
        message.setId(id);
        message.setQos(1);
        return message;
    }

    private static void complete(Exchange exchange) {
        List<Synchronization> completions = exchange.getExchangeExtension().handoverCompletions();
        completions.forEach(completion -> completion.onComplete(exchange));
    }
}
