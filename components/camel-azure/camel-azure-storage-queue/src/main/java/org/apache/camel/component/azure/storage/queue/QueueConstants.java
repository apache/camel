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
package org.apache.camel.component.azure.storage.queue;

import org.apache.camel.spi.Metadata;

public final class QueueConstants {
    private static final String HEADER_PREFIX = "CamelAzureStorageQueue";
    // header names
    @Metadata(description = "The HTTP headers of the response of the queue service, as a com.azure.core.http.HttpHeaders."
                            + " The producer sets it for each operation except listQueues, receiveMessages and peekMessages.",
              javaType = "com.azure.core.http.HttpHeaders")
    public static final String RAW_HTTP_HEADERS = HEADER_PREFIX + "RawHttpHeaders";
    @Metadata(label = "producer",
              description = "The metadata of the queue that createQueue creates, or that sendMessage creates when createQueue"
                            + " is true. sendMessage does not change the metadata of a queue that already exists.",
              javaType = "Map<String,String>")
    public static final String METADATA = HEADER_PREFIX + "Metadata";
    @Metadata(description = "The ID of the message. The consumer sets it for each message it receives, and sendMessage for"
                            + " the message it sends. It replaces the messageId option for deleteMessage and updateMessage.",
              javaType = "String")
    public static final String MESSAGE_ID = HEADER_PREFIX + "MessageId";
    @Metadata(description = "The time the message was added to the queue. The consumer and sendMessage set it.",
              javaType = "java.time.OffsetDateTime")
    public static final String INSERTION_TIME = HEADER_PREFIX + "InsertionTime";
    @Metadata(description = "The time the message expires and the queue service deletes it. The consumer and sendMessage set"
                            + " it.",
              javaType = "java.time.OffsetDateTime")
    public static final String EXPIRATION_TIME = HEADER_PREFIX + "ExpirationTime";
    @Metadata(label = "producer",
              description = "The pop receipt of the message, which deleteMessage and updateMessage need, and which is no longer"
                            + " valid once the message is received again or updated. The consumer, sendMessage and"
                            + " updateMessage set it to the pop receipt they get. It replaces the popReceipt option for"
                            + " deleteMessage and updateMessage.",
              javaType = "String")
    public static final String POP_RECEIPT = HEADER_PREFIX + "PopReceipt";
    @Metadata(description = "The time the message becomes visible in the queue again. The consumer, sendMessage and"
                            + " updateMessage set it.",
              javaType = "java.time.OffsetDateTime")
    public static final String TIME_NEXT_VISIBLE = HEADER_PREFIX + "TimeNextVisible";
    @Metadata(description = "The number of times the message has been received from the queue, this time included. The"
                            + " consumer sets it.",
              javaType = "long")
    public static final String DEQUEUE_COUNT = HEADER_PREFIX + "DequeueCount";
    @Metadata(label = "consumer",
              description = "The name of the queue the message was received from. It is not the CamelAzureStorageQueueName"
                            + " header, which overrides the queue of a producer.",
              javaType = "String")
    public static final String CONSUMED_QUEUE_NAME = HEADER_PREFIX + "ConsumedQueueName";
    // headers to be retrieved
    @Metadata(label = "producer", description = "The operation that the producer performs. It replaces the operation option.",
              javaType = "org.apache.camel.component.azure.storage.queue.QueueOperationDefinition")
    public static final String QUEUE_OPERATION = HEADER_PREFIX + "Operation";
    @Metadata(label = "producer", description = "The name of the queue for the producer. It replaces the queueName option.",
              javaType = "String")
    public static final String QUEUE_NAME = HEADER_PREFIX + "Name";
    @Metadata(label = "producer",
              description = "The options of listQueues: the prefix of the names of the queues to list, whether to include the"
                            + " metadata of each queue, and the number of queues per page. If not set, listQueues lists all"
                            + " queues without their metadata.",
              javaType = "com.azure.storage.queue.models.QueuesSegmentOptions")
    public static final String QUEUES_SEGMENT_OPTIONS = HEADER_PREFIX + "SegmentOptions";
    @Metadata(label = "producer",
              description = "The time within which each request of the producer to the queue service must complete, or it"
                            + " fails with an exception. It replaces the timeout option.",
              javaType = "java.time.Duration")
    public static final String TIMEOUT = HEADER_PREFIX + "Timeout";
    @Metadata(label = "producer",
              description = "The maximum number of messages that receiveMessages or peekMessages returns, from 1 to 32. It"
                            + " replaces the maxMessages option.",
              javaType = "Integer")
    public static final String MAX_MESSAGES = HEADER_PREFIX + "MaxMessages";
    @Metadata(label = "producer",
              description = "How long the message stays invisible in the queue, at most 7 days: the message that sendMessage"
                            + " sends, each message that receiveMessages receives, or the message that updateMessage updates,"
                            + " for which it is required. It replaces the visibilityTimeout option.",
              javaType = "java.time.Duration")
    public static final String VISIBILITY_TIMEOUT = HEADER_PREFIX + "VisibilityTimeout";
    @Metadata(label = "producer",
              description = "How long the message that sendMessage sends stays in the queue, where PT-1S keeps it until it is"
                            + " deleted. It replaces the timeToLive option.",
              javaType = "java.time.Duration")
    public static final String TIME_TO_LIVE = HEADER_PREFIX + "TimeToLive";
    @Metadata(label = "producer",
              description = "Whether sendMessage first creates the queue if it does not exist. It replaces the createQueue"
                            + " option.",
              javaType = "boolean")
    public static final String CREATE_QUEUE = HEADER_PREFIX + "CreateQueue";

    private QueueConstants() {
    }
}
