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
package org.apache.camel.component.kafka;

import org.apache.camel.spi.Metadata;

public final class KafkaConstants {

    @Metadata(label = "producer", description = "Explicitly specify the partition", javaType = "Integer")
    public static final String PARTITION_KEY = "kafka.PARTITION_KEY";
    @Metadata(label = "consumer", description = "The partition where the message was stored", javaType = "Integer")
    public static final String PARTITION = "kafka.PARTITION";
    @Metadata(description = "*Producer:* The key of the message in order to ensure that all related message goes in the same partition. "
                            +
                            "*Consumer:* The key of the message if configured",
              javaType = "Object", required = true)
    public static final String KEY = "kafka.KEY";
    @Metadata(label = "consumer", description = "The topic from where the message originated", javaType = "String")
    public static final String TOPIC = "kafka.TOPIC";
    @Metadata(label = "producer",
              description = "The topic to which send the message (override and takes precedence), and the header is not preserved.",
              javaType = "String")
    public static final String OVERRIDE_TOPIC = "kafka.OVERRIDE_TOPIC";
    @Metadata(label = "consumer", description = "The offset of the message", javaType = "Long")
    public static final String OFFSET = "kafka.OFFSET";
    @Metadata(label = "consumer", description = "The record headers", javaType = "org.apache.kafka.common.header.Headers")
    public static final String HEADERS = "kafka.HEADERS";
    @Metadata(label = "consumer",
              description = "Whether or not it's the last record before commit (only available if `autoCommitEnable` endpoint parameter is `false`)",
              javaType = "Boolean")
    public static final String LAST_RECORD_BEFORE_COMMIT = "kafka.LAST_RECORD_BEFORE_COMMIT";
    @Metadata(label = "consumer", description = "Indicates the last record within the current poll request " +
                                                "(only available if `autoCommitEnable` endpoint parameter is `false` or `allowManualCommit` is `true`)",
              javaType = "Boolean")
    public static final String LAST_POLL_RECORD = "kafka.LAST_POLL_RECORD";
    @Metadata(label = "consumer", description = "The timestamp of the message", javaType = "Long")
    public static final String TIMESTAMP = "kafka.TIMESTAMP";
    @Metadata(label = "producer", description = "The ProducerRecord also has an associated timestamp. " +
                                                "If the user did provide a timestamp, the producer will stamp the  record with the provided timestamp and the header is not preserved.",
              javaType = "Long")
    public static final String OVERRIDE_TIMESTAMP = "kafka.OVERRIDE_TIMESTAMP";

    @Deprecated
    public static final String KAFKA_DEFAULT_ENCODER = "kafka.serializer.DefaultEncoder";
    @Deprecated
    public static final String KAFKA_STRING_ENCODER = "kafka.serializer.StringEncoder";

    public static final String KAFKA_DEFAULT_SERIALIZER = "org.apache.kafka.common.serialization.StringSerializer";
    public static final String KAFKA_DEFAULT_DESERIALIZER = "org.apache.kafka.common.serialization.StringDeserializer";
    public static final String PARTITIONER_RANGE_ASSIGNOR = "org.apache.kafka.clients.consumer.RangeAssignor";
    @Metadata(label = "producer",
              description = "The metadata (only configured if `recordMetadata` endpoint parameter is `true`)",
              javaType = "List<RecordMetadata>")
    public static final String KAFKA_RECORD_META = "kafka.RECORD_META";
    @Metadata(label = "consumer", description = "Can be used for forcing manual offset commit when using Kafka consumer.",
              javaType = "org.apache.camel.component.kafka.consumer.KafkaManualCommit")
    public static final String MANUAL_COMMIT = "CamelKafkaManualCommit";

    private KafkaConstants() {
        // Utility class
    }
}
