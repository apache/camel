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
package org.apache.camel.component.google.pubsub;

import org.apache.camel.spi.Metadata;

public final class GooglePubsubConstants {

    @Metadata(description = "The ID of the message, assigned by the server when the message is published.", javaType = "String")
    public static final String MESSAGE_ID = "CamelGooglePubsubMessageId";
    @Metadata(label = "consumer", description = "The ID used to acknowledge the received message.", javaType = "String")
    public static final String ACK_ID = "CamelGooglePubsubMsgAckId";
    @Metadata(label = "consumer", description = "The time at which the message was published",
              javaType = "com.google.protobuf.Timestamp")
    public static final String PUBLISH_TIME = "CamelGooglePubsubPublishTime";
    @Metadata(description = "The attributes of the message.", javaType = "Map<String, String>")
    public static final String ATTRIBUTES = "CamelGooglePubsubAttributes";
    @Metadata(label = "producer",
              description = "If non-empty, identifies related messages for which publish order should be\n" +
                            " respected.",
              javaType = "String")
    public static final String ORDERING_KEY = "CamelGooglePubsubOrderingKey";
    @Metadata(label = "consumer", description = "Can be used to manually acknowledge or negative-acknowledge a " +
                                                "message when ackMode=NONE.",
              javaType = "org.apache.camel.component.google.pubsub.consumer.GooglePubsubAcknowledge")
    public static final String GOOGLE_PUBSUB_ACKNOWLEDGE = "CamelGooglePubsubAcknowledge";
    public static final String RESERVED_GOOGLE_CLIENT_ATTRIBUTE_PREFIX = "goog";

    public enum AckMode {
        AUTO,
        NONE
    }

    private GooglePubsubConstants() {
        // not called
    }
}
