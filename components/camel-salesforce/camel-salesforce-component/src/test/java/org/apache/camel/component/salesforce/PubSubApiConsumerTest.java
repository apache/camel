package org.apache.camel.component.salesforce;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.salesforce.SalesforceConstants.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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