/**
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

public final class KafkaConstants {

    public static final String PARTITION_KEY = "kafka.PARTITION_KEY";
    public static final String PARTITION = "kafka.EXCHANGE_NAME";
    public static final String KEY = "kafka.CONTENT_TYPE";
    public static final String TOPIC = "kafka.TOPIC";
    public static final String OFFSET = "kafka.OFFSET";

    public static final String KAFKA_DEFAULT_ENCODER = "kafka.serializer.DefaultEncoder";
    public static final String KAFKA_STRING_ENCODER = "kafka.serializer.StringEncoder";
    public static final String KAFKA_DEFAULT_SERIALIZER  = "org.apache.kafka.common.serialization.StringSerializer";
    public static final String KAFKA_DEFAULT_DESERIALIZER  = "org.apache.kafka.common.serialization.StringDeserializer";
    public static final String KAFKA_DEFAULT_PARTITIONER = "org.apache.kafka.clients.producer.internals.DefaultPartitioner";
    public static final String PARTITIONER_RANGE_ASSIGNOR = "org.apache.kafka.clients.consumer.RangeAssignor";

    private KafkaConstants() {
        // Utility class
    }
}
