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

import org.apache.camel.spi.Metadata;

public final class ServiceBusConstants {
    private static final String HEADER_PREFIX = "CamelAzureServiceBus";
    // common headers, set by consumer and evaluated by producer

    // headers set by the consumer only
    @Metadata(label = "common",
              description = "The application properties (also known as custom properties) on messages sent and received by the producer and consumer, respectively.",
              javaType = "Map<String, Object>")
    public static final String APPLICATION_PROPERTIES = HEADER_PREFIX + "ApplicationProperties";
    @Metadata(label = "consumer", description = "Gets the content type of the message.", javaType = "String")
    public static final String CONTENT_TYPE = HEADER_PREFIX + "ContentType";
    @Metadata(label = "consumer", description = "Gets the description for a message that has been dead-lettered.",
              javaType = "String")
    public static final String DEAD_LETTER_ERROR_DESCRIPTION = HEADER_PREFIX + "DeadLetterErrorDescription";
    @Metadata(label = "consumer", description = "Gets the reason a message was dead-lettered.", javaType = "String")
    public static final String DEAD_LETTER_REASON = HEADER_PREFIX + "DeadLetterReason";
    @Metadata(label = "consumer",
              description = "Gets the name of the queue or subscription that this message was enqueued on, before it was dead-lettered.",
              javaType = "String")
    public static final String DEAD_LETTER_SOURCE = HEADER_PREFIX + "DeadLetterSource";
    @Metadata(label = "consumer", description = "Gets the number of the times this message was delivered to clients.",
              javaType = "long")
    public static final String DELIVERY_COUNT = HEADER_PREFIX + "DeliveryCount";
    @Metadata(label = "consumer", description = "Gets the enqueued sequence number assigned to a message by Service Bus.",
              javaType = "long")
    public static final String ENQUEUED_SEQUENCE_NUMBER = HEADER_PREFIX + "EnqueuedSequenceNumber";
    @Metadata(label = "consumer", description = "Gets the datetime at which this message was enqueued in Azure Service Bus.",
              javaType = "OffsetDateTime")
    public static final String ENQUEUED_TIME = HEADER_PREFIX + "EnqueuedTime";
    @Metadata(label = "consumer", description = "Gets the datetime at which this message will expire.",
              javaType = "OffsetDateTime")
    public static final String EXPIRES_AT = HEADER_PREFIX + "ExpiresAt";
    @Metadata(label = "consumer", description = "Gets the lock token for the current message.", javaType = "String")
    public static final String LOCK_TOKEN = HEADER_PREFIX + "LockToken";
    @Metadata(label = "consumer", description = "Gets the datetime at which the lock of this message expires.",
              javaType = "OffsetDateTime")
    public static final String LOCKED_UNTIL = HEADER_PREFIX + "LockedUntil";
    @Metadata(label = "consumer", description = "Gets the identifier for the message.", javaType = "String")
    public static final String MESSAGE_ID = HEADER_PREFIX + "MessageId";
    @Metadata(label = "consumer", description = "Gets the partition key for sending a message to a partitioned entity.",
              javaType = "String")
    public static final String PARTITION_KEY = HEADER_PREFIX + "PartitionKey";
    @Metadata(label = "consumer", description = "The representation of message as defined by AMQP protocol.",
              javaType = "AmqpAnnotatedMessage")
    public static final String RAW_AMQP_MESSAGE = HEADER_PREFIX + "RawAmqpMessage";
    @Metadata(label = "consumer", description = "Gets the address of an entity to send replies to.", javaType = "String")
    public static final String REPLY_TO = HEADER_PREFIX + "ReplyTo";
    @Metadata(label = "consumer", description = "Gets or sets a session identifier augmenting the ReplyTo address.",
              javaType = "String")
    public static final String REPLY_TO_SESSION_ID = HEADER_PREFIX + "ReplyToSessionId";
    @Metadata(label = "consumer", description = "Gets the unique number assigned to a message by Service Bus.",
              javaType = "long")
    public static final String SEQUENCE_NUMBER = HEADER_PREFIX + "SequenceNumber";
    @Metadata(label = "consumer", description = "Gets the session id of the message.", javaType = "String")
    public static final String SESSION_ID = HEADER_PREFIX + "SessionId";
    @Metadata(label = "consumer", description = "Gets the subject for the message.", javaType = "String")
    public static final String SUBJECT = HEADER_PREFIX + "Subject";
    @Metadata(label = "consumer", description = "Gets the duration before this message expires.", javaType = "Duration")
    public static final String TIME_TO_LIVE = HEADER_PREFIX + "TimeToLive";
    @Metadata(label = "consumer", description = "Gets the \"to\" address.", javaType = "String")
    public static final String TO = HEADER_PREFIX + "To";

    // headers set by consumer and evaluated by producer
    @Metadata(description = "(producer)Overrides the OffsetDateTime at which the message should appear in the Service Bus queue or topic. "
                            +
                            "(consumer) Gets the scheduled enqueue time of this message.",
              javaType = "OffsetDateTime")
    public static final String SCHEDULED_ENQUEUE_TIME = HEADER_PREFIX + "ScheduledEnqueueTime";

    // headers evaluated by the producer
    @Metadata(label = "producer",
              description = "Overrides the transaction in service. This object just contains transaction id.",
              javaType = "ServiceBusTransactionContext")
    public static final String SERVICE_BUS_TRANSACTION_CONTEXT = HEADER_PREFIX + "ServiceBusTransactionContext";
    @Metadata(label = "producer", description = "Overrides the desired operation to be used in the producer.",
              javaType = "org.apache.camel.component.azure.servicebus.ServiceBusProducerOperationDefinition")
    public static final String PRODUCER_OPERATION = HEADER_PREFIX + "ProducerOperation";

    // headers evaluated by the producer and consumer
    @Metadata(label = "common", description = "Gets or Sets a correlation identifier.", javaType = "String")
    public static final String CORRELATION_ID = HEADER_PREFIX + "CorrelationId";

    private ServiceBusConstants() {
    }
}
