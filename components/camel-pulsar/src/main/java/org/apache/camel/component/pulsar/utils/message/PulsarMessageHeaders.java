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
package org.apache.camel.component.pulsar.utils.message;

import org.apache.camel.spi.Metadata;

public interface PulsarMessageHeaders {

    @Metadata(label = "consumer", description = "The properties attached to the message.", javaType = "Map<String, String>")
    String PROPERTIES = "properties";
    @Metadata(label = "consumer", description = "The producer name who produced the message.", javaType = "String")
    String PRODUCER_NAME = "producer_name";
    @Metadata(label = "consumer", description = "The sequence id associated with the message.", javaType = "long")
    String SEQUENCE_ID = "sequence_id";
    @Metadata(label = "consumer", description = "The publish time of the message.", javaType = "long")
    String PUBLISH_TIME = "publish_time";
    @Metadata(label = "consumer", description = "The unique message ID associated with the message.",
              javaType = "org.apache.pulsar.client.api.MessageId")
    String MESSAGE_ID = "message_id";
    @Metadata(label = "consumer", description = "The event time associated with the message.", javaType = "long")
    String EVENT_TIME = "event_time";
    @Metadata(label = "consumer", description = "The key of the message.", javaType = "String")
    String KEY = "key";
    @Metadata(label = "consumer", description = "The bytes in key.", javaType = "byte[]")
    String KEY_BYTES = "key_bytes";
    @Metadata(label = "consumer", description = "The topic the message was published to.", javaType = "String")
    String TOPIC_NAME = "topic_name";
    @Metadata(label = "consumer", description = "The message receipt.",
              javaType = "org.apache.camel.component.pulsar.PulsarMessageReceipt")
    String MESSAGE_RECEIPT = "message_receipt";
    @Metadata(label = "producer", description = "The key of the message for routing policy.", javaType = "String")
    String KEY_OUT = "CamelPulsarProducerMessageKey";
    @Metadata(label = "producer", description = "The properties of the message to add.", javaType = "Map<String, String>")
    String PROPERTIES_OUT = "CamelPulsarProducerMessageProperties";
    @Metadata(label = "producer", description = "The event time of the message message.", javaType = "Long")
    String EVENT_TIME_OUT = "CamelPulsarProducerMessageEventTime";
    @Metadata(label = "producer", description = "Deliver the message only at or after the specified absolute timestamp."
                                                + " The timestamp is milliseconds and based on UTC (eg: System.currentTimeMillis)"
                                                + " Note: messages are only delivered with delay when a consumer is consuming through a Shared subscription."
                                                + " With other subscription types, the messages will still be delivered immediately.",
              javaType = "Long")
    String DELIVER_AT_OUT = "CamelPulsarProducerMessageDeliverAt";
    @Metadata(label = "consumer", description = "The message redelivery count, redelivery count maintain in pulsar broker.",
              javaType = "int")
    String PULSAR_REDELIVERY_COUNT = "CamelPulsarRedeliveryCount";
}
