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

import java.util.stream.Stream;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that the serializer and deserializer classes that schemaRegistryVendor sets exist in the serde jars of the
 * vendors, which are test dependencies, so that a renamed class fails the build when a vendor version is updated.
 * Confluent is not checked here, as its serdes are only in the Confluent Maven repository.
 */
class KafkaSchemaRegistryVendorClassesTest {

    static Stream<Arguments> vendorFormats() {
        return Stream.of(
                Arguments.of("apicurio", "avro"),
                Arguments.of("apicurio", "json"),
                Arguments.of("apicurio", "protobuf"),
                Arguments.of("aws-glue", "avro"),
                Arguments.of("aws-glue", "json"),
                Arguments.of("aws-glue", "protobuf"),
                Arguments.of("azure", "avro"));
    }

    @ParameterizedTest
    @MethodSource("vendorFormats")
    void testSerdeClassesExist(String vendor, String format) throws Exception {
        Class<?> serializer = Class.forName(KafkaSchemaRegistry.serializer(vendor, format));
        assertTrue(Serializer.class.isAssignableFrom(serializer), serializer + " is not a Kafka Serializer");

        Class<?> deserializer = Class.forName(KafkaSchemaRegistry.deserializer(vendor, format));
        assertTrue(Deserializer.class.isAssignableFrom(deserializer), deserializer + " is not a Kafka Deserializer");
    }
}
