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

import static org.apache.camel.component.salesforce.SalesforceConstants.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.api.Test;

class PubSubApiConsumerTest {

    @Test
    void testProcessEventSetsHeaders() throws Exception {
        final PubSubApiConsumer consumer = mock(PubSubApiConsumer.class);
        final Exchange mockExchange = mock(Exchange.class);
        final Message message = mock(Message.class);
        when(mockExchange.getIn()).thenReturn(message);
        when(consumer.createExchange(true)).thenReturn(mockExchange);
        doCallRealMethod().when(consumer).processEvent(any(), any(), any(), any());
        final AsyncProcessor asyncProcessorMock = mock(AsyncProcessor.class);
        when(consumer.getAsyncProcessor()).thenReturn(asyncProcessorMock);
        String testRecord = "TEST";
        String eventId = "testEventId";
        String replayId = "testReplayId";
        String rpcId = "testRpcId";
        consumer.processEvent(testRecord, eventId, replayId, rpcId);
        verify(asyncProcessorMock, times(1)).process(any(), any());
        verify(message, times(1)).setHeader(HEADER_SALESFORCE_PUBSUB_EVENT_ID, eventId);
        verify(message, times(1)).setHeader(HEADER_SALESFORCE_PUBSUB_REPLAY_ID, replayId);
        verify(message, times(1)).setHeader(HEADER_SALESFORCE_PUBSUB_RPC_ID, rpcId);
    }
}
