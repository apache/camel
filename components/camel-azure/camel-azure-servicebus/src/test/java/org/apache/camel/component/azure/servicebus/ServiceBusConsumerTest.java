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
package org.apache.camel.component.azure.servicebus;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.azure.core.amqp.models.AmqpAnnotatedMessage;
import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.*;
import com.azure.messaging.servicebus.models.DeadLetterOptions;
import com.azure.messaging.servicebus.models.ServiceBusMessageState;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import com.azure.messaging.servicebus.models.SubQueue;
import org.apache.camel.*;
import org.apache.camel.component.azure.servicebus.client.ServiceBusClientFactory;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ServiceBusConsumerTest {
    private static final String MESSAGE_BODY = "bodyValue";
    private static final String MESSAGE_CONTENT_TYPE = "text/plain";
    private static final String MESSAGE_CORRELATION_ID = "correlationIdValue";
    private static final long MESSAGE_DELIVERY_COUNT_VALUE = 1;
    private static final long MESSAGE_ENQUEUED_SEQUENCE_NUMBER_VALUE = 2;
    private static final OffsetDateTime MESSAGE_ENQUEUED_TIME = OffsetDateTime.now();
    private static final OffsetDateTime MESSAGE_EXPIRES_AT = MESSAGE_ENQUEUED_TIME.plusMinutes(5);
    private static final String MESSAGE_LOCK_TOKEN = "lockTokenValue";
    private static final OffsetDateTime MESSAGE_LOCKED_UNTIL = MESSAGE_ENQUEUED_TIME.plusMinutes(1);
    private static final String MESSAGE_ID = "messageIdValue";
    private static final String MESSAGE_PARTITION_KEY = "partitionKeyValue";
    private static final String MESSAGE_REPLY_TO = "replyToValue";
    private static final String MESSAGE_REPLY_TO_SESSION_ID = "replyToSessionIdValue";
    private static final OffsetDateTime MESSAGE_SCHEDULED_ENQUEUE_TIME = OffsetDateTime.now();
    private static final long MESSAGE_SEQUENCE_NUMBER = 3;
    private static final String MESSAGE_SESSION_ID = "sessionIdValue";
    private static final ServiceBusMessageState MESSAGE_STATE = ServiceBusMessageState.ACTIVE;
    private static final String MESSAGE_SUBJECT = "subjectValue";
    private static final Duration MESSAGE_TIME_TO_LIVE = Duration.ofMinutes(5);
    private static final String MESSAGE_TO = "toValue";
    private static final String MESSAGE_DEAD_LETTER_ERROR_DESCRIPTION = "deadLetterErrorDescriptionValue";
    private static final String MESSAGE_DEAD_LETTER_REASON = "deadLetterReasonValue";
    private static final String MESSAGE_DEAD_LETTER_SOURCE = "deadLetterSourceValue";
    private static final String PROPAGATED_HEADER_KEY = "propagatedHeaderKey";
    private static final String PROPAGATED_HEADER_VALUE = "propagatedHeaderValue";

    private final ServiceBusConfiguration configuration = mock();
    private final ServiceBusClientFactory clientFactory = mock();
    private final ServiceBusProcessorClient client = mock();
    private final ServiceBusComponent component = mock();
    private final ServiceBusEndpoint endpoint = mock();
    private final ServiceBusReceivedMessageContext messageContext = mock();
    private final ServiceBusReceivedMessage message = mock();
    private final AmqpAnnotatedMessage rawAmqpAnnotatedMessage = mock();
    private final AsyncProcessor processor = mock();
    private final CamelContext context = mock();
    private final ExtendedCamelContext ecc = mock();
    private final ExchangeFactory ef = mock();
    private final HeaderFilterStrategy headerFilterStrategy = mock();
    private final ExceptionHandler exceptionHandler = mock();
    private final ArgumentCaptor<Consumer<ServiceBusReceivedMessageContext>> processMessageCaptor = ArgumentCaptor.captor();
    private final ArgumentCaptor<Consumer<ServiceBusErrorContext>> processErrorCaptor = ArgumentCaptor.captor();
    private final ArgumentCaptor<Exchange> exchangeCaptor = ArgumentCaptor.captor();
    private final ArgumentCaptor<AsyncCallback> asyncCallbackCaptor = ArgumentCaptor.captor();

    @BeforeEach
    void beforeEach() {
        when(endpoint.getCamelContext()).thenReturn(context);
        when(context.getCamelContextExtension()).thenReturn(ecc);
        when(ecc.getExchangeFactory()).thenReturn(ef);
        when(ef.newExchangeFactory(any())).thenReturn(ef);
        when(ef.create(any(Endpoint.class), anyBoolean()))
                .thenAnswer(invocationOnMock -> DefaultExchange.newFromEndpoint(invocationOnMock.getArgument(0)));
        when(endpoint.getComponent()).thenReturn(component);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getServiceBusClientFactory()).thenReturn(clientFactory);
        when(clientFactory.createServiceBusProcessorClient(any(), processMessageCaptor.capture(), processErrorCaptor.capture()))
                .thenReturn(client);
        when(clientFactory.createServiceBusSessionProcessorClient(any(), processMessageCaptor.capture(),
                processErrorCaptor.capture()))
                .thenReturn(client);
        when(processor.process(exchangeCaptor.capture(), asyncCallbackCaptor.capture())).thenReturn(true);
        when(configuration.getHeaderFilterStrategy()).thenReturn(headerFilterStrategy);
    }

    @Test
    void consumerSubmitsExchangeToProcessor() throws Exception {
        try (ServiceBusConsumer consumer = new ServiceBusConsumer(endpoint, processor)) {
            consumer.doStart();
            verify(client).start();
            verify(clientFactory).createServiceBusProcessorClient(any(), any(), any());

            when(messageContext.getMessage()).thenReturn(message);
            configureMockMessage();

            processMessageCaptor.getValue().accept(messageContext);

            verify(processor).process(any(Exchange.class), any(AsyncCallback.class));
            Exchange exchange = exchangeCaptor.getValue();
            assertThat(exchange).isNotNull();

            Message inMessage = exchange.getIn();
            assertThat(inMessage).isNotNull();
            assertThat(inMessage.getBody()).isInstanceOf(BinaryData.class);
            assertThat(inMessage.getBody(BinaryData.class).toString()).isEqualTo(MESSAGE_BODY);
            assertThat(inMessage.getHeaders()).isEqualTo(createExpectedMessageHeaders());
        }
    }

    @Test
    void consumerSubmitsExchangeToSessionProcessor() throws Exception {
        when(configuration.getSessionId()).thenReturn("sessionIdValue"); // Simulate session ID presence
        try (ServiceBusConsumer consumer = new ServiceBusConsumer(endpoint, processor)) {
            consumer.doStart();
            verify(client).start();
            verify(clientFactory).createServiceBusSessionProcessorClient(any(), any(), any());

            when(messageContext.getMessage()).thenReturn(message);
            configureMockMessage();

            processMessageCaptor.getValue().accept(messageContext);

            verify(processor).process(any(Exchange.class), any(AsyncCallback.class));
            Exchange exchange = exchangeCaptor.getValue();
            assertThat(exchange).isNotNull();

            Message inMessage = exchange.getIn();
            assertThat(inMessage).isNotNull();
            assertThat(inMessage.getBody()).isInstanceOf(BinaryData.class);
            assertThat(inMessage.getBody(BinaryData.class).toString()).isEqualTo(MESSAGE_BODY);
            assertThat(inMessage.getHeaders()).isEqualTo(createExpectedMessageHeaders());
        }
    }

    @Test
    void consumerSubmitsDeadLetterExchangeToProcessor() throws Exception {
        try (ServiceBusConsumer consumer = new ServiceBusConsumer(endpoint, processor)) {
            consumer.doStart();
            verify(client).start();
            verify(clientFactory).createServiceBusProcessorClient(any(), any(), any());

            when(messageContext.getMessage()).thenReturn(message);
            configureMockDeadLetterMessage();

            processMessageCaptor.getValue().accept(messageContext);

            verify(processor).process(any(Exchange.class), any(AsyncCallback.class));
            Exchange exchange = exchangeCaptor.getValue();
            assertThat(exchange).isNotNull();

            Message inMessage = exchange.getIn();
            assertThat(inMessage).isNotNull();
            assertThat(inMessage.getBody()).isInstanceOf(BinaryData.class);
            assertThat(inMessage.getBody(BinaryData.class).toString()).isEqualTo(MESSAGE_BODY);
            assertThat(inMessage.getHeaders()).isEqualTo(createExpectedDeadLetterMessageHeaders());
        }
    }

    @Test
    void consumerPropagatesApplicationPropertiesToMessageHeaders() throws Exception {
        try (ServiceBusConsumer consumer = new ServiceBusConsumer(endpoint, processor)) {
            consumer.doStart();
            verify(client).start();
            verify(clientFactory).createServiceBusProcessorClient(any(), any(), any());

            when(messageContext.getMessage()).thenReturn(message);
            configureMockMessage();
            message.getApplicationProperties().put(PROPAGATED_HEADER_KEY, PROPAGATED_HEADER_VALUE);
            when(headerFilterStrategy.applyFilterToExternalHeaders(anyString(), any(), any())).thenReturn(false);

            processMessageCaptor.getValue().accept(messageContext);

            verify(headerFilterStrategy, atLeastOnce()).applyFilterToExternalHeaders(anyString(), any(), any(Exchange.class));
            verifyNoMoreInteractions(headerFilterStrategy);

            verify(processor).process(any(Exchange.class), any(AsyncCallback.class));
            Exchange exchange = exchangeCaptor.getValue();
            assertThat(exchange).isNotNull();

            Message inMessage = exchange.getIn();
            assertThat(inMessage).isNotNull();
            assertThat(inMessage.getHeaders()).containsEntry(PROPAGATED_HEADER_KEY, PROPAGATED_HEADER_VALUE);
        }
    }

    @Test
    void consumerFiltersApplicationPropertiesFromMessageHeaders() throws Exception {
        try (ServiceBusConsumer consumer = new ServiceBusConsumer(endpoint, processor)) {
            consumer.doStart();
            verify(client).start();
            verify(clientFactory).createServiceBusProcessorClient(any(), any(), any());

            when(messageContext.getMessage()).thenReturn(message);
            configureMockMessage();
            message.getApplicationProperties().put(PROPAGATED_HEADER_KEY, PROPAGATED_HEADER_VALUE);
            when(headerFilterStrategy.applyFilterToExternalHeaders(anyString(), any(), any(Exchange.class))).thenReturn(true);

            processMessageCaptor.getValue().accept(messageContext);

            verify(headerFilterStrategy, atLeastOnce()).applyFilterToExternalHeaders(anyString(), any(), any(Exchange.class));
            verifyNoMoreInteractions(headerFilterStrategy);

            verify(processor).process(any(Exchange.class), any(AsyncCallback.class));
            Exchange exchange = exchangeCaptor.getValue();
            assertThat(exchange).isNotNull();

            Message inMessage = exchange.getIn();
            assertThat(inMessage).isNotNull();
            assertThat(inMessage.getHeaders()).doesNotContainKey(PROPAGATED_HEADER_KEY);
        }
    }

    @Test
    void consumerHandlesClientError() throws Exception {
        try (ServiceBusConsumer consumer = new ServiceBusConsumer(endpoint, processor)) {
            consumer.doStart();
            verify(client).start();
            verify(clientFactory).createServiceBusProcessorClient(any(), any(), any());

            ServiceBusErrorContext errorContext = mock();
            when(errorContext.getErrorSource()).thenReturn(ServiceBusErrorSource.UNKNOWN);
            when(errorContext.getException()).thenReturn(new Exception("Test exception"));

            processErrorCaptor.getValue().accept(errorContext);

            verifyNoInteractions(processor);
        }
    }

    @Test
    void synchronizationCompletesMessageOnSuccess() throws Exception {
        try (ServiceBusConsumer consumer = new ServiceBusConsumer(endpoint, processor)) {
            when(configuration.getServiceBusReceiveMode()).thenReturn(ServiceBusReceiveMode.PEEK_LOCK);
            consumer.doStart();
            verify(client).start();
            verify(clientFactory).createServiceBusProcessorClient(any(), any(), any());

            when(messageContext.getMessage()).thenReturn(message);

            processMessageCaptor.getValue().accept(messageContext);

            verify(messageContext).getMessage();

            Exchange exchange = exchangeCaptor.getValue();
            assertThat(exchange).isNotNull();

            Synchronization synchronization = exchange.getExchangeExtension().handoverCompletions().get(0);
            synchronization.onComplete(exchange);
            verify(messageContext).complete();

            verifyNoMoreInteractions(messageContext);
        }
    }

    @Test
    void synchronizationAbandonsMessageOnFailure() throws Exception {
        try (ServiceBusConsumer consumer = new ServiceBusConsumer(endpoint, processor)) {
            when(configuration.getServiceBusReceiveMode()).thenReturn(ServiceBusReceiveMode.PEEK_LOCK);
            consumer.doStart();
            verify(client).start();
            verify(clientFactory).createServiceBusProcessorClient(any(), any(), any());

            when(messageContext.getMessage()).thenReturn(message);
            processMessageCaptor.getValue().accept(messageContext);

            verify(messageContext).getMessage();

            verify(processor).process(any(Exchange.class), any(AsyncCallback.class));
            Exchange exchange = exchangeCaptor.getValue();
            assertThat(exchange).isNotNull();

            Synchronization synchronization = exchange.getExchangeExtension().handoverCompletions().get(0);
            synchronization.onFailure(exchange);
            verify(messageContext).abandon();

            verifyNoMoreInteractions(messageContext);
        }
    }

    @Test
    void synchronizationCallsExceptionHandlerOnFailure() throws Exception {
        try (ServiceBusConsumer consumer = new ServiceBusConsumer(endpoint, processor)) {
            consumer.setExceptionHandler(exceptionHandler);
            consumer.doStart();
            verify(client).start();
            verify(clientFactory).createServiceBusProcessorClient(any(), any(), any());

            when(messageContext.getMessage()).thenReturn(message);

            processMessageCaptor.getValue().accept(messageContext);

            verify(processor).process(any(Exchange.class), any(AsyncCallback.class));
            Exchange exchange = exchangeCaptor.getValue();
            assertThat(exchange).isNotNull();

            final Exception testException = new Exception("Test exception");
            exchange.setException(testException);
            Synchronization synchronization = exchange.getExchangeExtension().handoverCompletions().get(0);
            synchronization.onFailure(exchange);
            verify(exceptionHandler).handleException(anyString(), eq(exchange), eq(testException));
        }
    }

    @Test
    void synchronizationDeadLettersMessageOnFailureWhenSubQueueIsNull() throws Exception {
        synchronizationDeadLettersMessageWithOptionsWhenExceptionPresent(null);
    }

    @Test
    void synchronizationDeadLettersMessageOnFailureWhenSubQueueIsNone() throws Exception {
        synchronizationDeadLettersMessageWithOptionsWhenExceptionPresent(SubQueue.NONE);
    }

    private void synchronizationDeadLettersMessageWithOptionsWhenExceptionPresent(SubQueue subQueue) throws Exception {
        try (ServiceBusConsumer consumer = new ServiceBusConsumer(endpoint, processor)) {
            when(configuration.getServiceBusReceiveMode()).thenReturn(ServiceBusReceiveMode.PEEK_LOCK);
            when(configuration.isEnableDeadLettering()).thenReturn(true);
            when(configuration.getSubQueue()).thenReturn(subQueue);

            consumer.doStart();
            verify(client).start();
            verify(clientFactory).createServiceBusProcessorClient(any(), any(), any());

            when(messageContext.getMessage()).thenReturn(message);

            processMessageCaptor.getValue().accept(messageContext);

            verify(messageContext).getMessage();

            verify(processor).process(any(Exchange.class), any(AsyncCallback.class));
            Exchange exchange = exchangeCaptor.getValue();
            assertThat(exchange).isNotNull();
            exchange.setException(new Exception("Test exception"));

            Synchronization synchronization = exchange.getExchangeExtension().handoverCompletions().get(0);
            synchronization.onFailure(exchange);

            ArgumentCaptor<DeadLetterOptions> deadLetterOptionsCaptor = ArgumentCaptor.captor();
            verify(messageContext).deadLetter(deadLetterOptionsCaptor.capture());
            DeadLetterOptions deadLetterOptions = deadLetterOptionsCaptor.getValue();
            assertThat(deadLetterOptions.getDeadLetterReason()).contains(Exception.class.getName());
            assertThat(deadLetterOptions.getDeadLetterReason()).contains("Test exception");
            assertThat(deadLetterOptions.getDeadLetterErrorDescription()).contains(getClass().getName());

            verifyNoMoreInteractions(messageContext);
        }
    }

    @Test
    void synchronizationDeadLettersMessageWithoutOptionsWhenExceptionNotPresent() throws Exception {
        try (ServiceBusConsumer consumer = new ServiceBusConsumer(endpoint, processor)) {
            when(configuration.getServiceBusReceiveMode()).thenReturn(ServiceBusReceiveMode.PEEK_LOCK);
            when(configuration.isEnableDeadLettering()).thenReturn(true);
            consumer.doStart();
            verify(client).start();
            verify(clientFactory).createServiceBusProcessorClient(any(), any(), any());

            when(messageContext.getMessage()).thenReturn(message);

            processMessageCaptor.getValue().accept(messageContext);

            verify(messageContext).getMessage();

            verify(processor).process(any(Exchange.class), any(AsyncCallback.class));
            Exchange exchange = exchangeCaptor.getValue();
            assertThat(exchange).isNotNull();

            Synchronization synchronization = exchange.getExchangeExtension().handoverCompletions().get(0);
            synchronization.onFailure(exchange);
            verify(messageContext).deadLetter();

            verifyNoMoreInteractions(messageContext);
        }
    }

    @ParameterizedTest
    @EnumSource(value = SubQueue.class, names = "NONE", mode = EnumSource.Mode.EXCLUDE)
    void synchronizationAbandonsMessageOnFailureWhenProcessingDeadLetterQueue(SubQueue subQueue) throws Exception {
        try (ServiceBusConsumer consumer = new ServiceBusConsumer(endpoint, processor)) {
            when(configuration.getServiceBusReceiveMode()).thenReturn(ServiceBusReceiveMode.PEEK_LOCK);
            when(configuration.isEnableDeadLettering()).thenReturn(true);
            when(configuration.getSubQueue()).thenReturn(subQueue);
            consumer.doStart();
            verify(client).start();
            verify(clientFactory).createServiceBusProcessorClient(any(), any(), any());

            when(messageContext.getMessage()).thenReturn(message);

            processMessageCaptor.getValue().accept(messageContext);

            verify(messageContext).getMessage();

            verify(processor).process(any(Exchange.class), any(AsyncCallback.class));
            Exchange exchange = exchangeCaptor.getValue();
            assertThat(exchange).isNotNull();
            exchange.setException(new Exception("Test exception"));

            Synchronization synchronization = exchange.getExchangeExtension().handoverCompletions().get(0);
            synchronization.onFailure(exchange);
            verify(messageContext).abandon();

            verifyNoMoreInteractions(messageContext);
        }
    }

    @Test
    void synchronizationDoesNotCompleteMessageWhenReceiveModeIsReceiveAndDelete() throws Exception {
        try (ServiceBusConsumer consumer = new ServiceBusConsumer(endpoint, processor)) {
            when(configuration.getServiceBusReceiveMode()).thenReturn(ServiceBusReceiveMode.RECEIVE_AND_DELETE);
            consumer.doStart();
            verify(client).start();
            verify(clientFactory).createServiceBusProcessorClient(any(), any(), any());

            when(messageContext.getMessage()).thenReturn(message);

            processMessageCaptor.getValue().accept(messageContext);

            verify(messageContext).getMessage();

            Exchange exchange = exchangeCaptor.getValue();
            assertThat(exchange).isNotNull();

            Synchronization synchronization = exchange.getExchangeExtension().handoverCompletions().get(0);
            synchronization.onComplete(exchange);

            verifyNoMoreInteractions(messageContext);
        }
    }

    @Test
    void synchronizationDoesNotAbandonMessageWhenReceiveModeIsReceiveAndDelete() throws Exception {
        try (ServiceBusConsumer consumer = new ServiceBusConsumer(endpoint, processor)) {
            when(configuration.getServiceBusReceiveMode()).thenReturn(ServiceBusReceiveMode.RECEIVE_AND_DELETE);
            consumer.doStart();
            verify(client).start();
            verify(clientFactory).createServiceBusProcessorClient(any(), any(), any());

            when(messageContext.getMessage()).thenReturn(message);

            processMessageCaptor.getValue().accept(messageContext);

            verify(messageContext).getMessage();

            verify(processor).process(any(Exchange.class), any(AsyncCallback.class));
            Exchange exchange = exchangeCaptor.getValue();
            assertThat(exchange).isNotNull();

            Synchronization synchronization = exchange.getExchangeExtension().handoverCompletions().get(0);
            synchronization.onFailure(exchange);

            verifyNoMoreInteractions(messageContext);
        }
    }

    @Test
    void synchronizationDoesNotDeadLetterMessageWhenReceiveModeIsReceiveAndDelete() throws Exception {
        try (ServiceBusConsumer consumer = new ServiceBusConsumer(endpoint, processor)) {
            when(configuration.getServiceBusReceiveMode()).thenReturn(ServiceBusReceiveMode.RECEIVE_AND_DELETE);
            when(configuration.isEnableDeadLettering()).thenReturn(true);
            consumer.doStart();
            verify(client).start();
            verify(clientFactory).createServiceBusProcessorClient(any(), any(), any());

            when(messageContext.getMessage()).thenReturn(message);

            processMessageCaptor.getValue().accept(messageContext);

            verify(messageContext).getMessage();

            verify(processor).process(any(Exchange.class), any(AsyncCallback.class));
            Exchange exchange = exchangeCaptor.getValue();
            assertThat(exchange).isNotNull();

            Synchronization synchronization = exchange.getExchangeExtension().handoverCompletions().get(0);
            synchronization.onFailure(exchange);

            verifyNoMoreInteractions(messageContext);
        }
    }

    private void configureMockMessage() {
        when(message.getApplicationProperties()).thenReturn(new HashMap<>());
        when(message.getBody()).thenReturn(BinaryData.fromBytes(MESSAGE_BODY.getBytes()));
        when(message.getContentType()).thenReturn(MESSAGE_CONTENT_TYPE);
        when(message.getCorrelationId()).thenReturn(MESSAGE_CORRELATION_ID);
        when(message.getDeliveryCount()).thenReturn(MESSAGE_DELIVERY_COUNT_VALUE);
        when(message.getEnqueuedSequenceNumber()).thenReturn(MESSAGE_ENQUEUED_SEQUENCE_NUMBER_VALUE);
        when(message.getEnqueuedTime()).thenReturn(MESSAGE_ENQUEUED_TIME);
        when(message.getExpiresAt()).thenReturn(MESSAGE_EXPIRES_AT);
        when(message.getLockToken()).thenReturn(MESSAGE_LOCK_TOKEN);
        when(message.getLockedUntil()).thenReturn(MESSAGE_LOCKED_UNTIL);
        when(message.getMessageId()).thenReturn(MESSAGE_ID);
        when(message.getPartitionKey()).thenReturn(MESSAGE_PARTITION_KEY);
        when(message.getRawAmqpMessage()).thenReturn(rawAmqpAnnotatedMessage);
        when(message.getReplyTo()).thenReturn(MESSAGE_REPLY_TO);
        when(message.getReplyToSessionId()).thenReturn(MESSAGE_REPLY_TO_SESSION_ID);
        when(message.getScheduledEnqueueTime()).thenReturn(MESSAGE_SCHEDULED_ENQUEUE_TIME);
        when(message.getSequenceNumber()).thenReturn(MESSAGE_SEQUENCE_NUMBER);
        when(message.getSessionId()).thenReturn(MESSAGE_SESSION_ID);
        when(message.getState()).thenReturn(MESSAGE_STATE);
        when(message.getSubject()).thenReturn(MESSAGE_SUBJECT);
        when(message.getTimeToLive()).thenReturn(MESSAGE_TIME_TO_LIVE);
        when(message.getTo()).thenReturn(MESSAGE_TO);
    }

    private void configureMockDeadLetterMessage() {
        configureMockMessage();
        when(message.getDeadLetterErrorDescription()).thenReturn(MESSAGE_DEAD_LETTER_ERROR_DESCRIPTION);
        when(message.getDeadLetterReason()).thenReturn(MESSAGE_DEAD_LETTER_REASON);
        when(message.getDeadLetterSource()).thenReturn(MESSAGE_DEAD_LETTER_SOURCE);
    }

    private Map<String, Object> createExpectedMessageHeaders() {
        Map<String, Object> expectedMessageHeaders = new HashMap<>();
        expectedMessageHeaders.put(ServiceBusConstants.APPLICATION_PROPERTIES, message.getApplicationProperties());
        expectedMessageHeaders.put(ServiceBusConstants.CONTENT_TYPE, MESSAGE_CONTENT_TYPE);
        expectedMessageHeaders.put(ServiceBusConstants.MESSAGE_ID, MESSAGE_ID);
        expectedMessageHeaders.put(ServiceBusConstants.CORRELATION_ID, MESSAGE_CORRELATION_ID);
        expectedMessageHeaders.put(ServiceBusConstants.DEAD_LETTER_ERROR_DESCRIPTION, null);
        expectedMessageHeaders.put(ServiceBusConstants.DEAD_LETTER_REASON, null);
        expectedMessageHeaders.put(ServiceBusConstants.DEAD_LETTER_SOURCE, null);
        expectedMessageHeaders.put(ServiceBusConstants.DELIVERY_COUNT, MESSAGE_DELIVERY_COUNT_VALUE);
        expectedMessageHeaders.put(ServiceBusConstants.SCHEDULED_ENQUEUE_TIME, MESSAGE_SCHEDULED_ENQUEUE_TIME);
        expectedMessageHeaders.put(ServiceBusConstants.ENQUEUED_SEQUENCE_NUMBER, MESSAGE_ENQUEUED_SEQUENCE_NUMBER_VALUE);
        expectedMessageHeaders.put(ServiceBusConstants.ENQUEUED_TIME, MESSAGE_ENQUEUED_TIME);
        expectedMessageHeaders.put(ServiceBusConstants.EXPIRES_AT, MESSAGE_EXPIRES_AT);
        expectedMessageHeaders.put(ServiceBusConstants.LOCK_TOKEN, MESSAGE_LOCK_TOKEN);
        expectedMessageHeaders.put(ServiceBusConstants.LOCKED_UNTIL, MESSAGE_LOCKED_UNTIL);
        expectedMessageHeaders.put(ServiceBusConstants.PARTITION_KEY, MESSAGE_PARTITION_KEY);
        expectedMessageHeaders.put(ServiceBusConstants.RAW_AMQP_MESSAGE, rawAmqpAnnotatedMessage);
        expectedMessageHeaders.put(ServiceBusConstants.REPLY_TO, MESSAGE_REPLY_TO);
        expectedMessageHeaders.put(ServiceBusConstants.REPLY_TO_SESSION_ID, MESSAGE_REPLY_TO_SESSION_ID);
        expectedMessageHeaders.put(ServiceBusConstants.SEQUENCE_NUMBER, MESSAGE_SEQUENCE_NUMBER);
        expectedMessageHeaders.put(ServiceBusConstants.SESSION_ID, MESSAGE_SESSION_ID);
        expectedMessageHeaders.put(ServiceBusConstants.SUBJECT, MESSAGE_SUBJECT);
        expectedMessageHeaders.put(ServiceBusConstants.TIME_TO_LIVE, MESSAGE_TIME_TO_LIVE);
        expectedMessageHeaders.put(ServiceBusConstants.TO, MESSAGE_TO);
        return expectedMessageHeaders;
    }

    private Map<String, Object> createExpectedDeadLetterMessageHeaders() {
        Map<String, Object> expectedMessageHeaders = createExpectedMessageHeaders();
        expectedMessageHeaders.put(ServiceBusConstants.DEAD_LETTER_ERROR_DESCRIPTION, MESSAGE_DEAD_LETTER_ERROR_DESCRIPTION);
        expectedMessageHeaders.put(ServiceBusConstants.DEAD_LETTER_REASON, MESSAGE_DEAD_LETTER_REASON);
        expectedMessageHeaders.put(ServiceBusConstants.DEAD_LETTER_SOURCE, MESSAGE_DEAD_LETTER_SOURCE);
        return expectedMessageHeaders;
    }
}
