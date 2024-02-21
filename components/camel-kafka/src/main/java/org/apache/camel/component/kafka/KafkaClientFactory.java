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

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;

/**
 * Factory to create a new Kafka {@link Consumer} and Kafka {@link Producer} instances.
 */
public interface KafkaClientFactory {

    /**
     * Creates a new instance of the Kafka {@link Producer} class.
     *
     * @param  kafkaProps The producer configs.
     * @return            an instance of Kafka producer.
     */
    Producer getProducer(Properties kafkaProps);

    /**
     * Creates a new instance of the Kafka {@link Consumer} class.
     *
     * @param  kafkaProps The consumer configs.
     * @return            an instance of Kafka consumer.
     */
    Consumer getConsumer(Properties kafkaProps);

    /**
     * URL of the Kafka brokers to use. The format is host1:port1,host2:port2, and the list can be a subset of brokers
     * or a VIP pointing to a subset of brokers.
     * <p/>
     * This option is known as <tt>bootstrap.servers</tt> in the Kafka documentation.
     *
     * @param configuration the configuration
     */
    String getBrokers(KafkaConfiguration configuration);
}
