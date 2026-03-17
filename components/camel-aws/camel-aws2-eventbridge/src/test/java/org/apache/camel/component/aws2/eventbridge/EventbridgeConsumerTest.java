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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutTargetsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutTargetsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutTargetsResultEntry;
import software.amazon.awssdk.services.eventbridge.model.RemoveTargetsRequest;
import software.amazon.awssdk.services.eventbridge.model.RemoveTargetsResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EventbridgeConsumer} using Mockito mocks for SQS and EventBridge clients.
 */
@ExtendWith(MockitoExtension.class)
public class EventbridgeConsumerTest extends CamelTestSupport {

    private static final String TEST_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/123456789/camel-eventbridge-test";
    private static final String TEST_QUEUE_ARN = "arn:aws:sqs:us-east-1:123456789:camel-eventbridge-test";
    private static final String USER_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/123456789/user-queue";
    private static final String USER_QUEUE_ARN = "arn:aws:sqs:us-east-1:123456789:user-queue";

    @Mock
    private SqsClient sqsClient;
    @Mock
    private EventBridgeClient eventBridgeClient;

    private List<Exchange> receivedExchanges;

    @BeforeEach
    public void setupExchangeList() {
        receivedExchanges = new ArrayList<>();
    }

    // -- Setup / Target wiring tests --

    @Test
    public void testAutoCreateQueueAndWireAsTarget() throws Exception {
        stubAutoCreateQueue();
        stubPutTargetsSuccess();

        EventbridgeConsumer consumer = createConsumer("test-rule", null, true, true);
        consumer.start();

        verify(sqsClient).createQueue(any(CreateQueueRequest.class));
        verify(sqsClient).setQueueAttributes(any(SetQueueAttributesRequest.class));

        ArgumentCaptor<PutTargetsRequest> captor = ArgumentCaptor.forClass(PutTargetsRequest.class);
        verify(eventBridgeClient).putTargets(captor.capture());
        assertEquals("test-rule", captor.getValue().rule());
        assertEquals(TEST_QUEUE_ARN, captor.getValue().targets().get(0).arn());

        consumer.stop();
    }

    @Test
    public void testUserProvidedQueueUrl() throws Exception {
        stubGetQueueAttributes(USER_QUEUE_URL, USER_QUEUE_ARN);
        stubPutTargetsSuccess();

        EventbridgeConsumer consumer = createConsumer("test-rule", USER_QUEUE_URL, true, true);
        consumer.start();

        verify(sqsClient, never()).createQueue(any(CreateQueueRequest.class));
        verify(sqsClient).getQueueAttributes(any(GetQueueAttributesRequest.class));

        ArgumentCaptor<PutTargetsRequest> captor = ArgumentCaptor.forClass(PutTargetsRequest.class);
        verify(eventBridgeClient).putTargets(captor.capture());
        assertEquals(USER_QUEUE_ARN, captor.getValue().targets().get(0).arn());

        consumer.stop();
    }

    @Test
    public void testAutoCreateDisabledWithoutQueueUrlFails() throws Exception {
        EventbridgeComponent component = context.getComponent("aws2-eventbridge", EventbridgeComponent.class);
        String uri = "aws2-eventbridge://default?accessKey=test&secretKey=test&ruleName=test-rule"
                     + "&autoCreateQueue=false";
        EventbridgeEndpoint endpoint = (EventbridgeEndpoint) component.createEndpoint(uri);
        endpoint.getConfiguration().setEventbridgeClient(eventBridgeClient);

        EventbridgeEndpoint spyEndpoint = Mockito.spy(endpoint);
        doReturn(sqsClient).when(spyEndpoint).getSqsClient();

        Processor processor = exchange -> {
        };
        EventbridgeConsumer consumer = new EventbridgeConsumer(spyEndpoint, processor);
        consumer.setStartScheduler(false);

        assertThrows(IllegalArgumentException.class, consumer::start);
    }

