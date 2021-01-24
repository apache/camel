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

import java.util.Properties;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;

/**
 * Factory to create a new {@link KafkaConsumer} and {@link KafkaProducer} instances.
 */
public interface KafkaClientFactory {

    /**
     * Creates a new instance of the {@link KafkaProducer} class.
     * 
     * @param  kafkaProps The producer configs.
     * @return            an instance of Kafka producer.
     */
    KafkaProducer getProducer(Properties kafkaProps);

    /**
     * Creates a new instance of the {@link KafkaConsumer} class.
     * 
     * @param  kafkaProps The consumer configs.
     * @return            an instance of Kafka consumer.
     */
    KafkaConsumer getConsumer(Properties kafkaProps);
}
