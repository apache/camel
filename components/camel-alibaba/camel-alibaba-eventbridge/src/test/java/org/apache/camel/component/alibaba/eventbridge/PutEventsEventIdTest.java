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
package org.apache.camel.component.alibaba.eventbridge;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.aliyun.eventbridge.EventBridgeClient;
import com.aliyun.eventbridge.models.CloudEvent;
import com.aliyun.eventbridge.models.Config;
import com.aliyun.eventbridge.util.EventBuilder;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.alibaba.eventbridge.constants.AlibabaEventBridgeHeaders;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * The SDK client checks each event before it sends the request, and an event without an id fails that check. Here a
 * real {@link EventBridgeClient} runs the check, and only its doRequest is replaced, so that no request is sent.
 */
class PutEventsEventIdTest extends CamelTestSupport {

    private static final String JSON = "{\"orderId\":\"123\"}";

    private final TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("eventBridgeClient")
    EventBridgeClient eventBridgeClient = clientThatSendsNothing();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:put")
                        .to("alibaba-eventbridge:putEvents"
                            + "?eventBusName=" + testConfiguration.getProperty("eventBusName")
                            + "&eventSource=" + testConfiguration.getProperty("eventSource")
                            + "&eventType=" + testConfiguration.getProperty("eventType")
                            + "&eventBridgeClient=#eventBridgeClient");
            }
        };
    }

    @Test
    void aJsonTextBodyGetsAnId() {
        template.sendBody("direct:put", JSON);

        List<CloudEvent> events = checkedEvents();
        assertThat(events).hasSize(1);
        assertGeneratedId(events.get(0).getId());
    }

    @Test
    void aMapBodyWithoutAnIdGetsAnId() {
        template.sendBody("direct:put", Map.of("eventData", Map.of("orderId", "123")));

        List<CloudEvent> events = checkedEvents();
        assertThat(events).hasSize(1);
        assertGeneratedId(events.get(0).getId());
    }

    @Test
    void aMapBodyKeepsItsId() {
        template.sendBody("direct:put", Map.of("id", "order-123", "eventData", Map.of("orderId", "123")));

        List<CloudEvent> events = checkedEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getId()).isEqualTo("order-123");
    }

    @Test
    void eachEventOfABatchGetsAnIdOfItsOwn() {
        template.sendBody("direct:put", List.of(JSON, JSON, Map.of("eventData", Map.of("orderId", "456"))));

        List<CloudEvent> events = checkedEvents();
        assertThat(events).hasSize(3);
        assertThat(events).extracting(CloudEvent::getId).allSatisfy(PutEventsEventIdTest::assertGeneratedId)
                .doesNotHaveDuplicates();
    }

    @Test
    void aBlankIdGetsAnIdWhenValidateEventSpecIsFalse() {
        template.sendBodyAndHeader("direct:put", Map.of("id", " ", "eventData", Map.of("orderId", "123")),
                AlibabaEventBridgeHeaders.VALIDATE_EVENT_SPEC, false);

        List<CloudEvent> events = checkedEvents();
        assertThat(events).hasSize(1);
        assertGeneratedId(events.get(0).getId());
    }

    @Test
    void aBlankIdStillFailsWhenValidateEventSpecIsTrue() {
        Map<String, Object> body = Map.of("id", " ", "eventData", Map.of("orderId", "123"));

        assertThatThrownBy(() -> template.sendBody("direct:put", body))
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .cause().hasMessage("Event 'id' cannot be blank when provided");

        verify(eventBridgeClient, never()).doRequest(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aCloudEventBodyIsSentAsItIs() {
        CloudEvent cloudEvent = EventBuilder.builder()
                .withId("event-1")
                .withSource(URI.create(testConfiguration.getProperty("eventSource")))
                .withType(testConfiguration.getProperty("eventType"))
                .withAliyunEventBus(testConfiguration.getProperty("eventBusName"))
                .withJsonStringData(JSON)
                .build();

        template.sendBody("direct:put", cloudEvent);

        List<CloudEvent> events = checkedEvents();
        assertThat(events).singleElement().isSameAs(cloudEvent);
        assertThat(cloudEvent.getId()).isEqualTo("event-1");
    }

    @Test
    void aCloudEventBodyWithoutAnIdIsNotGivenOne() {
        CloudEvent cloudEvent = EventBuilder.builder()
                .withSource(URI.create(testConfiguration.getProperty("eventSource")))
                .withType(testConfiguration.getProperty("eventType"))
                .withAliyunEventBus(testConfiguration.getProperty("eventBusName"))
                .withJsonStringData(JSON)
                .build();

        assertThatThrownBy(() -> template.sendBody("direct:put", cloudEvent))
                .isInstanceOf(CamelExecutionException.class)
                .hasRootCauseMessage("Field id is required");

        assertThat(cloudEvent.getId()).isNull();
        verify(eventBridgeClient, never()).doRequest(any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * A generated id is a UUID, as the doc page says.
     */
    private static void assertGeneratedId(String id) {
        assertThat(id).isNotBlank();
        assertThat(UUID.fromString(id)).hasToString(id);
    }

    /**
     * The events of the one putEvents call, which the SDK client checked before it called doRequest once.
     */
    @SuppressWarnings("unchecked")
    private List<CloudEvent> checkedEvents() {
        verify(eventBridgeClient).doRequest(eq("putEvents"), any(), any(), any(), any(), any(), any());
        ArgumentCaptor<List<CloudEvent>> events = ArgumentCaptor.forClass(List.class);
        verify(eventBridgeClient).putEvents(events.capture());
        return events.getValue();
    }

    /**
     * A real client, so that putEvents checks the events as it does in production. Only doRequest, which would send the
     * request, is replaced, so the endpoint below is never contacted.
     */
    private EventBridgeClient clientThatSendsNothing() {
        Config config = new Config();
        config.setAccessKeyId(testConfiguration.getProperty("accessKey"));
        config.setAccessKeySecret(testConfiguration.getProperty("secretKey"));
        config.setEndpoint("eventbridge.invalid");
        EventBridgeClient client = spy(new EventBridgeClient(config));
        doReturn(Map.of("RequestId", "req-eb-id", "FailedEntryCount", 0))
                .when(client).doRequest(any(), any(), any(), any(), any(), any(), any());
        return client;
    }
}
