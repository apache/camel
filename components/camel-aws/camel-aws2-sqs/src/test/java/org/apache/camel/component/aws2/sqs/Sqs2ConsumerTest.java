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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.apache.camel.ContextEvents;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.clock.EventClock;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDeletedRecentlyException;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.mockito.Mockito.doReturn;
import static software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName.ALL;
import static software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName.MESSAGE_GROUP_ID;
import static software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName.SENT_TIMESTAMP;
import static software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName.SEQUENCE_NUMBER;

@ExtendWith(MockitoExtension.class)
class Sqs2ConsumerTest extends CamelTestSupport {
    private AmazonSQSClientMock sqsClientMock;
    private Sqs2Configuration configuration;
    @Mock
    private EventClock<ContextEvents> clock;

    private final List<Exchange> receivedExchanges = new ArrayList<>();
    private final AtomicInteger sequenceNumber = new AtomicInteger();
    private boolean generateSequenceNumber;

    @BeforeEach
    void setup() {
        sqsClientMock = new AmazonSQSClientMock();
        sqsClientMock.setQueueName("test");
        configuration = new Sqs2Configuration();
        configuration.setMessageAttributeNames("foo,bar,bazz");
        configuration.setAttributeNames("SentTimestamp, MessageGroupId");
        configuration.setSortAttributeName("SequenceNumber");
        configuration.setWaitTimeSeconds(13);
        configuration.setVisibilityTimeout(512);
        configuration.setQueueUrl("/test");
        configuration.setQueueName("test");
        configuration.setConcurrentRequestLimit(15);
        configuration.setUseDefaultCredentialsProvider(true);

        receivedExchanges.clear();
        sequenceNumber.set(0);
        generateSequenceNumber = true;
    }

    @Test
    void shouldIgnoreNullAttributeNames() throws Exception {
        // given
        configuration.setAttributeNames(null);
        configuration.setMessageAttributeNames(null);
        configuration.setSortAttributeName(null);
        try (var tested = createConsumer(-1)) {
            // when
            var polledMessagesCount = tested.poll();

            // then
            var expectedRequest = expectedReceiveRequestBuilder()
                    .messageSystemAttributeNames((List<MessageSystemAttributeName>) null)
                    .messageAttributeNames((List<String>) null)
                    .maxNumberOfMessages(1)
                    .build();
            assertThat(polledMessagesCount).isZero();
            assertThat(receivedExchanges).isEmpty();
            assertThat(sqsClientMock.getReceiveRequests()).containsExactlyInAnyOrder(expectedRequest);
            assertThat(sqsClientMock.getQueues()).isEmpty();
        }
    }

    @Test
    void shouldIgnoreEmptyAttributeNames() throws Exception {
        // given
        configuration.setAttributeNames("");
        configuration.setMessageAttributeNames("");
        configuration.setSortAttributeName("");
        try (var tested = createConsumer(9)) {

            // when
            var polledMessagesCount = tested.poll();

            // then
            var expectedRequest = expectedReceiveRequestBuilder()
                    .messageSystemAttributeNames((List<MessageSystemAttributeName>) null)
                    .messageAttributeNames((List<String>) null)
                    .maxNumberOfMessages(9)
                    .build();
            assertThat(polledMessagesCount).isZero();
            assertThat(receivedExchanges).isEmpty();
            assertThat(sqsClientMock.getReceiveRequests()).containsExactlyInAnyOrder(expectedRequest);
            assertThat(sqsClientMock.getQueues()).isEmpty();
        }
    }

    @Test
    void shouldInnoreUnsupportedAttributeNames() throws Exception {
        // given
        configuration.setAttributeNames("foo, bar");
        try (var tested = createConsumer(-1)) {
            // when
            var polledMessagesCount = tested.poll();

            // then
            var expectedRequest = expectedReceiveRequestBuilder()
                    .messageSystemAttributeNames(List.of(SEQUENCE_NUMBER))
                    .maxNumberOfMessages(1)
                    .build();
            assertThat(polledMessagesCount).isZero();
            assertThat(receivedExchanges).isEmpty();
            assertThat(sqsClientMock.getReceiveRequests()).containsExactlyInAnyOrder(expectedRequest);
            assertThat(sqsClientMock.getQueues()).isEmpty();
        }
    }

