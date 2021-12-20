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

import org.apache.camel.util.ObjectHelper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;

public class DefaultKafkaClientFactory implements KafkaClientFactory {

    @Override
    public Producer getProducer(Properties kafkaProps) {
        return new org.apache.kafka.clients.producer.KafkaProducer(kafkaProps);
    }

    @Override
    public Consumer getConsumer(Properties kafkaProps) {
        return new org.apache.kafka.clients.consumer.KafkaConsumer(kafkaProps);
    }

    @Override
    public String getBrokers(KafkaConfiguration configuration) {
        // broker urls is mandatory in this implementation
        String brokers = configuration.getBrokers();
        if (ObjectHelper.isEmpty(brokers)) {
            throw new IllegalArgumentException("URL to the Kafka brokers must be configured with the brokers option.");
        }
        return brokers;
    }
}
