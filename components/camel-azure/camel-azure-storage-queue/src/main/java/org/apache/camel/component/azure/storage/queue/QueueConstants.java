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
    @Metadata(description = "Returns non-parsed httpHeaders that can be used by the user.", javaType = "HttpHeaders")
    public static final String RAW_HTTP_HEADERS = HEADER_PREFIX + "RawHttpHeaders";
    @Metadata(label = "producer", description = "(createQueue) Metadata to associate with the queue",
              javaType = "Map<String,String>")
    public static final String METADATA = HEADER_PREFIX + "Metadata";
    @Metadata(description = "The ID of the message.", javaType = "String")
    public static final String MESSAGE_ID = HEADER_PREFIX + "MessageId";
    @Metadata(description = "The time the Message was inserted into the Queue.", javaType = "OffsetDateTime")
    public static final String INSERTION_TIME = HEADER_PREFIX + "InsertionTime";
    @Metadata(description = "The time that the Message will expire and be automatically deleted.", javaType = "OffsetDateTime")
    public static final String EXPIRATION_TIME = HEADER_PREFIX + "ExpirationTime";
    @Metadata(label = "producer",
              description = "(deleteMessage, updateMessage) Unique identifier that must match for the message to be deleted or updated. "
                            +
                            "If deletion fails using this pop receipt then the message has been dequeued by another client.",
              javaType = "String")
    public static final String POP_RECEIPT = HEADER_PREFIX + "PopReceipt";
    @Metadata(description = "The time that the message will again become visible in the Queue.", javaType = "OffsetDateTime")
    public static final String TIME_NEXT_VISIBLE = HEADER_PREFIX + "TimeNextVisible";
    @Metadata(description = "The number of times the message has been dequeued.", javaType = "long")
    public static final String DEQUEUE_COUNT = HEADER_PREFIX + "DequeueCount";
    // headers to be retrieved
    @Metadata(label = "producer",
              description = "(All) Specify the producer operation to execute, please see the doc on this page related to producer operation.",
              javaType = "org.apache.camel.component.azure.storage.queue.QueueOperationDefinition")
    public static final String QUEUE_OPERATION = HEADER_PREFIX + "Operation";
    @Metadata(label = "producer", description = "(All) Override the queue name.", javaType = "String")
    public static final String QUEUE_NAME = HEADER_PREFIX + "Name";
    @Metadata(label = "producer", description = "(listQueues) Options for listing queues", javaType = "QueuesSegmentOptions")
    public static final String QUEUES_SEGMENT_OPTIONS = HEADER_PREFIX + "SegmentOptions";
    @Metadata(label = "producer",
              description = "(All) An optional timeout value beyond which a `RuntimeException` will be raised.",
              javaType = "Duration")
    public static final String TIMEOUT = HEADER_PREFIX + "Timeout";
    @Metadata(label = "producer",
              description = "(receiveMessages, peekMessages) Maximum number of messages to get, if there are less messages exist in the queue than requested all the messages will be returned. "
                            +
                            "If left empty only 1 message will be retrieved, the allowed range is 1 to 32 messages.",
              javaType = "Integer")
    public static final String MAX_MESSAGES = HEADER_PREFIX + "MaxMessages";
    @Metadata(label = "producer",
              description = "(sendMessage, receiveMessages, updateMessage) The timeout period for how long the message is invisible in the queue. "
                            +
                            "If unset the value will default to 0 and the message will be instantly visible. The timeout must be between 0 seconds and 7 days.",
              javaType = "Duration")
    public static final String VISIBILITY_TIMEOUT = HEADER_PREFIX + "VisibilityTimeout";
    @Metadata(label = "producer", description = "(sendMessage) How long the message will stay alive in the queue. " +
                                                "If unset the value will default to 7 days, if -1 is passed the message will not expire. The time to live must be -1 or any positive number.",
              javaType = "Duration")
    public static final String TIME_TO_LIVE = HEADER_PREFIX + "TimeToLive";
    @Metadata(label = "producer",
              description = "(sendMessage) When is set to `true`, the queue will be automatically created when sending messages to the queue.",
              javaType = "boolean")
    public static final String CREATE_QUEUE = HEADER_PREFIX + "CreateQueue";

    private QueueConstants() {
    }
}