    @Test
    void shouldIgnoreSortingByAllAttribute() throws Exception {
        // given
        configuration.setSortAttributeName("All");
        try (var tested = createConsumer(-1)) {
            // when
            var polledMessagesCount = tested.poll();

            // then
            var expectedRequest = expectedReceiveRequestBuilder()
                    .messageSystemAttributeNames(List.of(SENT_TIMESTAMP, MESSAGE_GROUP_ID))
                    .maxNumberOfMessages(1)
                    .build();
            assertThat(polledMessagesCount).isZero();
            assertThat(receivedExchanges).isEmpty();
            assertThat(sqsClientMock.getReceiveRequests()).containsExactlyInAnyOrder(expectedRequest);
            assertThat(sqsClientMock.getQueues()).isEmpty();
        }
    }

    @Test
    void shouldIgnoreAddingSortAttributeWhenAllAttributesAreRequested() throws Exception {
        // given
        configuration.setAttributeNames("All");
        try (var tested = createConsumer(-1)) {
            // when
            var polledMessagesCount = tested.poll();

            // then
            var expectedRequest = expectedReceiveRequestBuilder()
                    .messageSystemAttributeNames(List.of(ALL))
                    .maxNumberOfMessages(1)
                    .build();
            assertThat(polledMessagesCount).isZero();
            assertThat(receivedExchanges).isEmpty();
            assertThat(sqsClientMock.getReceiveRequests()).containsExactlyInAnyOrder(expectedRequest);
            assertThat(sqsClientMock.getQueues()).isEmpty();
        }
    }

    @Test
    void shouldRequestSingleMessageWithSingleReceiveRequest() throws Exception {
        // given
        sqsClientMock.addMessage(message("A"));
        sqsClientMock.addMessage(message("B"));

        try (var tested = createConsumer(0)) {

            // when
            var polledMessagesCount = tested.poll();

            // then
            assertThat(polledMessagesCount).isOne();
            assertThat(receiveMessageBodies()).containsExactly("A");
            assertThat(sqsClientMock.getReceiveRequests()).containsExactlyInAnyOrder(expectedReceiveRequest(1));
            assertThat(sqsClientMock.getQueues()).isEmpty();
        }
    }

    @Test
    void consumerStopsExchangeFromExtendingVisibilityOnError() throws Exception {
        // given
        sqsClientMock.addMessage(message("A"));
        configuration.setExtendMessageVisibility(true);
        configuration.setWaitTimeSeconds(5);
        configuration.setVisibilityTimeout(2);
        configuration.setConcurrentConsumers(1);
        try (var tested = createConsumerWithProcessor(1, exchange -> {
            Thread.sleep(2000L);
            throw new OutOfMemoryError();
        })) {
            //when
            try {
                tested.poll();
            } catch (Error e) {
            }
            //simulate some time pass after error
            Thread.sleep(2000L);
            // then
            assertThat(sqsClientMock.getChangeMessageVisibilityBatchRequests().size()).isEqualTo(2);
        }
    }

    @Test
    void shouldRequest10MessagesWithSingleReceiveRequest() throws Exception {
        // given
        var expectedMessages = IntStream.range(0, 10).mapToObj(Integer::toString).toList();
        expectedMessages.stream().map(this::message).forEach(sqsClientMock::addMessage);

        try (var tested = createConsumer(10)) {

            // when
            var polledMessagesCount = tested.poll();

            // then
            assertThat(polledMessagesCount).isEqualTo(10);
            assertThat(receiveMessageBodies()).isEqualTo(expectedMessages);
            assertThat(sqsClientMock.getReceiveRequests()).containsExactlyInAnyOrder(expectedReceiveRequest(10));
            assertThat(sqsClientMock.getQueues()).isEmpty();
        }
    }

    @Test
    void shouldRequest11MessagesWithTwoReceiveRequest() throws Exception {
        // given
        var expectedMessages = IntStream.range(0, 11).mapToObj(Integer::toString).toList();
        expectedMessages.stream().map(this::message).forEach(sqsClientMock::addMessage);

        try (var tested = createConsumer(11)) {

            // when
            var polledMessagesCount = tested.poll();

            // then
            assertThat(polledMessagesCount).isEqualTo(11);
            assertThat(receiveMessageBodies()).isEqualTo(expectedMessages);
            assertThat(sqsClientMock.getReceiveRequests()).containsExactlyInAnyOrder(
                    expectedReceiveRequest(10),
                    expectedReceiveRequest(1));
            assertThat(sqsClientMock.getQueues()).isEmpty();
        }
    }

