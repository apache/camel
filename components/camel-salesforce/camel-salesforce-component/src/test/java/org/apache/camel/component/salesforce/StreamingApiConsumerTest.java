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
import org.apache.camel.ExchangeExtension;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.component.salesforce.api.dto.PlatformEvent;
import org.apache.camel.component.salesforce.internal.streaming.SubscriptionHelper;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.ExchangeFactory;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.common.HashMapMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
public class StreamingApiConsumerTest {

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
    ExchangeExtension exchangeExtension = mock(ExchangeExtension.class);
    org.apache.camel.Message in = mock(org.apache.camel.Message.class);
    AsyncProcessor processor = mock(AsyncProcessor.class);
    CamelContext context = mock(CamelContext.class);
    ExtendedCamelContext ecc = mock(ExtendedCamelContext.class);
    ExchangeFactory exchangeFactory = mock(ExchangeFactory.class);
    Message pushTopicMessage;

    @Mock
    private Message mockChangeEvent;
    @Mock
    private Map<String, Object> mockChangeEventPayload;
    @Mock
    private Map<String, Object> mockChangeEventData;
    @Mock
    private Map<String, Object> mockChangeEventMap;

    @BeforeEach
    public void setupMocks() {
        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getCamelContext()).thenReturn(context);
        when(context.getCamelContextExtension()).thenReturn(ecc);
        when(ecc.getExchangeFactory()).thenReturn(exchangeFactory);
        when(exchangeFactory.newExchangeFactory(any())).thenReturn(exchangeFactory);
        when(exchangeFactory.create(endpoint, true)).thenReturn(exchange);
        when(exchange.getExchangeExtension()).thenReturn(exchangeExtension);
        when(exchange.getIn()).thenReturn(in);
        final SalesforceComponent component = mock(SalesforceComponent.class);
        when(endpoint.getComponent()).thenReturn(component);
        final CamelContext camelContext = mock(CamelContext.class);
        when(component.getCamelContext()).thenReturn(camelContext);
        final ClassResolver classResolver = mock(ClassResolver.class);
        when(camelContext.getClassResolver()).thenReturn(classResolver);
        when(classResolver.resolveClass(AccountUpdates.class.getName())).thenReturn((Class) AccountUpdates.class);

        pushTopicMessage = createPushTopicMessage();

