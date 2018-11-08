/**
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
package org.apache.camel.component.salesforce;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.salesforce.api.dto.PlatformEvent;
import org.apache.camel.component.salesforce.internal.streaming.SubscriptionHelper;
import org.apache.camel.spi.ClassResolver;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.common.HashMapMessage;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class SalesforceConsumerTest {

    public static class AccountUpdates {

        @JsonProperty("Id")
        String id;

        @JsonProperty("Name")
        String name;

        @JsonProperty("Phone")
        String phone;

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof AccountUpdates)) {
                return false;
            }

            final AccountUpdates other = (AccountUpdates) obj;

            return Objects.equals(id, other.id) && Objects.equals(name, other.name) && Objects.equals(phone, other.phone);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name, phone);
        }

    }

    static final SubscriptionHelper NOT_USED = null;

    SalesforceEndpointConfig configuration = new SalesforceEndpointConfig();

    SalesforceEndpoint endpoint = mock(SalesforceEndpoint.class);

    Exchange exchange = mock(Exchange.class);

    org.apache.camel.Message in = mock(org.apache.camel.Message.class);

    AsyncProcessor processor = mock(AsyncProcessor.class);

    Message pushTopicMessage;

    @Before
    public void setupMocks() {
        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.createExchange()).thenReturn(exchange);
        when(exchange.getIn()).thenReturn(in);
        final SalesforceComponent component = mock(SalesforceComponent.class);
        when(endpoint.getComponent()).thenReturn(component);
        final CamelContext camelContext = mock(CamelContext.class);
        when(component.getCamelContext()).thenReturn(camelContext);
        final ClassResolver classResolver = mock(ClassResolver.class);
        when(camelContext.getClassResolver()).thenReturn(classResolver);
        when(classResolver.resolveClass(AccountUpdates.class.getName())).thenReturn((Class) AccountUpdates.class);

        pushTopicMessage = createPushTopicMessage();
    }

    @Test
    public void shouldProcessMappedPayloadPushTopicMessages() throws Exception {
        when(endpoint.getTopicName()).thenReturn("AccountUpdates");
        configuration.setSObjectClass(AccountUpdates.class.getName());

        final SalesforceConsumer consumer = new SalesforceConsumer(endpoint, processor, NOT_USED);

        consumer.processMessage(mock(ClientSessionChannel.class), pushTopicMessage);

        final AccountUpdates accountUpdates = new AccountUpdates();
        accountUpdates.phone = "(415) 555-1212";
        accountUpdates.id = "001D000000KneakIAB";
        accountUpdates.name = "Blackbeard";

        verify(in).setBody(accountUpdates);
        verify(in).setHeader("CamelSalesforceEventType", "created");
        verify(in).setHeader("CamelSalesforceCreatedDate", "2016-09-16T19:45:27.454Z");
        verify(in).setHeader("CamelSalesforceReplayId", 1L);
        verify(in).setHeader("CamelSalesforceTopicName", "AccountUpdates");
        verify(in).setHeader("CamelSalesforceChannel", "/topic/AccountUpdates");
        verify(in).setHeader("CamelSalesforceClientId", "lxdl9o32njygi1gj47kgfaga4k");

        verify(processor).process(same(exchange), any());
    }

    @Test
    public void shouldProcessPlatformEvents() throws Exception {
        when(endpoint.getTopicName()).thenReturn("/event/TestEvent__e");

        final Message message = new HashMapMessage();
        final Map<String, Object> data = new HashMap<>();
        data.put("schema", "30H2pgzuWcF844p26Ityvg");

        final Map<String, Object> payload = new HashMap<>();
        payload.put("Test_Field__c", "abc");
        payload.put("CreatedById", "00541000002WYFpAAO");
        payload.put("CreatedDate", "2018-07-06T12:41:04Z");
        data.put("payload", payload);
        data.put("event", Collections.singletonMap("replayId", 4L));
        message.put("data", data);
        message.put("channel", "/event/TestEvent__e");

        final SalesforceConsumer consumer = new SalesforceConsumer(endpoint, processor, NOT_USED);

        consumer.processMessage(mock(ClientSessionChannel.class), message);

        final ZonedDateTime created = ZonedDateTime.parse("2018-07-06T12:41:04Z");
        final PlatformEvent event = new PlatformEvent(created, "00541000002WYFpAAO");
        event.set("Test_Field__c", "abc");
        verify(in).setBody(event);
        verify(in).setHeader("CamelSalesforceCreatedDate", created);
        verify(in).setHeader("CamelSalesforceReplayId", 4L);
        verify(in).setHeader("CamelSalesforceChannel", "/event/TestEvent__e");
        verify(in).setHeader("CamelSalesforceEventType", "TestEvent__e");
        verify(in).setHeader("CamelSalesforcePlatformEventSchema", "30H2pgzuWcF844p26Ityvg");

        verify(processor).process(same(exchange), any());

        verifyNoMoreInteractions(in, processor);
    }

    @Test
    public void shouldProcessPushTopicMessages() throws Exception {
        when(endpoint.getTopicName()).thenReturn("AccountUpdates");

        final SalesforceConsumer consumer = new SalesforceConsumer(endpoint, processor, NOT_USED);

        consumer.processMessage(mock(ClientSessionChannel.class), pushTopicMessage);

        @SuppressWarnings("unchecked")
        final Object sobject = ((Map<String, Object>) pushTopicMessage.get("data")).get("sobject");
        verify(in).setBody(sobject);
        verify(in).setHeader("CamelSalesforceEventType", "created");
        verify(in).setHeader("CamelSalesforceCreatedDate", "2016-09-16T19:45:27.454Z");
        verify(in).setHeader("CamelSalesforceReplayId", 1L);
        verify(in).setHeader("CamelSalesforceTopicName", "AccountUpdates");
        verify(in).setHeader("CamelSalesforceChannel", "/topic/AccountUpdates");
        verify(in).setHeader("CamelSalesforceClientId", "lxdl9o32njygi1gj47kgfaga4k");

        verify(processor).process(same(exchange), any());
    }

    @Test
    public void shouldProcessRawPayloadPushTopicMessages() throws Exception {
        when(endpoint.getTopicName()).thenReturn("AccountUpdates");
        configuration.setRawPayload(true);

        final SalesforceConsumer consumer = new SalesforceConsumer(endpoint, processor, NOT_USED);

        consumer.processMessage(mock(ClientSessionChannel.class), pushTopicMessage);

        verify(in).setBody("{\"Phone\":\"(415) 555-1212\",\"Id\":\"001D000000KneakIAB\",\"Name\":\"Blackbeard\"}");
        verify(in).setHeader("CamelSalesforceEventType", "created");
        verify(in).setHeader("CamelSalesforceCreatedDate", "2016-09-16T19:45:27.454Z");
        verify(in).setHeader("CamelSalesforceReplayId", 1L);
        verify(in).setHeader("CamelSalesforceTopicName", "AccountUpdates");
        verify(in).setHeader("CamelSalesforceChannel", "/topic/AccountUpdates");
        verify(in).setHeader("CamelSalesforceClientId", "lxdl9o32njygi1gj47kgfaga4k");

        verify(processor).process(same(exchange), any());
    }

    @Test
    public void shouldProcessRawPlatformEvents() throws Exception {
        when(endpoint.getTopicName()).thenReturn("/event/TestEvent__e");
        configuration.setRawPayload(true);

        final Message message = new HashMapMessage();
        final Map<String, Object> data = new HashMap<>();
        data.put("schema", "30H2pgzuWcF844p26Ityvg");

        final Map<String, Object> payload = new HashMap<>();
        payload.put("Test_Field__c", "abc");
        payload.put("CreatedById", "00541000002WYFpAAO");
        payload.put("CreatedDate", "2018-07-06T12:41:04Z");
        data.put("payload", payload);
        data.put("event", Collections.singletonMap("replayId", 4L));
        message.put("data", data);
        message.put("channel", "/event/TestEvent__e");

        final SalesforceConsumer consumer = new SalesforceConsumer(endpoint, processor, NOT_USED);

        consumer.processMessage(mock(ClientSessionChannel.class), message);

        verify(in).setBody(message);
        verify(in).setHeader("CamelSalesforceCreatedDate", ZonedDateTime.parse("2018-07-06T12:41:04Z"));
        verify(in).setHeader("CamelSalesforceReplayId", 4L);
        verify(in).setHeader("CamelSalesforceChannel", "/event/TestEvent__e");
        verify(in).setHeader("CamelSalesforceEventType", "TestEvent__e");
        verify(in).setHeader("CamelSalesforcePlatformEventSchema", "30H2pgzuWcF844p26Ityvg");

        verify(processor).process(same(exchange), any());

        verifyNoMoreInteractions(in, processor);
    }

    static Message createPushTopicMessage() {
        final Message pushTopicMessage = new HashMapMessage();
        pushTopicMessage.put("clientId", "lxdl9o32njygi1gj47kgfaga4k");

        final Map<String, Object> data = new HashMap<>();
        pushTopicMessage.put("data", data);

        final Map<String, Object> event = new HashMap<>();
        data.put("event", event);

        event.put("createdDate", "2016-09-16T19:45:27.454Z");
        event.put("replayId", 1L);
        event.put("type", "created");

        final Map<String, Object> sobject = new HashMap<>();
        data.put("sobject", sobject);

        sobject.put("Phone", "(415) 555-1212");
        sobject.put("Id", "001D000000KneakIAB");
        sobject.put("Name", "Blackbeard");

        pushTopicMessage.put("channel", "/topic/AccountUpdates");
        return pushTopicMessage;
    }
}