    @Test
    void shouldRequest20MessagesWithTwoReceiveRequest() throws Exception {
        // given
        var expectedMessages = IntStream.range(0, 20).mapToObj(Integer::toString).toList();
        expectedMessages.stream().map(this::message).forEach(sqsClientMock::addMessage);

        try (var tested = createConsumer(20)) {

            // when
            var polledMessagesCount = tested.poll();

            // then
            assertThat(polledMessagesCount).isEqualTo(20);
            assertThat(receiveMessageBodies()).isEqualTo(expectedMessages);
            assertThat(sqsClientMock.getReceiveRequests()).containsExactlyInAnyOrder(
                    expectedReceiveRequest(10),
                    expectedReceiveRequest(10));
            assertThat(sqsClientMock.getQueues()).isEmpty();
        }
    }

    @Test
    void shouldRequest33MessagesWithFourReceiveRequest() throws Exception {
        // given
        var expectedMessages = IntStream.range(0, 33).mapToObj(Integer::toString).toList();
        expectedMessages.stream().map(this::message).forEach(sqsClientMock::addMessage);

        try (var tested = createConsumer(33)) {

            // when
            var polledMessagesCount = tested.poll();

            // then
            assertThat(polledMessagesCount).isEqualTo(33);
            assertThat(receiveMessageBodies()).isEqualTo(expectedMessages);
            assertThat(sqsClientMock.getReceiveRequests()).containsExactlyInAnyOrder(
                    expectedReceiveRequest(10),
                    expectedReceiveRequest(10),
                    expectedReceiveRequest(10),
                    expectedReceiveRequest(3));
            assertThat(sqsClientMock.getQueues()).isEmpty();
        }
    }

    @Test
    void shouldRequest3001MessagesWithThreehoundredAndOneReceiveRequest() throws Exception {
        // given
        var expectedMessages = IntStream.range(0, 3001).mapToObj(Integer::toString).toList();
        expectedMessages.stream().map(this::message).forEach(sqsClientMock::addMessage);

        try (var tested = createConsumer(3001)) {

            // when
            var polledMessagesCount = tested.poll();

            // then
            assertThat(polledMessagesCount).isEqualTo(3001);
            assertThat(receiveMessageBodies()).isEqualTo(expectedMessages);
            assertThat(sqsClientMock.getReceiveRequests()).hasSize(301);
            assertThat(sqsClientMock.getReceiveRequests()).contains(expectedReceiveRequest(1));
            assertThat(sqsClientMock.getQueues()).isEmpty();
        }
    }

    @Test
    void shouldRequest10MessagesWithSingleReceiveRequestAndIgnoredSequenceNumberSorting() throws Exception {
        // given
        generateSequenceNumber = false;
        var expectedMessages = IntStream.range(0, 10).mapToObj(Integer::toString).toList();
        expectedMessages.stream().map(this::message).forEach(sqsClientMock::addMessage);

        try (var tested = createConsumer(10)) {

            // when
            var polledMessagesCount = tested.poll();

            // then
            assertThat(polledMessagesCount).isEqualTo(10);
            assertThat(receiveMessageBodies()).isEqualTo(expectedMessages);
            assertThat(sqsClientMock.getReceiveRequests()).containsExactlyInAnyOrder(expectedReceiveRequest(10));
            assertThat(sqsClientMock.getQueues()).isEmpty();
        }
    }

    @Test
    void shouldRequest18MessagesWithTwoReceiveRequestWithoutSorting() throws Exception {
        // given
        generateSequenceNumber = false;
        var expectedMessages = IntStream.range(0, 18).mapToObj(Integer::toString).toList();
        expectedMessages.stream().map(this::message).forEach(sqsClientMock::addMessage);

        try (var tested = createConsumer(18)) {

            // when
            var polledMessagesCount = tested.poll();

            // then
            assertThat(polledMessagesCount).isEqualTo(18);
            assertThat(receiveMessageBodies()).containsExactlyInAnyOrderElementsOf(expectedMessages);
            assertThat(sqsClientMock.getReceiveRequests()).containsExactlyInAnyOrder(
                    expectedReceiveRequest(10),
                    expectedReceiveRequest(8));
            assertThat(sqsClientMock.getQueues()).isEmpty();
        }
    }

