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

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class KafkaNetworkHealthHelperTest {

    @Test
    void consumerHasReadyNodesShouldFailClosedForNullConsumer() {
        assertFalse(KafkaNetworkHealthHelper.consumerHasReadyNodes(null));
    }

    @Test
    void consumerHasReadyNodesShouldFailClosedForNonKafkaConsumer() {
        Consumer<Object, Object> other = mock(Consumer.class);
        assertFalse(KafkaNetworkHealthHelper.consumerHasReadyNodes(other));
    }

    @Test
    void producerHasReadyNodesShouldFailClosedForNullProducer() {
        assertFalse(KafkaNetworkHealthHelper.producerHasReadyNodes(null));
    }

    @Test
    void producerHasReadyNodesShouldFailClosedForNonKafkaProducer() {
        Producer<Object, Object> other = mock(Producer.class);
        assertFalse(KafkaNetworkHealthHelper.producerHasReadyNodes(other));
    }
}
