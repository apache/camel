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

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.spi.ExchangeFactory;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PahoMqtt5ConsumerLifecycleTest {

    @Test
    void failedStartForceClosesOwnedClient() throws Exception {
        MqttClient client = mock(MqttClient.class);
        MqttException connectException = new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION);
        doThrow(connectException).when(client).connect(any(MqttConnectionOptions.class));
        PahoMqtt5Consumer consumer = createConsumer(new PahoMqtt5Configuration(), client);

        MqttException thrown = catchThrowableOfType(MqttException.class, consumer::doStart);

        assertThat(thrown).isSameAs(connectException);
        verify(client).close(true);
    }

    @Test
    void failedStartSuppressesCloseFailure() throws Exception {
        MqttClient client = mock(MqttClient.class);
        MqttException connectException = new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION);
        MqttException closeException = new MqttException(1);
        doThrow(connectException).when(client).connect(any(MqttConnectionOptions.class));
        doThrow(closeException).when(client).close(true);
        PahoMqtt5Consumer consumer = createConsumer(new PahoMqtt5Configuration(), client);

        MqttException thrown = catchThrowableOfType(MqttException.class, consumer::doStart);

        assertThat(thrown).isSameAs(connectException);
        assertThat(thrown.getSuppressed()).containsExactly(closeException);
    }

    @Test
    void failedStartAfterConnectDisconnectsAndClosesOwnedClient() throws Exception {
        MqttClient client = mock(MqttClient.class);
        MqttException subscribeException = new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION);
        PahoMqtt5Configuration configuration = new PahoMqtt5Configuration();
        when(client.isConnected()).thenReturn(true);
        doThrow(subscribeException).when(client).subscribe("test", configuration.getQos());
        PahoMqtt5Consumer consumer = createConsumer(configuration, client);

        MqttException thrown = catchThrowableOfType(MqttException.class, consumer::doStart);

        assertThat(thrown).isSameAs(subscribeException);
        verify(client).disconnect();
        verify(client).close(true);
    }

    @Test
    void stopForceClosesOwnedClientWhenDisconnected() throws Exception {
        MqttClient client = mock(MqttClient.class);
        PahoMqtt5Consumer consumer = createConsumer(new PahoMqtt5Configuration(), client);
        consumer.doStart();

        consumer.doStop();

        verify(client, never()).disconnect();
        verify(client).close(true);
    }

    @Test
    void failedStopSuppressesCloseFailure() throws Exception {
        MqttClient client = mock(MqttClient.class);
        MqttException disconnectException = new MqttException(1);
        MqttException closeException = new MqttException(2);
        when(client.isConnected()).thenReturn(true);
        doThrow(disconnectException).when(client).disconnect();
        doThrow(closeException).when(client).close(true);
        PahoMqtt5Configuration configuration = new PahoMqtt5Configuration();
        configuration.setCleanStart(false);
        PahoMqtt5Consumer consumer = createConsumer(configuration, client);
        consumer.doStart();

        MqttException thrown = catchThrowableOfType(MqttException.class, consumer::doStop);

        assertThat(thrown).isSameAs(disconnectException);
        assertThat(thrown.getSuppressed()).containsExactly(closeException);
    }

    @Test
    void durableConnectedClientDisconnectsAndClosesWithoutUnsubscribe() throws Exception {
        MqttClient client = mock(MqttClient.class);
        when(client.isConnected()).thenReturn(true);
        PahoMqtt5Configuration configuration = new PahoMqtt5Configuration();
        configuration.setCleanStart(false);
        PahoMqtt5Consumer consumer = createConsumer(configuration, client);
        consumer.doStart();

        consumer.doStop();

        verify(client, never()).unsubscribe("test");
        verify(client).disconnect();
        verify(client).close(true);
    }

    @Test
    void sharedClientIsNotClosed() throws Exception {
        MqttClient client = mock(MqttClient.class);
        PahoMqtt5Consumer consumer = createConsumer(new PahoMqtt5Configuration(), mock(MqttClient.class));
        consumer.setClient(client);

        consumer.doStart();
        consumer.doStop();

        verify(client, never()).close(true);
    }

    private static PahoMqtt5Consumer createConsumer(PahoMqtt5Configuration configuration, MqttClient createdClient) {
        CamelContext context = mock(CamelContext.class);
        ExtendedCamelContext extension = mock(ExtendedCamelContext.class);
        ExchangeFactory exchangeFactory = mock(ExchangeFactory.class);
        when(context.getCamelContextExtension()).thenReturn(extension);
        when(extension.getExchangeFactory()).thenReturn(exchangeFactory);
        when(exchangeFactory.newExchangeFactory(any())).thenReturn(exchangeFactory);
        PahoMqtt5Endpoint endpoint = new PahoMqtt5Endpoint(
                "paho-mqtt5:test", "test", new PahoMqtt5Component(context), configuration);
        return new PahoMqtt5Consumer(endpoint, mock(Processor.class)) {
            @Override
            MqttClient createClient() {
                return createdClient;
            }
        };
    }
}
