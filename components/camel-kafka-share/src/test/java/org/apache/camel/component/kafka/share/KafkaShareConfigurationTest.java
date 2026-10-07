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

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaShareConsumer;
import org.apache.kafka.common.config.ConfigException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaShareConfigurationTest {

    @Test
    void propertiesUseExplicitAcknowledgementAndNoConsumerGroupOption() {
        KafkaShareConfiguration configuration = configuration();
        configuration.setMaxPollRecords(10);
        configuration.setAcquireMode("record_limit");

        Properties props = configuration.createShareConsumerProperties();

        assertThat(props)
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "my-group")
                .containsEntry(ConsumerConfig.SHARE_ACKNOWLEDGEMENT_MODE_CONFIG, "explicit")
                .containsEntry(ConsumerConfig.SHARE_ACQUIRE_MODE_CONFIG, "record_limit")
                .containsEntry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10)
                .doesNotContainKeys(KafkaShareConfiguration.SHARE_GROUP_UNSUPPORTED_CONFIGS.toArray(new String[0]));
    }

    @Test
    void shareConsumerAcceptsTheProperties() {
        Properties props = configuration().createShareConsumerProperties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

        // creating the client validates the properties, without connecting to the broker
        new KafkaShareConsumer<>(props).close();
    }

    @ParameterizedTest
    @FieldSource("org.apache.camel.component.kafka.share.KafkaShareConfiguration#SHARE_GROUP_UNSUPPORTED_CONFIGS")
    void consumerGroupOptionIsRejectedByTheShareConsumerAndTheValidation(String option) {
        KafkaShareConfiguration configuration = configuration();
        configuration.getAdditionalProperties().put(option, "any");
        Properties props = configuration.createShareConsumerProperties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

        // the list of options must stay in line with the options kafka-clients rejects
        assertThatThrownBy(() -> new KafkaShareConsumer<>(props))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining(option);
        assertThatThrownBy(() -> KafkaShareConfiguration.validateShareConsumerProperties(props))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(option);
    }

    @Test
    void copyHasItsOwnAdditionalProperties() {
        KafkaShareConfiguration configuration = configuration();
        configuration.getAdditionalProperties().put("custom.option", "value");

        KafkaShareConfiguration copy = configuration.copy();
        copy.getAdditionalProperties().put("other.option", "value");

        assertThat(copy.getGroupId()).isEqualTo("my-group");
        assertThat(copy.getAdditionalProperties()).containsKeys("custom.option", "other.option");
        assertThat(configuration.getAdditionalProperties()).doesNotContainKey("other.option");
    }

    private static KafkaShareConfiguration configuration() {
        KafkaShareConfiguration configuration = new KafkaShareConfiguration();
        configuration.setTopic("orders");
        configuration.setGroupId("my-group");
        return configuration;
    }
}