    @Test
    void shouldRequest66MessagesWithSevenReceiveRequestSortedBySenderId() throws Exception {
        // given
        generateSequenceNumber = false;
        var expectedMessages = IntStream.range(0, 66)
                .mapToObj(i -> Message.builder()
                        .body(Integer.toString(i))
                        .attributes(Map.of(MessageSystemAttributeName.SENDER_ID, "%02d".formatted(i)))
                        .build())
                .toList();
        expectedMessages.forEach(sqsClientMock::addMessage);

        try (var tested = createConsumer(66)) {

            // when
            var polledMessagesCount = tested.poll();

            // then
            assertThat(polledMessagesCount).isEqualTo(66);
            assertThat(receiveMessageBodies()).containsExactly(expectedMessages.stream().map(Message::body).toArray());
            assertThat(sqsClientMock.getReceiveRequests()).containsExactlyInAnyOrder(
                    expectedReceiveRequest(10),
                    expectedReceiveRequest(10),
                    expectedReceiveRequest(10),
                    expectedReceiveRequest(10),
                    expectedReceiveRequest(10),
                    expectedReceiveRequest(10),
                    expectedReceiveRequest(6));
            assertThat(sqsClientMock.getQueues()).isEmpty();
        }
    }

    @Test
    void shouldRethrowQueueDoesNotExistExceptionOnPoll() throws Exception {
        // given
        sqsClientMock.setReceiveRequestHandler(request -> {
            throw QueueDoesNotExistException.builder().build();
        });
        try (var tested = createConsumer(10)) {

            // when
            var caughtException = catchException(tested::poll);

            // then
            assertThat(caughtException).isInstanceOf(QueueDoesNotExistException.class);
            assertThat(receivedExchanges).isEmpty();
            assertThat(sqsClientMock.getReceiveRequests()).containsExactlyInAnyOrder(expectedReceiveRequest(10));
            assertThat(sqsClientMock.getQueues()).isEmpty();
        }
    }

    @Test
    void shouldAutomaticallyCreateQueueOnQueueDoesNotExistException() throws Exception {
        // given
        configuration.setAutoCreateQueue(true);
        sqsClientMock.setReceiveRequestHandler(request -> {
            throw QueueDoesNotExistException.builder().build();
        });
        sqsClientMock.setQueueUrl(null);
        try (var tested = createConsumer(5)) {

            // when
            var polledMessagesCount = tested.poll();

            // then
            assertThat(polledMessagesCount).isZero();
            assertThat(receivedExchanges).isEmpty();
            assertThat(sqsClientMock.getReceiveRequests()).containsExactlyInAnyOrder(expectedReceiveRequest(5));
            assertThat(sqsClientMock.getQueueUrlRequests()).containsExactly(GetQueueUrlRequest.builder()
                    .queueName(configuration.getQueueName())
                    .build());
            assertThat(sqsClientMock.getCreateQueueRequets()).containsExactly(CreateQueueRequest.builder()
                    .queueName(configuration.getQueueName())
                    .attributes(emptyMap())
                    .build());
        }
    }

    @Test
    void shouldIgnoreAutomaticalQueueCreationWhenAlreadyExists() throws Exception {
        // given
        configuration.setAutoCreateQueue(true);
        sqsClientMock.setReceiveRequestHandler(request -> {
            throw QueueDoesNotExistException.builder().build();
        });
        try (var tested = createConsumer(5)) {

            // when
            var polledMessagesCount = tested.poll();

            // then
            assertThat(polledMessagesCount).isZero();
            assertThat(receivedExchanges).isEmpty();
            assertThat(sqsClientMock.getReceiveRequests()).containsExactlyInAnyOrder(expectedReceiveRequest(5));
            assertThat(sqsClientMock.getQueueUrlRequests()).containsExactly(GetQueueUrlRequest.builder()
                    .queueName(configuration.getQueueName())
                    .build());
            assertThat(sqsClientMock.getCreateQueueRequets()).isEmpty();
        }
    }