        setupMockChangeEvent();
    }

    private void setupMockChangeEvent() {
        final Map<String, Object> changeEventHeader = new HashMap<>();
        changeEventHeader.put("changeType", "CREATE");
        changeEventHeader.put("changeOrigin", "com/salesforce/api/rest/45.0");
        changeEventHeader.put("transactionKey", "000bc577-90c7-0d33-cebb-971bb50d75b8");
        changeEventHeader.put("sequenceNumber", 1L);
        changeEventHeader.put("isTransactionEnd", Boolean.TRUE);
        changeEventHeader.put("commitTimestamp", 1558949299000L);
        changeEventHeader.put("commitUser", "0052p000009cl8uBBB");
        changeEventHeader.put("commitNumber", 10585193272713L);
        changeEventHeader.put("entityName", "Account");
        changeEventHeader.put("recordIds", new Object[] { "0012p00002HHpNlAAL" });

        when(mockChangeEventPayload.get("ChangeEventHeader")).thenReturn(changeEventHeader);

        when(mockChangeEventMap.get("replayId")).thenReturn(4L);

        when(mockChangeEventData.get("schema")).thenReturn("30H2pgzuWcF844p26Ityvg");
        when(mockChangeEventData.get("payload")).thenReturn(mockChangeEventPayload);
        when(mockChangeEventData.get("event")).thenReturn(mockChangeEventMap);

        when(mockChangeEvent.getDataAsMap()).thenReturn(mockChangeEventData);
        when(mockChangeEvent.getChannel()).thenReturn("/data/AccountChangeEvent");
    }

    @Test
    public void shouldProcessMappedPayloadPushTopicMessages() throws Exception {
        when(endpoint.getTopicName()).thenReturn("AccountUpdates");
        configuration.setSObjectClass(AccountUpdates.class.getName());

        final StreamingApiConsumer consumer = new StreamingApiConsumer(endpoint, processor, NOT_USED);
        consumer.determineSObjectClass();

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

        final StreamingApiConsumer consumer = new StreamingApiConsumer(endpoint, processor, NOT_USED);

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

        final StreamingApiConsumer consumer = new StreamingApiConsumer(endpoint, processor, NOT_USED);

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

        final StreamingApiConsumer consumer = new StreamingApiConsumer(endpoint, processor, NOT_USED);

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

        final StreamingApiConsumer consumer = new StreamingApiConsumer(endpoint, processor, NOT_USED);

        consumer.processMessage(mock(ClientSessionChannel.class), message);

        verify(in).setBody(new org.cometd.common.JacksonJSONContextClient()
                .generate(new org.cometd.common.HashMapMessage(message)));
        verify(in).setHeader("CamelSalesforceCreatedDate", ZonedDateTime.parse("2018-07-06T12:41:04Z"));
        verify(in).setHeader("CamelSalesforceReplayId", 4L);
        verify(in).setHeader("CamelSalesforceChannel", "/event/TestEvent__e");
        verify(in).setHeader("CamelSalesforceEventType", "TestEvent__e");
        verify(in).setHeader("CamelSalesforcePlatformEventSchema", "30H2pgzuWcF844p26Ityvg");

        verify(processor).process(same(exchange), any());

        verifyNoMoreInteractions(in, processor);
    }

    @Test
    public void shouldProcessChangeEvents() throws Exception {
        when(endpoint.getTopicName()).thenReturn("/data/AccountChangeEvent");

        final StreamingApiConsumer consumer = new StreamingApiConsumer(endpoint, processor, NOT_USED);

        consumer.processMessage(mock(ClientSessionChannel.class), mockChangeEvent);

        verify(in).setBody(mockChangeEventPayload);
        verify(in).setHeader("CamelSalesforceChannel", "/data/AccountChangeEvent");
        verify(in).setHeader("CamelSalesforceReplayId", 4L);
        verify(in).setHeader("CamelSalesforceChangeEventSchema", "30H2pgzuWcF844p26Ityvg");
        verify(in).setHeader("CamelSalesforceEventType", "AccountChangeEvent");
        verify(in).setHeader("CamelSalesforceChangeType", "CREATE");
        verify(in).setHeader("CamelSalesforceChangeOrigin", "com/salesforce/api/rest/45.0");
        verify(in).setHeader("CamelSalesforceTransactionKey", "000bc577-90c7-0d33-cebb-971bb50d75b8");
        verify(in).setHeader("CamelSalesforceSequenceNumber", 1L);
        verify(in).setHeader("CamelSalesforceIsTransactionEnd", Boolean.TRUE);
        verify(in).setHeader("CamelSalesforceCommitTimestamp", 1558949299000L);
        verify(in).setHeader("CamelSalesforceCommitUser", "0052p000009cl8uBBB");
        verify(in).setHeader("CamelSalesforceCommitNumber", 10585193272713L);
        verify(in).setHeader("CamelSalesforceEntityName", "Account");
        verify(in).setHeader("CamelSalesforceRecordIds", new Object[] { "0012p00002HHpNlAAL" });

        verify(mockChangeEventPayload).remove("ChangeEventHeader");

        verify(processor).process(same(exchange), any());

        verifyNoMoreInteractions(in, processor);
    }

    @Test
    public void processNoReplayIdChangeEventsShouldNotSetReplayIdHeader() throws Exception {
        when(endpoint.getTopicName()).thenReturn("/data/AccountChangeEvent");
        when(mockChangeEventMap.get("replayId")).thenReturn(null);

        final StreamingApiConsumer consumer = new StreamingApiConsumer(endpoint, processor, NOT_USED);

        consumer.processMessage(mock(ClientSessionChannel.class), mockChangeEvent);

        verify(in, never()).setHeader(eq("CamelSalesforceReplayId"), any());
    }

    @Test
    public void processRawPayloadChangeEventsShouldSetInputMessageAsBody() throws Exception {
        when(endpoint.getTopicName()).thenReturn("/data/AccountChangeEvent");
        configuration.setRawPayload(true);

        final StreamingApiConsumer consumer = new StreamingApiConsumer(endpoint, processor, NOT_USED);

        consumer.processMessage(mock(ClientSessionChannel.class), mockChangeEvent);

        verify(in).setBody(new org.cometd.common.JacksonJSONContextClient()
                .generate(new org.cometd.common.HashMapMessage(mockChangeEvent)));
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
