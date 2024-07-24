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
package org.apache.camel.component.aws2.sqs;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.function.IOConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDeletedRecentlyException;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

@ExtendWith(MockitoExtension.class)
class Sqs2PollingClientTest {
    @Mock
    private SqsClient sqsClient;
    @Mock
    private IOConsumer<SqsClient> createQueueOperation;
    private Sqs2Configuration configuration;
    @Mock
    private Clock clock;

    @BeforeEach
    void setup() {
        configuration = new Sqs2Configuration();
        configuration.setMessageAttributeNames("foo,bar,bazz");
        configuration.setAttributeNames("fuzz,wazz");
        configuration.setWaitTimeSeconds(13);
        configuration.setVisibilityTimeout(512);
        configuration.setQueueUrl("sqs://queue");
    }

    @SuppressWarnings("resource")
    @Test
    void shouldIgnoreNullAttributeNames() throws IOException {
        // given
        configuration.setAttributeNames(null);
        configuration.setMessageAttributeNames(null);
        var tested = createPollingClient(-1);

        doReturn(responseOf()).when(sqsClient).receiveMessage(any(ReceiveMessageRequest.class));

        // when
        var polledMessages = tested.poll();

        // then
        assertEquals(emptyList(), polledMessages);

        var expectedRequest = expectedReceiveRequestBuilder()
            .messageSystemAttributeNamesWithStrings((List<String>) null)
            .messageAttributeNames((List<String>) null)
            .maxNumberOfMessages(1)
            .build();
        BDDMockito.then(sqsClient).should(times(1)).receiveMessage(expectedRequest);
        BDDMockito.then(createQueueOperation).shouldHaveNoInteractions();
    }

    @SuppressWarnings("resource")
    @Test
    void shouldIgnoreEmptyAttributeNames() throws IOException {
        // given
        configuration.setAttributeNames("");
        configuration.setMessageAttributeNames("");
        var tested = createPollingClient(9);

        doReturn(responseOf()).when(sqsClient).receiveMessage(any(ReceiveMessageRequest.class));

        // when
        var polledMessages = tested.poll();

        // then
        assertEquals(emptyList(), polledMessages);

        var expectedRequest = expectedReceiveRequestBuilder()
            .messageSystemAttributeNamesWithStrings((List<String>) null)
            .messageAttributeNames((List<String>) null)
            .maxNumberOfMessages(9)
            .build();
        BDDMockito.then(sqsClient).should(times(1)).receiveMessage(expectedRequest);
        BDDMockito.then(createQueueOperation).shouldHaveNoInteractions();
    }

    @SuppressWarnings("resource")
    @Test
    void shouldRequestSingleMessage() throws IOException {
        // given
        var tested = createPollingClient(0);

        doReturn(responseOf(message("1"))).when(sqsClient).receiveMessage(expectedReceiveRequest(1));

        // when
        var polledMessages = tested.poll();

        // then
        var expectedMessages = List.of(message("1"));
        assertEquals(expectedMessages, polledMessages);

        BDDMockito.then(sqsClient).should(times(1)).receiveMessage(expectedReceiveRequest(1));
        BDDMockito.then(createQueueOperation).shouldHaveNoInteractions();
    }

    @SuppressWarnings("resource")
    @Test
    void shouldRequestUpTo10MessagesWithASingleRequest() throws IOException {
        // given
        var tested = createPollingClient(10);

        doReturn(responseOf(message("1"), message("2"))).when(sqsClient).receiveMessage(expectedReceiveRequest(10));

        // when
        var polledMessages = tested.poll();

        // then
        var expectedMessages = List.of(message("1"), message("2"));
        assertEquals(expectedMessages, polledMessages);

        BDDMockito.then(sqsClient).should(times(1)).receiveMessage(expectedReceiveRequest(10));
        BDDMockito.then(createQueueOperation).shouldHaveNoInteractions();
    }

