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

import java.io.UnsupportedEncodingException;

import org.apache.camel.ConsumerTemplate;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PahoComponentTest extends PahoTestSupport {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;
    @EndpointInject("mock:test")
    MockEndpoint mock;

    @EndpointInject("mock:testCustomizedPaho")
    MockEndpoint testCustomizedPahoMock;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                PahoComponent customizedPaho = new PahoComponent();
                getContext().addComponent("customizedPaho", customizedPaho);

                from("direct:test").to("paho:queue?brokerUrl=" + service.serviceAddress());
                from("paho:queue?brokerUrl=" + service.serviceAddress()).to("mock:test");

                from("direct:test2").to("paho:queue?brokerUrl=" + service.serviceAddress());

                from("paho:persistenceTest?persistence=FILE&brokerUrl=" + service.serviceAddress())
                        .to("mock:persistenceTest");

                from("direct:testCustomizedPaho")
                        .to("customizedPaho:testCustomizedPaho?brokerUrl=" + service.serviceAddress());
                from("paho:testCustomizedPaho?brokerUrl=" + service.serviceAddress()).to("mock:testCustomizedPaho");
            }
        };
    }

    // Tests

    @Test
    public void checkOptions() {
        String uri = "paho:/test/topic" + "?clientId=sampleClient" + "&brokerUrl=" + service.serviceAddress()
                     + "&qos=2"
                     + "&persistence=file";

        PahoEndpoint endpoint = getMandatoryEndpoint(uri, PahoEndpoint.class);

        // Then
        assertEquals("/test/topic", endpoint.getTopic());
        assertEquals("sampleClient", endpoint.getConfiguration().getClientId());
        assertEquals(service.serviceAddress(), endpoint.getConfiguration().getBrokerUrl());
        assertEquals(2, endpoint.getConfiguration().getQos());
        assertEquals(PahoPersistence.FILE, endpoint.getConfiguration().getPersistence());
    }

    @Test
    public void shouldReadMessageFromMqtt() throws InterruptedException {
        // Given
        String msg = "msg";
        mock.expectedBodiesReceived(msg);

        // When
        template.sendBody("direct:test", msg);

        // Then
        mock.assertIsSatisfied();
    }

    @Test
    public void shouldNotReadMessageFromUnregisteredTopic() throws InterruptedException {
        // Given
        mock.expectedMessageCount(0);

        // When
        template.sendBody("paho:someRandomQueue?brokerUrl=" + service.serviceAddress(), "msg");

        // Then
        mock.assertIsSatisfied();
    }

    @Test
    public void shouldKeepDefaultMessageInHeader() throws InterruptedException, UnsupportedEncodingException {
        // Given
        final String msg = "msg";
        mock.expectedBodiesReceived(msg);

        // When
        template.sendBody("direct:test", msg);

        // Then
        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        String payload = new String((byte[]) exchange.getIn().getBody(), "utf-8");

        assertEquals("queue", exchange.getIn().getHeader(PahoConstants.MQTT_TOPIC));
        assertEquals(msg, payload);
    }

    @Test
    public void shouldKeepOriginalMessageInHeader() throws InterruptedException {
        // Given
        final String msg = "msg";
        mock.expectedBodiesReceived(msg);

        // When
        template.sendBody("direct:test2", msg);

        // Then
        mock.assertIsSatisfied();
        Exchange exchange = mock.getExchanges().get(0);

        MqttMessage message = exchange.getIn(PahoMessage.class).getMqttMessage();
        assertNotNull(message);
        assertEquals(msg, new String(message.getPayload()));
    }

    @Test
    public void shouldReadMessageFromCustomizedComponent() throws InterruptedException {
        // Given
        String msg = "msg";
        testCustomizedPahoMock.expectedBodiesReceived(msg);

        // When
        template.sendBody("direct:testCustomizedPaho", msg);

        // Then
        testCustomizedPahoMock.assertIsSatisfied();
    }

    @Test
    public void shouldNotSendMessageAuthIsNotValid() throws InterruptedException {
        // Given
        mock.expectedMessageCount(0);

        // When
        template.sendBody(
                "paho:someRandomQueue?brokerUrl=" + service.serviceAddress() + "&userName=test&password=test",
                "msg");

        // Then
        mock.assertIsSatisfied();
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        template = getCamelContextExtension().getProducerTemplate();
        consumer = getCamelContextExtension().getConsumerTemplate();
    }
}
