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

import java.util.Properties;

import org.apache.kafka.clients.consumer.ShareConsumer;

/**
 * Factory for the Kafka share consumers.
 */
public interface KafkaShareClientFactory {

    /**
     * Creates a new instance of the Kafka {@link ShareConsumer}.
     *
     * @param  kafkaProps The share consumer configs.
     * @return            an instance of Kafka share consumer.
     */
    ShareConsumer<Object, Object> getShareConsumer(Properties kafkaProps);

    /**
     * URL of the Kafka brokers to use. The format is host1:port1,host2:port2, and the list can be a subset of brokers
     * or a VIP pointing to a subset of brokers.
     * <p/>
     * This option is known as <tt>bootstrap.servers</tt> in the Kafka documentation.
     *
     * @param configuration the configuration
     */
    String getBrokers(KafkaShareConfiguration configuration);
}
