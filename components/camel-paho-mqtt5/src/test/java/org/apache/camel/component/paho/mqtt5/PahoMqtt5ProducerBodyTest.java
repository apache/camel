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

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PahoMqtt5ProducerBodyTest extends CamelTestSupport {

    private MqttClient client;
    private PahoMqtt5Endpoint endpoint;

    @BeforeEach
    void setUpClient() {
        client = mock(MqttClient.class);
        endpoint = context.getEndpoint("paho-mqtt5:test?brokerUrl=tcp://localhost:1883", PahoMqtt5Endpoint.class);
        endpoint.setClient(client);
    }

    @Test
    void textBodyIsPublishedAsBytes() throws Exception {
        Exchange exchange = template.send(endpoint, e -> e.getIn().setBody("Hello"));

        assertThat(exchange.getException()).isNull();
        ArgumentCaptor<MqttMessage> message = ArgumentCaptor.forClass(MqttMessage.class);
        verify(client).publish(eq("test"), message.capture());
        assertThat(message.getValue().getPayload()).isEqualTo("Hello".getBytes());
    }

    @Test
    void bodyThatIsNotBytesFailsWithInvalidPayloadException() throws Exception {
        Exchange exchange = template.send(endpoint, e -> e.getIn().setBody(new Object()));

        assertThat(exchange.getException()).isInstanceOf(InvalidPayloadException.class)
                .hasMessageContaining("java.lang.Object");
        verify(client, never()).publish(anyString(), any(MqttMessage.class));
    }

    @Test
    void missingBodyFailsWithInvalidPayloadException() throws Exception {
        Exchange exchange = template.send(endpoint, e -> e.getIn().setBody(null));

        assertThat(exchange.getException()).isInstanceOf(InvalidPayloadException.class);
        verify(client, never()).publish(anyString(), any(MqttMessage.class));
    }
}
