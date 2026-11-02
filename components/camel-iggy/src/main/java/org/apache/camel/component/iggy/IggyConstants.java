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
package org.apache.camel.component.iggy;

import org.apache.camel.spi.Metadata;

public final class IggyConstants {

    @Metadata(label = "consumer", description = "The unique identifier of the message.",
              javaType = "org.apache.iggy.message.MessageId")
    public static final String MESSAGE_ID = "CamelIggyMessageId";
    @Metadata(label = "consumer", description = "The offset of the message in the partition.",
              javaType = "java.math.BigInteger")
    public static final String MESSAGE_OFFSET = "CamelIggyMessageOffset";
    @Metadata(label = "consumer", description = "The timestamp of the message.",
              javaType = "java.math.BigInteger")
    public static final String MESSAGE_TIMESTAMP = "CamelIggyMessageTimestamp";
    @Metadata(label = "consumer", description = "The original timestamp of the message.",
              javaType = "java.math.BigInteger")
    public static final String MESSAGE_ORIGIN_TIMESTAMP = "CamelIggyMessageOriginTimestamp";
    @Metadata(label = "consumer", description = "The checksum of the message.", javaType = "java.math.BigInteger")
    public static final String MESSAGE_CHECKSUM = "CamelIggyMessageChecksum";
    @Metadata(label = "consumer", description = "The length of the payload of the message, in bytes.", javaType = "Long")
    public static final String MESSAGE_LENGTH = "CamelIggyMessageLength";
    @Metadata(label = "consumer", description = "The size of the message, in bytes.", javaType = "int")
    public static final String MESSAGE_SIZE = "CamelIggyMessageSize";
    @Metadata(label = "producer", description = "The name of the topic to send to, instead of the topic of the endpoint.",
              javaType = "String")
    public static final String TOPIC_OVERRIDE = "CamelIggyTopicOverride";
    @Metadata(label = "producer",
              description = "The name of the stream to send to, instead of the streamName of the endpoint.",
              javaType = "String")
    public static final String STREAM_OVERRIDE = "CamelIggyStreamOverride";

    private IggyConstants() {
        // Constants class
    }
}