    @Test
    void shouldAutomaticallyCreateQueueOnQueueDoesNotExistExceptionOnce() throws Exception {
        // given
        configuration.setAutoCreateQueue(true);
        sqsClientMock.setReceiveRequestHandler(request -> {
            throw QueueDoesNotExistException.builder().build();
        });
        sqsClientMock.setQueueUrl(null);
        try (var tested = createConsumer(1555)) {

            // when
            var polledMessagesCount = tested.poll();

            // then
            assertThat(polledMessagesCount).isZero();
            assertThat(receivedExchanges).isEmpty();
            // the request execution will be ignored once non-existing queue is detected
            assertThat(sqsClientMock.getReceiveRequests()).hasSizeLessThanOrEqualTo(156);
            assertThat(sqsClientMock.getQueueUrlRequests()).containsExactly(GetQueueUrlRequest.builder()
                    .queueName(configuration.getQueueName())
                    .build());
            assertThat(sqsClientMock.getCreateQueueRequets()).containsExactly(CreateQueueRequest.builder()
                    .queueName(configuration.getQueueName())
                    .attributes(emptyMap())
                    .build());
        }
    }

    @Test
    void shouldCreateMissingQueueWith30SecondsBackoffTimeOnRecentlyDeletedQueue() throws Exception {
        // given
        var createQueueRequestCount = new AtomicInteger();
        configuration.setAutoCreateQueue(true);
        sqsClientMock.setReceiveRequestHandler(request -> {
            throw QueueDoesNotExistException.builder().build();
        });
        sqsClientMock.setCreateQueueHandler(request -> {
            if (createQueueRequestCount.getAndIncrement() == 0) {
                throw QueueDeletedRecentlyException.builder().build();
            }
        });
        sqsClientMock.setQueueUrl(null);
        var elapsedTimeMillis = 450340L;
        doReturn(elapsedTimeMillis).when(clock).elapsed();

        try (var tested = createConsumer(1)) {
            // when
            var polledMessagesCount1 = tested.poll();

            // then queue creation should be scheduled
            assertThat(polledMessagesCount1).isZero();
            assertThat(createQueueRequestCount).hasValue(1);
            assertThat(sqsClientMock.getReceiveRequests()).containsExactly(expectedReceiveRequest(1));

            // when 29.999 seconds passed on next polling
            doReturn(elapsedTimeMillis + 29_999).when(clock).elapsed();
            var polledMessagesCount2 = tested.poll();

            // then queue creation should be ignored
            assertThat(polledMessagesCount2).isZero();
            assertThat(createQueueRequestCount).hasValue(1);
            assertThat(sqsClientMock.getReceiveRequests()).containsExactly(expectedReceiveRequest(1));

            // when 30 seconds passed on next polling
            doReturn(elapsedTimeMillis + 30_000).when(clock).elapsed();
            var polledMessagesCount3 = tested.poll();

            // then queue creation should be invoked
            assertThat(polledMessagesCount3).isZero();
            assertThat(createQueueRequestCount).hasValue(2);
            assertThat(sqsClientMock.getReceiveRequests()).containsExactly(expectedReceiveRequest(1));
        }
    }

    @Test
    void shouldIgnoreErrorsOnAtLeastOneSuccessfulReceiveRequest() throws Exception {
        // given
        sqsClientMock.addMessage(message("A"));
        sqsClientMock.addMessage(message("B"));
        sqsClientMock.addMessage(message("C"));
        sqsClientMock.setReceiveRequestHandler(request -> {
            if (request.maxNumberOfMessages() != null && request.maxNumberOfMessages().intValue() == 10) {
                throw SqsException.builder().build();
            }
        });

        try (var tested = createConsumer(25)) {

            // when
            var polledMessagesCount = tested.poll();

            // then
            assertThat(polledMessagesCount).isEqualTo(3);
            assertThat(receiveMessageBodies()).containsExactly("A", "B", "C");
            assertThat(sqsClientMock.getReceiveRequests()).containsExactlyInAnyOrder(
                    expectedReceiveRequest(10),
                    expectedReceiveRequest(10),
                    expectedReceiveRequest(5));
            assertThat(sqsClientMock.getQueues()).isEmpty();
        }
    }