    @SuppressWarnings("resource")
    @Test
    void shouldRequestUpTo20MessagesSplitWith2RequestsAndSortedBySequenceNumber() throws IOException {
        // given
        var tested = createPollingClient(20);

        doReturn(
            responseOf(message("1"), message("4")),
            responseOf(message("2"), message("3")))
            .when(sqsClient)
            .receiveMessage(expectedReceiveRequest(10));


        // when
        var polledMessages = tested.poll();

        // then
        var expectedMessages = List.of(
            message("1"),
            message("2"),
            message("3"),
            message("4"));
        assertEquals(expectedMessages, polledMessages);

        BDDMockito.then(sqsClient).should(times(2)).receiveMessage(expectedReceiveRequest(10));
        BDDMockito.then(createQueueOperation).shouldHaveNoInteractions();
    }

    @SuppressWarnings("resource")
    @Test
    void shouldRequestUpTo11MessagesSplitWith2RequestsAndSortedBySequenceNumber() throws IOException {
        // given
        doReturn(
            responseOf(message("1"), message("2"), message("5")))
            .when(sqsClient)
            .receiveMessage(expectedReceiveRequest(10));
        doReturn(responseOf(message("3"), message("4")))
            .when(sqsClient)
            .receiveMessage(expectedReceiveRequest(1));

        var tested = createPollingClient(11);

        // when
        var polledMessages = tested.poll();

        // then
        var expected = List.of(
            message("1"),
            message("2"),
            message("3"),
            message("4"),
            message("5"));
        assertEquals(expected, polledMessages);

        BDDMockito.then(sqsClient).should(times(1)).receiveMessage(expectedReceiveRequest(10));
        BDDMockito.then(sqsClient).should(times(1)).receiveMessage(expectedReceiveRequest(1));
        BDDMockito.then(createQueueOperation).shouldHaveNoInteractions();
    }

    @SuppressWarnings("resource")
    @Test
    void shouldRequestUpTo33MessagesSplitWith4RequestsAndSortedBySequenceNumber() throws IOException {
        // given
        doReturn(
            responseOf(message("1"), message("5"), message("7")),
            responseOf(message("2"), message("4")),
            responseOf(message("3")))
            .when(sqsClient)
            .receiveMessage(expectedReceiveRequest(10));
        doReturn(responseOf(message("6"), message("8")))
            .when(sqsClient)
            .receiveMessage(expectedReceiveRequest(3));

        var tested = createPollingClient(33);

        // when
        var polledMessages = tested.poll();

        // then
        var expected = List.of(
            message("1"),
            message("2"),
            message("3"),
            message("4"),
            message("5"),
            message("6"),
            message("7"),
            message("8"));
        assertEquals(expected, polledMessages);

        BDDMockito.then(sqsClient).should(times(3)).receiveMessage(expectedReceiveRequest(10));
        BDDMockito.then(sqsClient).should(times(1)).receiveMessage(expectedReceiveRequest(3));
        BDDMockito.then(createQueueOperation).shouldHaveNoInteractions();
    }

    @SuppressWarnings("resource")
    @Test
    void shouldRequestUpTo18MessagesSplitWith2RequestsWithSortingIgnored() throws IOException {
        // given
        doReturn(responseOf(
            Message.builder().messageId("id1").build(),
            Message.builder().messageId("id2").build()))
            .when(sqsClient)
            .receiveMessage(expectedReceiveRequest(10));
        doReturn(responseOf(
            Message.builder().messageId("id3").build()))
            .when(sqsClient)
            .receiveMessage(expectedReceiveRequest(8));

        var tested = createPollingClient(18);

        // when
        var polledMessages = tested.poll();

        // then
        var expected = List.of(
            Message.builder().messageId("id1").build(),
            Message.builder().messageId("id2").build(),
            Message.builder().messageId("id3").build());
        assertEquals(expected, polledMessages);

        BDDMockito.then(sqsClient).should(times(1)).receiveMessage(expectedReceiveRequest(10));
        BDDMockito.then(sqsClient).should(times(1)).receiveMessage(expectedReceiveRequest(8));
        BDDMockito.then(createQueueOperation).shouldHaveNoInteractions();
    }

