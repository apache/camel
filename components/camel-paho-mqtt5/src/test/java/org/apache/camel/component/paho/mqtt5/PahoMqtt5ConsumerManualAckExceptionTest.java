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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class PahoMqtt5ConsumerManualAckExceptionTest extends CamelTestSupport {

    @Test
    public void testOnCompleteCallsExceptionHandlerOnMqttException() throws Exception {
        PahoMqtt5Endpoint endpoint = context.getEndpoint(
                "paho-mqtt5:test?manualAcksEnabled=true&brokerUrl=tcp://localhost:1883", PahoMqtt5Endpoint.class);

        Processor mockProcessor = mock(Processor.class);
        PahoMqtt5Consumer consumer = new PahoMqtt5Consumer(endpoint, mockProcessor);

        MqttClient mockClient = mock(MqttClient.class);
        doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION))
                .when(mockClient).messageArrivedComplete(any(int.class), any(int.class));
        consumer.setClient(mockClient);

        ExceptionHandler mockExceptionHandler = mock(ExceptionHandler.class);
        consumer.setExceptionHandler(mockExceptionHandler);

        MqttMessage mqttMessage = new MqttMessage("test".getBytes());
        mqttMessage.setQos(2);

        Exchange exchange = consumer.createExchange(mqttMessage, "test-topic");
        List<Synchronization> completions = exchange.getExchangeExtension().handoverCompletions();

        assertNotNull(completions);
        assertFalse(completions.isEmpty());

        // Trigger the onComplete callback (simulating successful exchange processing)
        completions.get(0).onComplete(exchange);

        // Verify exception handler was called instead of throwing RuntimeException
        verify(mockExceptionHandler).handleException(
                contains("Error acknowledging MQTT message"),
                eq(exchange),
                any(MqttException.class));
    }

    @Test
    public void testOnCompleteAcknowledgesSuccessfully() throws Exception {
        PahoMqtt5Endpoint endpoint = context.getEndpoint(
                "paho-mqtt5:test?manualAcksEnabled=true&brokerUrl=tcp://localhost:1883", PahoMqtt5Endpoint.class);

        Processor mockProcessor = mock(Processor.class);
        PahoMqtt5Consumer consumer = new PahoMqtt5Consumer(endpoint, mockProcessor);

        MqttClient mockClient = mock(MqttClient.class);
        consumer.setClient(mockClient);

        ExceptionHandler mockExceptionHandler = mock(ExceptionHandler.class);
        consumer.setExceptionHandler(mockExceptionHandler);

        MqttMessage mqttMessage = new MqttMessage("test".getBytes());
        mqttMessage.setQos(2);

        Exchange exchange = consumer.createExchange(mqttMessage, "test-topic");
        List<Synchronization> completions = exchange.getExchangeExtension().handoverCompletions();

        assertNotNull(completions);
        assertEquals(1, completions.size());

        // Trigger the onComplete callback
        completions.get(0).onComplete(exchange);

        // Verify messageArrivedComplete was called and no exception was handled
        verify(mockClient).messageArrivedComplete(mqttMessage.getId(), mqttMessage.getQos());
        verifyNoInteractions(mockExceptionHandler);
    }

    @Test
    public void testNoCompletionRegisteredWhenManualAcksDisabled() throws Exception {
        PahoMqtt5Endpoint endpoint = context.getEndpoint(
                "paho-mqtt5:test?brokerUrl=tcp://localhost:1883", PahoMqtt5Endpoint.class);

        Processor mockProcessor = mock(Processor.class);
        PahoMqtt5Consumer consumer = new PahoMqtt5Consumer(endpoint, mockProcessor);

        MqttClient mockClient = mock(MqttClient.class);
        consumer.setClient(mockClient);

        MqttMessage mqttMessage = new MqttMessage("test".getBytes());

        Exchange exchange = consumer.createExchange(mqttMessage, "test-topic");
        List<Synchronization> completions = exchange.getExchangeExtension().handoverCompletions();

        // No synchronization should be registered when manualAcks is disabled
        assertTrue(completions == null || completions.isEmpty());
    }
}
