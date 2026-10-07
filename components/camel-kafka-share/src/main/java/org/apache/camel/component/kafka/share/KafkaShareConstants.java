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
package org.apache.camel.component.kafka.share;

import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.spi.Metadata;

public final class KafkaShareConstants {

    @Metadata(description = "The topic from where the message originated", javaType = "String", important = true)
    public static final String TOPIC = KafkaConstants.TOPIC;
    @Metadata(description = "The partition where the message was stored", javaType = "Integer", important = true)
    public static final String PARTITION = KafkaConstants.PARTITION;
    @Metadata(description = "The offset of the message", javaType = "Long", important = true)
    public static final String OFFSET = KafkaConstants.OFFSET;
    @Metadata(description = "The key of the message if configured", javaType = "Object", important = true)
    public static final String KEY = KafkaConstants.KEY;
    @Metadata(description = "The timestamp of the message", javaType = "Long")
    public static final String TIMESTAMP = KafkaConstants.TIMESTAMP;
    @Metadata(description = "The record headers", javaType = "org.apache.kafka.common.header.Headers")
    public static final String HEADERS = KafkaConstants.HEADERS;
    @Metadata(description = "How many times the record has been delivered, this delivery included. The header is not set"
                            + " when the broker does not count the deliveries.",
              javaType = "Short", important = true)
    public static final String DELIVERY_COUNT = "CamelKafkaShareDeliveryCount";
    @Metadata(description = "How to acknowledge the record (ACCEPT, RELEASE or REJECT), instead of deriving it from the"
                            + " outcome of the exchange. Set it in the route, for example to REJECT a record that was"
                            + " delivered too many times.",
              javaType = "String", enums = "ACCEPT,RELEASE,REJECT")
    public static final String ACKNOWLEDGE = "CamelKafkaShareAcknowledge";

    private KafkaShareConstants() {
        // Utility class
    }
}