    @Test
    void shouldThrowIOExceptionWhenAllReceiveRequestFails() throws Exception {
        // given
        sqsClientMock.addMessage(message("A"));
        sqsClientMock.setReceiveRequestHandler(request -> {
            throw SqsException.builder().build();
        });

        try (var tested = createConsumer(25)) {

            // when
            var caughtException = catchException(tested::poll);

            // then
            assertThat(caughtException)
                    .isInstanceOf(IOException.class)
                    .hasMessage(
                            "Error while polling - all 3 requests resulted in an error, please check the logs for more details");
            assertThat(receivedExchanges).isEmpty();
            assertThat(sqsClientMock.getReceiveRequests()).containsExactlyInAnyOrder(
                    expectedReceiveRequest(10),
                    expectedReceiveRequest(10),
                    expectedReceiveRequest(5));
            assertThat(sqsClientMock.getQueues()).isEmpty();
        }
    }

    @Test
    void shouldRethrowSqsExceptionOnErrorWithSingleRequest() throws Exception {
        // given
        final var exception = SqsException.builder().build();
        sqsClientMock.addMessage(message("A"));
        sqsClientMock.setReceiveRequestHandler(request -> {
            throw exception;
        });

        try (var tested = createConsumer(10)) {

            // when
            var caughtException = catchException(tested::poll);

            // then
            assertThat(caughtException).isEqualTo(exception);
            assertThat(receivedExchanges).isEmpty();
            assertThat(sqsClientMock.getReceiveRequests()).containsExactlyInAnyOrder(expectedReceiveRequest(10));
            assertThat(sqsClientMock.getQueues()).isEmpty();
        }
    }

    @SuppressWarnings("resource")
    private Sqs2Consumer createConsumer(int maxNumberOfMessages) throws Exception {
        var component = new Sqs2Component(context());
        component.setConfiguration(configuration);

        var endpoint = (Sqs2Endpoint) component.createEndpoint("aws2-sqs://%s?maxMessagesPerPoll=%s"
                .formatted(configuration.getQueueName(), maxNumberOfMessages));
        endpoint.setClient(sqsClientMock);
        endpoint.setClock(clock);

        var consumer = new Sqs2Consumer(endpoint, receivedExchanges::add);
        consumer.setStartScheduler(false);
        consumer.start();
        return consumer;
    }

    private Sqs2Consumer createConsumerWithProcessor(int maxNumberOfMessages, Processor processor) throws Exception {
        var component = new Sqs2Component(context());
        component.setConfiguration(configuration);

        var endpoint = (Sqs2Endpoint) component.createEndpoint("aws2-sqs://%s?maxMessagesPerPoll=%s"
                .formatted(configuration.getQueueName(), maxNumberOfMessages));
        endpoint.setClient(sqsClientMock);
        endpoint.setClock(clock);

        var consumer = new Sqs2Consumer(endpoint, processor);
        consumer.setStartScheduler(false);
        consumer.start();
        return consumer;
    }

    private List<Object> receiveMessageBodies() {
        return receivedExchanges.stream().map(it -> it.getIn().getBody()).toList();
    }

    private ReceiveMessageRequest.Builder expectedReceiveRequestBuilder() {
        return ReceiveMessageRequest.builder()
                .queueUrl(configuration.getQueueUrl())
                .waitTimeSeconds(configuration.getWaitTimeSeconds())
                .visibilityTimeout(configuration.getVisibilityTimeout())
                .messageAttributeNames(List.of("foo", "bar", "bazz"))
                .messageSystemAttributeNames(List.of(SENT_TIMESTAMP, MESSAGE_GROUP_ID, SEQUENCE_NUMBER));
    }

    private ReceiveMessageRequest expectedReceiveRequest(int maxNumberOfMessages) {
        return expectedReceiveRequestBuilder()
                .maxNumberOfMessages(maxNumberOfMessages)
                .build();
    }

    private Message message(String body) {
        final var builder = Message.builder();
        if (generateSequenceNumber) {
            builder.attributes(Map.of(MessageSystemAttributeName.SEQUENCE_NUMBER,
                    "%05d".formatted(sequenceNumber.incrementAndGet())));
        }
        return builder
                .body(body)
                .build();
    }
}
