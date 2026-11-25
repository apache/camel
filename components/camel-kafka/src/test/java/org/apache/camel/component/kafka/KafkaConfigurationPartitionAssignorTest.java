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

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The partition assignment strategy is left to Kafka unless partitionAssignor is set.
 */
class KafkaConfigurationPartitionAssignorTest {

    @Test
    void defaultUsesTheKafkaDefault() {
        Properties props = new KafkaConfiguration().createConsumerProperties();

        // not set: Kafka uses its default, [RangeAssignor, CooperativeStickyAssignor]
        assertFalse(props.containsKey(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG));
    }

    @Test
    void configuredAssignorIsUsed() {
        KafkaConfiguration configuration = new KafkaConfiguration();
        configuration.setPartitionAssignor(CooperativeStickyAssignor.class.getName());

        Properties props = configuration.createConsumerProperties();

        assertEquals(CooperativeStickyAssignor.class.getName(),
                props.get(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG));
    }

    @Test
    void configuredAssignorIsNotUsedWithConsumerGroupProtocol() {
        KafkaConfiguration configuration = new KafkaConfiguration();
        configuration.setPartitionAssignor(CooperativeStickyAssignor.class.getName());
        configuration.setGroupProtocol("consumer");

        Properties props = configuration.createConsumerProperties();

        assertFalse(props.containsKey(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG));
    }
}
