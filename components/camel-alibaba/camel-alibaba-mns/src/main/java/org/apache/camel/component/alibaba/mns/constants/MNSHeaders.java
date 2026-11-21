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
package org.apache.camel.component.alibaba.mns.constants;

import org.apache.camel.spi.Metadata;

public final class MNSHeaders {

    @Metadata(label = "common", description = "The ID of the message.", javaType = "String")
    public static final String MESSAGE_ID = "CamelAlibabaMnsMessageId";

    @Metadata(label = "common", description = "The receipt handle of the message for deleteMessage. The consumer sets it for a"
                                              + " received message. If this header is not set, the producer uses an exchange"
                                              + " property of the same name.",
              javaType = "String")
    public static final String RECEIPT_HANDLE = "CamelAlibabaMnsReceiptHandle";

    @Metadata(label = "common", description = "The MD5 digest of the message body", javaType = "String")
    public static final String MESSAGE_BODY_MD5 = "CamelAlibabaMnsMessageBodyMd5";

    @Metadata(label = "producer", description = "The delay in seconds for sendMessage before the message becomes visible. The"
                                                + " value must be from 0 to 604800. If this header is not set, the"
                                                + " DelaySeconds of the queue applies.",
              javaType = "Integer")
    public static final String DELAY_SECONDS = "CamelAlibabaMnsDelaySeconds";

    @Metadata(label = "producer", description = "The priority of the message for sendMessage. The consumer sets it for a"
                                                + " received message.",
              javaType = "Integer")
    public static final String PRIORITY = "CamelAlibabaMnsPriority";

    @Metadata(label = "producer", description = "The tag of the message for publishMessage. Simple Message Queue accepts at"
                                                + " most 16 characters.",
              javaType = "String")
    public static final String MESSAGE_TAG = "CamelAlibabaMnsMessageTag";

    @Metadata(label = "producer", description = "The operation that the producer performs. This header replaces the operation"
                                                + " option, and an exchange property of the same name replaces both.",
              javaType = "String")
    public static final String OPERATION = "CamelAlibabaMnsOperation";

    @Metadata(label = "producer", description = "The name of the topic for publishMessage. This header replaces the topicName"
                                                + " option, and an exchange property of the same name replaces both.",
              javaType = "String")
    public static final String TOPIC_NAME = "CamelAlibabaMnsTopicName";

    @Metadata(label = "consumer", description = "Number of times the message has been dequeued", javaType = "Integer")
    public static final String DEQUEUE_COUNT = "CamelAlibabaMnsDequeueCount";

    @Metadata(label = "consumer", description = "Time when the message was enqueued", javaType = "java.util.Date")
    public static final String ENQUEUE_TIME = "CamelAlibabaMnsEnqueueTime";

    @Metadata(label = "consumer", description = "Next time the message becomes visible", javaType = "java.util.Date")
    public static final String NEXT_VISIBLE_TIME = "CamelAlibabaMnsNextVisibleTime";

    @Metadata(label = "consumer", description = "Time when the message was first dequeued", javaType = "java.util.Date")
    public static final String FIRST_DEQUEUE_TIME = "CamelAlibabaMnsFirstDequeueTime";

    private MNSHeaders() {
    }
}