    @Test
    void shouldIgnorePollingAfterShutdown( )throws IOException {
        // given
        var tested = createPollingClient(100);
        tested.shutdown();

        // when
        var polledMessages = tested.poll();

        // then
        assertEquals(emptyList(), polledMessages);

        BDDMockito.then(sqsClient).shouldHaveNoInteractions();
        BDDMockito.then(createQueueOperation).shouldHaveNoInteractions();
    }

    @SuppressWarnings("resource")
    @Test
    void shouldRethrowQueueDoesNotExistException() {
        // given
        var tested = createPollingClient(1);

        doThrow(QueueDoesNotExistException.class).when(sqsClient).receiveMessage(expectedReceiveRequest(1));

        // when-then
        assertThrows(QueueDoesNotExistException.class, () -> tested.poll());

        BDDMockito.then(createQueueOperation).shouldHaveNoInteractions();
        BDDMockito.then(clock).shouldHaveNoInteractions();
    }

    @SuppressWarnings("resource")
    @Test
    void shouldCreateMissingQueueOnException() throws IOException {
        // given
        configuration.setAutoCreateQueue(true);
        var tested = createPollingClient(10);
        doThrow(QueueDoesNotExistException.class).when(sqsClient).receiveMessage(expectedReceiveRequest(10));

        // when
        var polledMessages = tested.poll();

        // then
        assertEquals(emptyList(), polledMessages);

        BDDMockito.then(sqsClient).should(times(1)).receiveMessage(expectedReceiveRequest(10));
        BDDMockito.then(createQueueOperation).should().accept(sqsClient);
    }

    @SuppressWarnings("resource")
    @Test
    void shouldCreateMissingQueueOnExceptionOnce() throws IOException {
        // given
        configuration.setAutoCreateQueue(true);
        var tested = createPollingClient(1555);
        doThrow(QueueDoesNotExistException.class).when(sqsClient).receiveMessage(any(ReceiveMessageRequest.class));

        // when
        var polledMessages = tested.poll();

        // then
        assertEquals(emptyList(), polledMessages);

        BDDMockito.then(sqsClient).should(atMost(155)).receiveMessage(expectedReceiveRequest(10));
        BDDMockito.then(sqsClient).should(atMost(1)).receiveMessage(expectedReceiveRequest(5));
        BDDMockito.then(createQueueOperation).should(only()).accept(sqsClient);
    }

    @SuppressWarnings("resource")
    @Test
    void shouldCreateMissingQueueWith30SecondsBackoffTimeOnRecentlyDeletedQueue() throws IOException {
        // given
        configuration.setAutoCreateQueue(true);
        var createQueueRequestCount = new AtomicInteger();
        var currentTime = System.currentTimeMillis();
        var tested = createPollingClient(1);

        doThrow(QueueDoesNotExistException.class).when(sqsClient).receiveMessage(any(ReceiveMessageRequest.class));
        doAnswer(args -> {
            if (createQueueRequestCount.getAndIncrement() == 0) {
                throw QueueDeletedRecentlyException.builder().build();
            }
            return null;
        }).when(createQueueOperation).accept(sqsClient);

        doReturn(currentTime).when(clock).millis();

        // when
        var polledMessages1 = tested.poll();

        // then queue creation should be scheduled
        assertEquals(emptyList(), polledMessages1);
        assertEquals(1, createQueueRequestCount.get());
        BDDMockito.then(sqsClient).should(times(1)).receiveMessage(expectedReceiveRequest(1));

        // when 29.999 seconds passed on next polling
        doReturn(currentTime + 29_999).when(clock).millis();
        var polledMessages2 = tested.poll();

        // then queue creation should be ignored
        assertEquals(emptyList(), polledMessages2);
        assertEquals(1, createQueueRequestCount.get());
        BDDMockito.then(sqsClient).should(times(1)).receiveMessage(expectedReceiveRequest(1));

        // when 30 seconds passed on next polling
        doReturn(currentTime + 30_000).when(clock).millis();
        var polledMessages3 = tested.poll();

        // then queue creation should be invoked
        assertEquals(emptyList(), polledMessages3);
        assertEquals(2, createQueueRequestCount.get());
        BDDMockito.then(sqsClient).should(times(1)).receiveMessage(expectedReceiveRequest(1));
    }

