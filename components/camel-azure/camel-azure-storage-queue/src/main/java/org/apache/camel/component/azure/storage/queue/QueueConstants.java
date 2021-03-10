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

public final class QueueConstants {
    private static final String HEADER_PREFIX = "CamelAzureStorageQueue";
    // header names
    public static final String RAW_HTTP_HEADERS = HEADER_PREFIX + "RawHttpHeaders";
    public static final String METADATA = HEADER_PREFIX + "Metadata";
    public static final String MESSAGE_ID = HEADER_PREFIX + "MessageId";
    public static final String INSERTION_TIME = HEADER_PREFIX + "InsertionTime";
    public static final String EXPIRATION_TIME = HEADER_PREFIX + "ExpirationTime";
    public static final String POP_RECEIPT = HEADER_PREFIX + "PopReceipt";
    public static final String TIME_NEXT_VISIBLE = HEADER_PREFIX + "TimeNextVisible";
    public static final String DEQUEUE_COUNT = HEADER_PREFIX + "DequeueCount";
    // headers to be retrieved
    public static final String QUEUE_OPERATION = HEADER_PREFIX + "Operation";
    public static final String QUEUE_NAME = HEADER_PREFIX + "Name";
    public static final String QUEUES_SEGMENT_OPTIONS = HEADER_PREFIX + "SegmentOptions";
    public static final String TIMEOUT = HEADER_PREFIX + "Timeout";
    public static final String MAX_MESSAGES = HEADER_PREFIX + "MaxMessages";
    public static final String VISIBILITY_TIMEOUT = HEADER_PREFIX + "VisibilityTimeout";
    public static final String TIME_TO_LIVE = HEADER_PREFIX + "TimeToLive";
    public static final String CREATE_QUEUE = HEADER_PREFIX + "CreateQueue";

    private QueueConstants() {
    }
}
