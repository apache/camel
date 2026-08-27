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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aliyun.eventbridge.EventBridgeClient;
import com.aliyun.eventbridge.models.CloudEvent;
import com.aliyun.eventbridge.models.PutEventsResponse;
import com.aliyun.eventbridge.models.PutEventsResponseEntry;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.alibaba.eventbridge.constants.AlibabaEventBridgeConstants;
import org.apache.camel.component.alibaba.eventbridge.constants.AlibabaEventBridgeHeaders;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PutEventsTest extends CamelTestSupport {

    private final TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("eventBridgeClient")
    EventBridgeClient eventBridgeClient = mock(EventBridgeClient.class);

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
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&eventBridgeClient=#eventBridgeClient")
                        .to("mock:result");
            }
        };
    }

    @Test
    void testPutEvents() throws Exception {
        PutEventsResponse response = new PutEventsResponse();
        response.setRequestId("req-eb-1");
        response.setFailedEntryCount(0);

        when(eventBridgeClient.putEvents(anyList())).thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct:put", "{\"key\":\"value\"}");

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        assertThat(exchange.getMessage().getBody(Map.class))
                .containsEntry(AlibabaEventBridgeConstants.EVENT_RESPONSE_REQUEST_IDENTIFIER, "req-eb-1")
                .containsEntry(AlibabaEventBridgeConstants.EVENT_RESPONSE_FAILED_ENTRY_COUNT, 0);
        assertThat(exchange.getMessage().getHeader(AlibabaEventBridgeHeaders.REQUEST_ID)).isEqualTo("req-eb-1");

        verify(eventBridgeClient).putEvents(anyList());
    }

    @Test
    void testPutEventsWithMapBody() throws Exception {
        PutEventsResponse response = new PutEventsResponse();
        response.setRequestId("req-eb-2");
        response.setFailedEntryCount(0);

        when(eventBridgeClient.putEvents(anyList())).thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        Map<String, Object> event = new HashMap<>();
        event.put(AlibabaEventBridgeConstants.EVENT_BUS_NAME, testConfiguration.getProperty("eventBusName"));
        event.put(AlibabaEventBridgeConstants.EVENT_SOURCE, testConfiguration.getProperty("eventSource"));
        event.put(AlibabaEventBridgeConstants.EVENT_TYPE, testConfiguration.getProperty("eventType"));
        event.put(AlibabaEventBridgeConstants.EVENT_DATA, Map.of("key", "value"));

        template.sendBody("direct:put", event);

        mock.assertIsSatisfied();

        verify(eventBridgeClient).putEvents(argThat(events -> {
            if (events.size() != 1) {
                return false;
            }
            CloudEvent cloudEvent = events.get(0);
            String data = new String(cloudEvent.getData(), StandardCharsets.UTF_8);
            return data.contains("\"key\":\"value\"");
        }));
    }

    @Test
    void testPutEventsWithListOfMapsAndSubjectAndEntryList() throws Exception {
        PutEventsResponse response = new PutEventsResponse();
        response.setRequestId("req-eb-3");
        response.setResourceOwnerAccountId("123456789");
        response.setFailedEntryCount(0);

        PutEventsResponseEntry entry1 = new PutEventsResponseEntry();
        entry1.setEventId("event-id-1");
        entry1.setErrorCode("200");
        entry1.setErrorMessage("Success");

        PutEventsResponseEntry entry2 = new PutEventsResponseEntry();
        entry2.setEventId("event-id-2");
        entry2.setErrorCode("200");
        entry2.setErrorMessage("Success");

        response.setEntryList(List.of(entry1, entry2));

        when(eventBridgeClient.putEvents(anyList())).thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        Map<String, Object> event1 = new HashMap<>();
        event1.put(AlibabaEventBridgeConstants.EVENT_BUS_NAME, "custom-bus");
        event1.put(AlibabaEventBridgeConstants.EVENT_SOURCE, "camel.source.one");
        event1.put(AlibabaEventBridgeConstants.EVENT_TYPE, "OrderCreated");
        event1.put(AlibabaEventBridgeConstants.EVENT_SUBJECT, "order/1001");
        event1.put(AlibabaEventBridgeConstants.EVENT_DATA, Map.of("orderId", "1001"));

        Map<String, Object> event2 = new HashMap<>();
        event2.put(AlibabaEventBridgeConstants.EVENT_BUS_NAME, "custom-bus");
        event2.put(AlibabaEventBridgeConstants.EVENT_SOURCE, "camel.source.two");
        event2.put(AlibabaEventBridgeConstants.EVENT_TYPE, "OrderUpdated");
        event2.put(AlibabaEventBridgeConstants.EVENT_SUBJECT, "order/1002");
        event2.put(AlibabaEventBridgeConstants.EVENT_DATA, Map.of("orderId", "1002"));

        template.sendBody("direct:put", List.of(event1, event2));

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        Map<String, Object> body = exchange.getMessage().getBody(Map.class);
        assertThat(body)
                .containsEntry(AlibabaEventBridgeConstants.EVENT_RESPONSE_REQUEST_IDENTIFIER, "req-eb-3")
                .containsEntry(AlibabaEventBridgeConstants.EVENT_RESPONSE_RESOURCE_OWNER_ACCOUNT_IDENTIFIER, "123456789")
                .containsEntry(AlibabaEventBridgeConstants.EVENT_RESPONSE_FAILED_ENTRY_COUNT, 0)
                .containsKey(AlibabaEventBridgeConstants.EVENT_RESPONSE_ENTRY_LIST);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entryList
                = (List<Map<String, Object>>) body.get(AlibabaEventBridgeConstants.EVENT_RESPONSE_ENTRY_LIST);
        assertThat(entryList).hasSize(2);

        Map<String, Object> entryMap1 = entryList.get(0);
        assertThat(entryMap1)
                .containsEntry(AlibabaEventBridgeConstants.EVENT_RESPONSE_ID, "event-id-1")
                .containsEntry(AlibabaEventBridgeConstants.EVENT_RESPONSE_ERROR_CODE, "200")
                .containsEntry(AlibabaEventBridgeConstants.EVENT_RESPONSE_ERROR_MESSAGE, "Success");

        Map<String, Object> entryMap2 = entryList.get(1);
        assertThat(entryMap2)
                .containsEntry(AlibabaEventBridgeConstants.EVENT_RESPONSE_ID, "event-id-2")
                .containsEntry(AlibabaEventBridgeConstants.EVENT_RESPONSE_ERROR_CODE, "200")
                .containsEntry(AlibabaEventBridgeConstants.EVENT_RESPONSE_ERROR_MESSAGE, "Success");

        assertThat(exchange.getMessage().getHeader(AlibabaEventBridgeHeaders.REQUEST_ID)).isEqualTo("req-eb-3");

        verify(eventBridgeClient).putEvents(argThat(events -> {
            if (events.size() != 2) {
                return false;
            }
            CloudEvent ce1 = events.get(0);
            CloudEvent ce2 = events.get(1);
            return "order/1001".equals(ce1.getSubject())
                    && "order/1002".equals(ce2.getSubject())
                    && "camel.source.one".equals(ce1.getSource().toString())
                    && "camel.source.two".equals(ce2.getSource().toString());
        }));
    }
}