    @Test
    public void testRuleNameRequiredForConsumer() throws Exception {
        EventbridgeComponent component = context.getComponent("aws2-eventbridge", EventbridgeComponent.class);
        EventbridgeEndpoint endpoint = (EventbridgeEndpoint) component.createEndpoint(
                "aws2-eventbridge://default?accessKey=test&secretKey=test");

        Processor noop = exchange -> {
        };
        assertThrows(IllegalArgumentException.class, () -> endpoint.createConsumer(noop));
    }

    @Test
    public void testPutTargetsFailureThrows() throws Exception {
        stubAutoCreateQueue();

        when(eventBridgeClient.putTargets(any(PutTargetsRequest.class)))
                .thenReturn(PutTargetsResponse.builder()
                        .failedEntryCount(1)
                        .failedEntries(PutTargetsResultEntry.builder()
                                .errorCode("InternalError")
                                .errorMessage("Something went wrong")
                                .build())
                        .build());

        EventbridgeConsumer consumer = createConsumer("test-rule", null, true, true);

        IllegalStateException exception = assertThrows(IllegalStateException.class, consumer::start);
        assertTrue(exception.getMessage().contains("Failed to add SQS target"));
    }

    // -- Polling tests --

    @Test
    public void testPollReceivesMessages() throws Exception {
        stubAutoCreateQueue();
        stubPutTargetsSuccess();
        stubReceiveMessages(
                createSqsMessage("msg-1", "rh-1", "{\"source\":\"camel.test\",\"detail\":{\"key\":\"value1\"}}"),
                createSqsMessage("msg-2", "rh-2", "{\"source\":\"camel.test\",\"detail\":{\"key\":\"value2\"}}"));

        EventbridgeConsumer consumer = createConsumer("test-rule", null, true, true);
        consumer.start();

        int count = consumer.poll();

        assertEquals(2, count);
        assertEquals(2, receivedExchanges.size());

        consumer.stop();
    }

    @Test
    public void testPollReturnsZeroWhenNoMessages() throws Exception {
        stubAutoCreateQueue();
        stubPutTargetsSuccess();
        stubReceiveMessages();

        EventbridgeConsumer consumer = createConsumer("test-rule", null, true, true);
        consumer.start();

        int count = consumer.poll();

        assertEquals(0, count);
        assertEquals(0, receivedExchanges.size());

        consumer.stop();
    }

    @Test
    public void testConsumerHeadersSet() throws Exception {
        stubAutoCreateQueue();
        stubPutTargetsSuccess();
        stubReceiveMessages(createSqsMessage("msg-123", "rh-456", "{\"detail\":\"test\"}"));

        EventbridgeConsumer consumer = createConsumer("test-rule", null, true, true);
        consumer.start();
        consumer.poll();

        assertEquals(1, receivedExchanges.size());
        Exchange exchange = receivedExchanges.get(0);
        assertEquals("msg-123", exchange.getMessage().getHeader(EventbridgeConstants.MESSAGE_ID, String.class));
        assertEquals("rh-456", exchange.getMessage().getHeader(EventbridgeConstants.RECEIPT_HANDLE, String.class));

        consumer.stop();
    }

