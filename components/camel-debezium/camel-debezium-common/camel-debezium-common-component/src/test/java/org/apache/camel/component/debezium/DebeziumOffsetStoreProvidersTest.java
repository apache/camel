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
package org.apache.camel.component.debezium;

import java.util.List;
import java.util.ServiceLoader;

import io.debezium.spi.storage.OffsetStoreProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With Kafka 4.x, the embedded engine creates the offset stores of Kafka through the OffsetStoreProvider of
 * debezium-storage-kafka, as the Kafka classes no longer have the no-argument constructor it used: camel-debezium
 * depends on it directly.
 */
class DebeziumOffsetStoreProvidersTest {

    @Test
    void theOffsetStoresOfKafkaHaveTheirProvider() {
        List<String> providers = ServiceLoader.load(OffsetStoreProvider.class).stream()
                .map(p -> p.type().getName()).toList();

        // offsetStorage=org.apache.kafka.connect.storage.KafkaOffsetBackingStore
        assertTrue(providers.contains("io.debezium.storage.kafka.offset.KafkaOffsetStoreProvider"), providers.toString());
        // the default, org.apache.kafka.connect.storage.FileOffsetBackingStore
        assertTrue(providers.contains("io.debezium.storage.kafka.offset.KafkaFileOffsetProvider"), providers.toString());
    }
}