    @SuppressWarnings("resource")
    @Test
    void shouldIgnoreErrorsOnAtLeastOneSuccessfulReceiveRequest() throws IOException {
        // given
        var tested = createPollingClient(25);

        doThrow(SqsException.class).when(sqsClient).receiveMessage(expectedReceiveRequest(10));
        doReturn(responseOf(message("1"), message("2"))).when(sqsClient).receiveMessage(expectedReceiveRequest(5));

        // when
        var polledMessages = tested.poll();

        // then
        var expectedMessages = List.of(message("1"), message("2"));
        assertEquals(expectedMessages, polledMessages);

        BDDMockito.then(sqsClient).should(times(2)).receiveMessage(expectedReceiveRequest(10));
        BDDMockito.then(sqsClient).should(times(1)).receiveMessage(expectedReceiveRequest(5));
        BDDMockito.then(createQueueOperation).shouldHaveNoInteractions();
    }

    @SuppressWarnings("resource")
    @Test
    void shouldThrowIOExceptionWhenAllReceiveRequestFails() {
        // given
        var tested = createPollingClient(25);

        doThrow(SqsException.class).when(sqsClient).receiveMessage(expectedReceiveRequest(10));
        doThrow(SqsException.class).when(sqsClient).receiveMessage(expectedReceiveRequest(5));

        // when
        var caughtException = assertThrows(IOException.class, () -> tested.poll());

        // then
        var expectedMessage = "Error while polling - all 3 requests resulted in an error, please check the logs for more details";
        assertEquals(expectedMessage, caughtException.getMessage());

        BDDMockito.then(sqsClient).should(times(2)).receiveMessage(expectedReceiveRequest(10));
        BDDMockito.then(sqsClient).should(times(1)).receiveMessage(expectedReceiveRequest(5));
        BDDMockito.then(createQueueOperation).shouldHaveNoInteractions();
    }

    @SuppressWarnings("resource")
    @Test
    void shouldRethrowSqsExceptionOnError() {
        // given
        var tested = createPollingClient(10);
        var exception = SqsException.builder().requestId("foo").build();
        doThrow(exception).when(sqsClient).receiveMessage(expectedReceiveRequest(10));

        // when
        var caughtException = assertThrows(Exception.class, () -> tested.poll());

        // then
        assertEquals(exception, caughtException);

        BDDMockito.then(sqsClient).should(times(1)).receiveMessage(expectedReceiveRequest(10));
        BDDMockito.then(createQueueOperation).shouldHaveNoInteractions();
    }

    private Sqs2PollingClient createPollingClient(int maxNumberOfMessages) {
        return new Sqs2PollingClient(sqsClient, maxNumberOfMessages, configuration, createQueueOperation, clock);
    }

    private ReceiveMessageRequest.Builder expectedReceiveRequestBuilder() {
        return ReceiveMessageRequest.builder()
            .queueUrl(configuration.getQueueUrl())
            .waitTimeSeconds(configuration.getWaitTimeSeconds())
            .visibilityTimeout(configuration.getVisibilityTimeout())
            .messageAttributeNames(List.of("foo", "bar", "bazz"))
            .messageSystemAttributeNamesWithStrings(List.of("fuzz", "wazz"));
    }

    private ReceiveMessageRequest expectedReceiveRequest(int maxNumberOfMessages) {
        return expectedReceiveRequestBuilder()
            .maxNumberOfMessages(maxNumberOfMessages)
            .build();
    }

    private static ReceiveMessageResponse responseOf(Message... messages) {
        final var response = mock(ReceiveMessageResponse.class);
        doReturn(List.of(messages)).when(response).messages();
        return response;
    }

    private static Message message(String sequenceNumber) {
        return Message.builder()
            .attributes(Map.of(MessageSystemAttributeName.SEQUENCE_NUMBER, sequenceNumber))
            .build();
    }
}
