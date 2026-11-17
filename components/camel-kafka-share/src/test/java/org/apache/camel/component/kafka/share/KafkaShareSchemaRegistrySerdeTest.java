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

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The value deserializer that the schemaRegistryVendor sets and that cannot be loaded fails the share consumer, with
 * the Maven artifact to add (the serde jars of the vendors are not on the test classpath).
 */
class KafkaShareSchemaRegistrySerdeTest {

    private static final String URI
            = "kafka-share:orders?brokers=localhost:39092&groupId=g&schemaRegistryVendor=apicurio&schemaFormat=protobuf";

    @Test
    void shareConsumerFailsToStartWithoutTheDeserializerAndNamesTheArtifact() throws Exception {
        try (CamelContext camel = new DefaultCamelContext()) {
            camel.start();
            KafkaShareEndpoint endpoint = camel.getEndpoint(URI, KafkaShareEndpoint.class);
            Consumer consumer = endpoint.createConsumer(exchange -> {
            });
            try {
                assertThatThrownBy(consumer::start)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageStartingWith("Cannot load the value deserializer "
                                                + "io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer "
                                                + "of schemaRegistryVendor apicurio and schemaFormat protobuf")
                        .hasMessageContaining(
                                "Add the Maven artifact io.apicurio:apicurio-registry-protobuf-serde-kafka to the classpath");
            } finally {
                consumer.stop();
            }
        }
    }

    @Test
    void customClientFactoryOnlyWarnsAboutAMissingDeserializer() throws Exception {
        // a custom client factory may create the share consumer without loading the class by its name
        try (CamelContext camel = new DefaultCamelContext()) {
            camel.start();
            KafkaShareEndpoint endpoint = camel.getEndpoint(URI, KafkaShareEndpoint.class);
            endpoint.setKafkaShareClientFactory(new DefaultKafkaShareClientFactory() {
            });
            Properties props = endpoint.getConfiguration().createShareConsumerProperties();

            assertThat(endpoint.getConfiguration().missingSchemaRegistrySerde(props,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG)).isNotNull();
            assertThatCode(() -> endpoint.checkSchemaRegistrySerde(props)).doesNotThrowAnyException();
        }
    }
}
