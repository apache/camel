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

public final class ServiceBusConstants {
    private static final String HEADER_PREFIX = "CamelAzureServiceBus";
    // common headers, set by consumer and evaluated by producer

    // headers set by the consumer only
    public static final String APPLICATION_PROPERTIES = HEADER_PREFIX + "ApplicationProperties";
    public static final String CONTENT_TYPE = HEADER_PREFIX + "ContentType";
    public static final String CORRELATION_ID = HEADER_PREFIX + "CorrelationId";
    public static final String DEAD_LETTER_ERROR_DESCRIPTION = HEADER_PREFIX + "DeadLetterErrorDescription";
    public static final String DEAD_LETTER_REASON = HEADER_PREFIX + "DeadLetterReason";
    public static final String DEAD_LETTER_SOURCE = HEADER_PREFIX + "DeadLetterSource";
    public static final String DELIVERY_COUNT = HEADER_PREFIX + "DeliveryCount";
    public static final String ENQUEUED_SEQUENCE_NUMBER = HEADER_PREFIX + "EnqueuedSequenceNumber";
    public static final String ENQUEUED_TIME = HEADER_PREFIX + "EnqueuedTime";
    public static final String EXPIRES_AT = HEADER_PREFIX + "ExpiresAt";
    public static final String LOCK_TOKEN = HEADER_PREFIX + "LockToken";
    public static final String LOCKED_UNTIL = HEADER_PREFIX + "LockedUntil";
    public static final String MESSAGE_ID = HEADER_PREFIX + "MessageId";
    public static final String PARTITION_KEY = HEADER_PREFIX + "PartitionKey";
    public static final String RAW_AMQP_MESSAGE = HEADER_PREFIX + "RawAmqpMessage";
    public static final String REPLY_TO = HEADER_PREFIX + "ReplyTo";
    public static final String REPLY_TO_SESSION_ID = HEADER_PREFIX + "ReplyToSessionId";
    public static final String SEQUENCE_NUMBER = HEADER_PREFIX + "SequenceNumber";
    public static final String SESSION_ID = HEADER_PREFIX + "SessionId";
    public static final String SUBJECT = HEADER_PREFIX + "Subject";
    public static final String TIME_TO_LIVE = HEADER_PREFIX + "TimeToLive";
    public static final String TO = HEADER_PREFIX + "To";

    // headers set by consumer and evaluated by producer
    public static final String SCHEDULED_ENQUEUE_TIME = HEADER_PREFIX + "ScheduledEnqueueTime";

    // headers evaluated by the producer
    public static final String SERVICE_BUS_TRANSACTION_CONTEXT = HEADER_PREFIX + "ServiceBusTransactionContext";
    public static final String PRODUCER_OPERATION = HEADER_PREFIX + "ProducerOperation";

    private ServiceBusConstants() {
    }
}