    @Test
    public void testBatchProperties() throws Exception {
        stubAutoCreateQueue();
        stubPutTargetsSuccess();
        stubReceiveMessages(
                createSqsMessage("msg-1", "rh-1", "body1"),
                createSqsMessage("msg-2", "rh-2", "body2"),
                createSqsMessage("msg-3", "rh-3", "body3"));

        EventbridgeConsumer consumer = createConsumer("test-rule", null, true, true);
        consumer.start();
        consumer.poll();

        assertEquals(3, receivedExchanges.size());

        assertEquals(0, receivedExchanges.get(0).getProperty(ExchangePropertyKey.BATCH_INDEX, Integer.class));
        assertEquals(3, receivedExchanges.get(0).getProperty(ExchangePropertyKey.BATCH_SIZE, Integer.class));
        assertFalse(receivedExchanges.get(0).getProperty(ExchangePropertyKey.BATCH_COMPLETE, Boolean.class));

        assertEquals(1, receivedExchanges.get(1).getProperty(ExchangePropertyKey.BATCH_INDEX, Integer.class));
        assertEquals(3, receivedExchanges.get(1).getProperty(ExchangePropertyKey.BATCH_SIZE, Integer.class));
        assertFalse(receivedExchanges.get(1).getProperty(ExchangePropertyKey.BATCH_COMPLETE, Boolean.class));

        assertEquals(2, receivedExchanges.get(2).getProperty(ExchangePropertyKey.BATCH_INDEX, Integer.class));
        assertEquals(3, receivedExchanges.get(2).getProperty(ExchangePropertyKey.BATCH_SIZE, Integer.class));
        assertTrue(receivedExchanges.get(2).getProperty(ExchangePropertyKey.BATCH_COMPLETE, Boolean.class));

        consumer.stop();
    }

    @Test
    public void testPollPassesConfigToReceiveRequest() throws Exception {
        stubAutoCreateQueue();
        stubPutTargetsSuccess();
        stubReceiveMessages();

        EventbridgeConsumer consumer = createConsumerWithCustomConfig("test-rule", 5, 10, 60);
        consumer.start();
        consumer.poll();

        ArgumentCaptor<ReceiveMessageRequest> captor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsClient).receiveMessage(captor.capture());
        assertEquals(5, captor.getValue().maxNumberOfMessages());
        assertEquals(10, captor.getValue().waitTimeSeconds());
        assertEquals(60, captor.getValue().visibilityTimeout());

