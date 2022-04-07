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
package org.apache.camel.component.vertx.kafka;

import org.apache.camel.spi.Metadata;

public final class VertxKafkaConstants {
    private static final String HEADER_PREFIX = "CamelVertxKafka";
    // common headers, set by the consumer and evaluated by the producer
    @Metadata(description = "*Producer:* Explicitly specify the partition identifier, for example partition `0`. " +
                            "This will trigger the component to produce all the massages to the specified partition.\n" +
                            "*Consumer:* The partition identifier where the message were consumed from.",
              javaType = "Integer")
    public static final String PARTITION_ID = HEADER_PREFIX + "PartitionId";
    @Metadata(description = "*Producer:* Explicitly specify the message key, if partition ID is not specified, " +
                            "this will trigger the messages to go into the same partition.\n" +
                            "*Consumer:* The message key.",
              javaType = "String")
    public static final String MESSAGE_KEY = HEADER_PREFIX + "MessageKey";
    @Metadata(description = "*Producer:* Explicitly specify the topic to where produce the messages, this will be *preserved* in case of header aggregation.\n"
                            +
                            "*Consumer:* The topic from where the message originated.",
              javaType = "String")
    public static final String TOPIC = HEADER_PREFIX + "Topic";
    // headers set by the producer only
    @Metadata(label = "producer", description = "Produced record metadata.", javaType = "List<RecordMetadata>")
    public static final String RECORD_METADATA = HEADER_PREFIX + "RecordMetadata";
    // headers set by the consumer only
    @Metadata(label = "consumer", description = "The offset of the message in Kafka topic.", javaType = "Long")
    public static final String OFFSET = HEADER_PREFIX + "Offset";
    @Metadata(label = "consumer", description = "The record Kafka headers.", javaType = "List<KafkaHeader>")
    public static final String HEADERS = HEADER_PREFIX + "Headers";
    @Metadata(label = "consumer", description = "The timestamp of this record.", javaType = "Long")
    public static final String TIMESTAMP = HEADER_PREFIX + "Timestamp";
    @Metadata(label = "producer", description = "The ProducerRecord also has an associated timestamp. " +
                                                "If the user did provide a timestamp, the producer will stamp the  record with the provided timestamp and the header is not preserved.",
              javaType = "Long")
    public static final String OVERRIDE_TIMESTAMP = HEADER_PREFIX + "OverrideTimestamp";
    public static final String MANUAL_COMMIT = HEADER_PREFIX + "ManualCommit";
    // headers evaluated by the producer only
    @Metadata(label = "producer", description = "Explicitly specify the topic to where produce the messages," +
                                                " this will *not be preserved* in case of header aggregation and it will take *precedence* over `CamelVertxKafkaTopic`.",
              javaType = "String")
    public static final String OVERRIDE_TOPIC = HEADER_PREFIX + "OverrideTopic";

    private VertxKafkaConstants() {
    }
}
