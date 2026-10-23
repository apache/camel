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
package org.apache.camel.component.aws2.eventbridge;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;
import software.amazon.awssdk.services.eventbridge.model.PutRuleRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventbridgeProducerTest {

    private EventBridgeClient client;
    private EventbridgeConfiguration configuration;
    private EventbridgeProducer producer;
    private Exchange exchange;

    @BeforeEach
    void setUp() {
        client = mock(EventBridgeClient.class);
        configuration = new EventbridgeConfiguration();
        configuration.setEventbusName("default");

        DefaultCamelContext context = new DefaultCamelContext();
        EventbridgeEndpoint endpoint = mock(EventbridgeEndpoint.class);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getEventbridgeClient()).thenReturn(client);
        when(endpoint.getCamelContext()).thenReturn(context);

        producer = new EventbridgeProducer(endpoint);
        exchange = new DefaultExchange(context);
    }

    private void putEventHeaders(String resourcesArn) {
        configuration.setOperation(EventbridgeOperations.putEvent);
        exchange.getIn().setHeader(EventbridgeConstants.EVENT_RESOURCES_ARN, resourcesArn);
        exchange.getIn().setHeader(EventbridgeConstants.EVENT_DETAIL_TYPE, "order");
        exchange.getIn().setHeader(EventbridgeConstants.EVENT_SOURCE, "shop");
    }

    @Test
    void putEventFailsWhenTheEventIsRejected() {
        putEventHeaders("arn:aws:sqs:eu-west-1:780410022472:camel-connector-test");
        exchange.getIn().setBody("Test Event");
        when(client.putEvents(any(PutEventsRequest.class))).thenReturn(PutEventsResponse.builder()
                .failedEntryCount(1)
                .entries(PutEventsResultEntry.builder()
                        .errorCode("MalformedDetail")
                        .errorMessage("Detail is malformed.")
                        .build())
                .build());

        assertThatThrownBy(() -> producer.process(exchange))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EventBridge rejected the event")
                .hasMessageContaining("MalformedDetail")
                .hasMessageContaining("Detail is malformed.");
        assertThat(exchange.getMessage().getHeader(EventbridgeConstants.FAILED_ENTRY_COUNT)).isEqualTo(1);
    }

    @Test
    void putEventSucceedsWhenTheEventIsAccepted() throws Exception {
        putEventHeaders("arn:aws:sqs:eu-west-1:780410022472:camel-connector-test");
        exchange.getIn().setBody("{\"id\": 1}");
        when(client.putEvents(any(PutEventsRequest.class))).thenReturn(PutEventsResponse.builder()
                .failedEntryCount(0)
                .entries(PutEventsResultEntry.builder().eventId("1234").build())
                .build());

        producer.process(exchange);

        assertThat(exchange.getMessage().getHeader(EventbridgeConstants.FAILED_ENTRY_COUNT)).isEqualTo(0);
        assertThat(exchange.getMessage().getBody()).isInstanceOf(PutEventsResponse.class);
    }

    @Test
    void putEventWithoutBodyThrowsInvalidPayloadException() {
        putEventHeaders("arn:aws:sqs:eu-west-1:780410022472:camel-connector-test");

        assertThatThrownBy(() -> producer.process(exchange))
                .isInstanceOf(InvalidPayloadException.class);
        verify(client, never()).putEvents(any(PutEventsRequest.class));
    }

    @Test
    void putEventTrimsTheResourcesArn() throws Exception {
        putEventHeaders(" arn:aws:sqs:eu-west-1:1:a , arn:aws:sqs:eu-west-1:1:b,, ");
        exchange.getIn().setBody("{\"id\": 1}");
        when(client.putEvents(any(PutEventsRequest.class))).thenReturn(PutEventsResponse.builder()
                .failedEntryCount(0)
                .build());

        producer.process(exchange);

        ArgumentCaptor<PutEventsRequest> captor = ArgumentCaptor.forClass(PutEventsRequest.class);
        verify(client).putEvents(captor.capture());
        assertThat(captor.getValue().entries().get(0).resources())
                .containsExactly("arn:aws:sqs:eu-west-1:1:a", "arn:aws:sqs:eu-west-1:1:b");
    }

    @Test
    void putRuleWithoutEventPatternFails() {
        configuration.setOperation(EventbridgeOperations.putRule);
        exchange.getIn().setHeader(EventbridgeConstants.RULE_NAME, "firstrule");

        assertThatThrownBy(() -> producer.process(exchange))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(EventbridgeConstants.EVENT_PATTERN)
                .hasMessageContaining("eventPatternFile");
        verify(client, never()).putRule(any(PutRuleRequest.class));
    }
}