        consumer.stop();
    }

    // -- Message deletion tests --

    @Test
    public void testMessageDeletedOnSuccess() throws Exception {
        stubAutoCreateQueue();
        stubPutTargetsSuccess();
        stubReceiveMessages(createSqsMessage("msg-1", "receipt-success", "body"));

        EventbridgeConsumer consumer = createConsumer("test-rule", null, true, true);
        consumer.start();
        consumer.poll();

        assertEquals(1, receivedExchanges.size());
        Exchange exchange = receivedExchanges.get(0);

        // Manually fire onCompletion since there is no UnitOfWork in a direct poll() unit test
        List<Synchronization> completions = exchange.getExchangeExtension().handoverCompletions();
        assertNotNull(completions);
        assertFalse(completions.isEmpty());
        for (Synchronization sync : completions) {
            sync.onComplete(exchange);
        }

        ArgumentCaptor<DeleteMessageRequest> captor = ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(sqsClient).deleteMessage(captor.capture());
        assertEquals("receipt-success", captor.getValue().receiptHandle());

        consumer.stop();
    }

    @Test
    public void testMessageLeftInQueueOnFailure() throws Exception {
        stubAutoCreateQueue();
        stubPutTargetsSuccess();
        stubReceiveMessages(createSqsMessage("msg-1", "receipt-fail", "body"));

        // Use a processor that captures the exchange and marks it as failed
        List<Exchange> failedExchanges = new ArrayList<>();
        Processor failingProcessor = exchange -> {
            failedExchanges.add(exchange);
            throw new RuntimeException("Processing failed");
        };
        EventbridgeConsumer consumer = createConsumerWithProcessor("test-rule", failingProcessor);
        consumer.start();
        consumer.poll();

        assertEquals(1, failedExchanges.size());
        Exchange exchange = failedExchanges.get(0);

        // Manually fire onFailure since there is no UnitOfWork in a direct poll() unit test
        List<Synchronization> completions = exchange.getExchangeExtension().handoverCompletions();
        assertNotNull(completions);
        for (Synchronization sync : completions) {
            sync.onFailure(exchange);
        }

        // deleteMessage should NOT have been called
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));

        consumer.stop();
    }

    // -- Cleanup / shutdown tests --

    @Test
    public void testCleanupRemovesTargetAndDeletesQueue() throws Exception {
        stubAutoCreateQueue();
        stubPutTargetsSuccess();
        when(eventBridgeClient.removeTargets(any(RemoveTargetsRequest.class)))
                .thenReturn(RemoveTargetsResponse.builder().build());

        EventbridgeConsumer consumer = createConsumer("test-rule", null, true, true);
        consumer.start();
        consumer.stop();

        verify(eventBridgeClient).removeTargets(any(RemoveTargetsRequest.class));
        verify(sqsClient).deleteQueue(any(DeleteQueueRequest.class));
    }

    @Test
    public void testDeleteQueueOnShutdownFalseKeepsQueue() throws Exception {
        stubAutoCreateQueue();
        stubPutTargetsSuccess();
        when(eventBridgeClient.removeTargets(any(RemoveTargetsRequest.class)))
                .thenReturn(RemoveTargetsResponse.builder().build());

        EventbridgeConsumer consumer = createConsumer("test-rule", null, true, false);
        consumer.start();
        consumer.stop();

        // Target is still removed
        verify(eventBridgeClient).removeTargets(any(RemoveTargetsRequest.class));
        // But queue is NOT deleted
        verify(sqsClient, never()).deleteQueue(any(DeleteQueueRequest.class));
    }

    @Test
    public void testUserProvidedQueueNotDeletedOnShutdown() throws Exception {
        stubGetQueueAttributes(USER_QUEUE_URL, USER_QUEUE_ARN);
        stubPutTargetsSuccess();
        when(eventBridgeClient.removeTargets(any(RemoveTargetsRequest.class)))
                .thenReturn(RemoveTargetsResponse.builder().build());

        // deleteQueueOnShutdown=true, but queue was user-provided so should NOT be deleted
        EventbridgeConsumer consumer = createConsumer("test-rule", USER_QUEUE_URL, true, true);
        consumer.start();
        consumer.stop();

        verify(eventBridgeClient).removeTargets(any(RemoveTargetsRequest.class));
        verify(sqsClient, never()).deleteQueue(any(DeleteQueueRequest.class));
    }

    @Test
    public void testCleanupSwallowsExceptions() throws Exception {
        stubAutoCreateQueue();
        stubPutTargetsSuccess();
        when(eventBridgeClient.removeTargets(any(RemoveTargetsRequest.class)))
                .thenThrow(new RuntimeException("EventBridge cleanup error"));
        doThrow(new RuntimeException("SQS cleanup error"))
                .when(sqsClient).deleteQueue(any(DeleteQueueRequest.class));

        EventbridgeConsumer consumer = createConsumer("test-rule", null, true, true);
        consumer.start();

        // Should not throw despite cleanup failures
        assertDoesNotThrow(consumer::stop);
    }

    // -- Helper methods --

    private EventbridgeConsumer createConsumer(
            String ruleName, String queueUrl, boolean autoCreateQueue, boolean deleteQueueOnShutdown)
            throws Exception {
        EventbridgeComponent component = context.getComponent("aws2-eventbridge", EventbridgeComponent.class);
        component.getConfiguration().setAccessKey("test");
        component.getConfiguration().setSecretKey("test");

        String uri = "aws2-eventbridge://default?accessKey=test&secretKey=test&ruleName=" + ruleName
                     + "&autoCreateQueue=" + autoCreateQueue
                     + "&deleteQueueOnShutdown=" + deleteQueueOnShutdown;
        if (queueUrl != null) {
            uri += "&queueUrl=" + queueUrl;
        }

        EventbridgeEndpoint endpoint = (EventbridgeEndpoint) component.createEndpoint(uri);
        endpoint.getConfiguration().setEventbridgeClient(eventBridgeClient);

        EventbridgeEndpoint spyEndpoint = Mockito.spy(endpoint);
        doReturn(sqsClient).when(spyEndpoint).getSqsClient();
        doReturn(eventBridgeClient).when(spyEndpoint).getEventbridgeClient();

        Processor processor = exchange -> receivedExchanges.add(exchange);
        EventbridgeConsumer consumer = new EventbridgeConsumer(spyEndpoint, processor);
        consumer.setStartScheduler(false);
        return consumer;
    }

    private EventbridgeConsumer createConsumerWithCustomConfig(
            String ruleName, int maxMessages, int waitTime, int visibilityTimeout)
            throws Exception {
        EventbridgeComponent component = context.getComponent("aws2-eventbridge", EventbridgeComponent.class);
        component.getConfiguration().setAccessKey("test");
        component.getConfiguration().setSecretKey("test");

        String uri = "aws2-eventbridge://default?accessKey=test&secretKey=test&ruleName=" + ruleName
                     + "&maxMessagesPerPoll=" + maxMessages
                     + "&waitTimeSeconds=" + waitTime
                     + "&visibilityTimeout=" + visibilityTimeout;

        EventbridgeEndpoint endpoint = (EventbridgeEndpoint) component.createEndpoint(uri);
        endpoint.getConfiguration().setEventbridgeClient(eventBridgeClient);

        EventbridgeEndpoint spyEndpoint = Mockito.spy(endpoint);
        doReturn(sqsClient).when(spyEndpoint).getSqsClient();
        doReturn(eventBridgeClient).when(spyEndpoint).getEventbridgeClient();

        Processor processor = exchange -> receivedExchanges.add(exchange);
        EventbridgeConsumer consumer = new EventbridgeConsumer(spyEndpoint, processor);
        consumer.setStartScheduler(false);
        return consumer;
    }

    private EventbridgeConsumer createConsumerWithProcessor(String ruleName, Processor processor) throws Exception {
        EventbridgeComponent component = context.getComponent("aws2-eventbridge", EventbridgeComponent.class);
        component.getConfiguration().setAccessKey("test");
        component.getConfiguration().setSecretKey("test");

        String uri = "aws2-eventbridge://default?accessKey=test&secretKey=test&ruleName=" + ruleName;
        EventbridgeEndpoint endpoint = (EventbridgeEndpoint) component.createEndpoint(uri);
        endpoint.getConfiguration().setEventbridgeClient(eventBridgeClient);

        EventbridgeEndpoint spyEndpoint = Mockito.spy(endpoint);
        doReturn(sqsClient).when(spyEndpoint).getSqsClient();
        doReturn(eventBridgeClient).when(spyEndpoint).getEventbridgeClient();

        EventbridgeConsumer consumer = new EventbridgeConsumer(spyEndpoint, processor);
        consumer.setStartScheduler(false);
        return consumer;
    }

    private void stubAutoCreateQueue() {
        when(sqsClient.createQueue(any(CreateQueueRequest.class)))
                .thenReturn(CreateQueueResponse.builder().queueUrl(TEST_QUEUE_URL).build());
        stubGetQueueAttributes(TEST_QUEUE_URL, TEST_QUEUE_ARN);
    }

    private void stubGetQueueAttributes(String queueUrl, String queueArn) {
        when(sqsClient.getQueueAttributes(any(GetQueueAttributesRequest.class)))
                .thenReturn(GetQueueAttributesResponse.builder()
                        .attributes(Map.of(QueueAttributeName.QUEUE_ARN, queueArn))
                        .build());
    }

    private void stubPutTargetsSuccess() {
        when(eventBridgeClient.putTargets(any(PutTargetsRequest.class)))
                .thenReturn(PutTargetsResponse.builder().failedEntryCount(0).build());
    }

    private void stubReceiveMessages(Message... messages) {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(messages).build());
    }

    private Message createSqsMessage(String messageId, String receiptHandle, String body) {
        return Message.builder()
                .messageId(messageId)
                .receiptHandle(receiptHandle)
                .body(body)
                .build();
    }
}
