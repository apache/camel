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

import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.Producer;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The schemaRegistryVendor and schemaFormat options set up the value serde and the registry properties of the vendor,
 * and the options the user sets explicitly win.
 */
class KafkaSchemaRegistryVendorTest {

    private static final String URL = "http://registry:8081";
    private static final String STRING_SERIALIZER = "org.apache.kafka.common.serialization.StringSerializer";
    private static final String STRING_DESERIALIZER = "org.apache.kafka.common.serialization.StringDeserializer";

    @RegisterExtension
    protected static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    private final CamelContext context = contextExtension.getContext();

    @AfterEach
    void clear() {
        context.removeComponent("kafka");
    }

    static Stream<Arguments> vendorsAndFormats() {
        return Stream.of(
                Arguments.of("confluent", "avro",
                        "io.confluent.kafka.serializers.KafkaAvroSerializer",
                        "io.confluent.kafka.serializers.KafkaAvroDeserializer",
                        Map.of("schema.registry.url", URL),
                        Map.of("schema.registry.url", URL)),
                Arguments.of("confluent", "json",
                        "io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer",
                        "io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer",
                        Map.of("schema.registry.url", URL),
                        Map.of("schema.registry.url", URL)),
                Arguments.of("confluent", "protobuf",
                        "io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer",
                        "io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer",
                        Map.of("schema.registry.url", URL),
                        Map.of("schema.registry.url", URL)),
                Arguments.of("apicurio", "avro",
                        "io.apicurio.registry.serde.avro.AvroKafkaSerializer",
                        "io.apicurio.registry.serde.avro.AvroKafkaDeserializer",
                        Map.of("apicurio.registry.url", URL, "apicurio.registry.auto-register", true),
                        Map.of("apicurio.registry.url", URL)),
                Arguments.of("apicurio", "json",
                        "io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaSerializer",
                        "io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaDeserializer",
                        Map.of("apicurio.registry.url", URL, "apicurio.registry.auto-register", true),
                        Map.of("apicurio.registry.url", URL)),
                Arguments.of("apicurio", "protobuf",
                        "io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer",
                        "io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer",
                        Map.of("apicurio.registry.url", URL, "apicurio.registry.auto-register", true),
                        Map.of("apicurio.registry.url", URL)),
                Arguments.of("aws-glue", "avro",
                        "com.amazonaws.services.schemaregistry.serializers.GlueSchemaRegistryKafkaSerializer",
                        "com.amazonaws.services.schemaregistry.deserializers.GlueSchemaRegistryKafkaDeserializer",
                        Map.of("endpoint", URL, "dataFormat", "AVRO", "schemaAutoRegistrationEnabled", true),
                        Map.of("endpoint", URL, "dataFormat", "AVRO", "avroRecordType", "GENERIC_RECORD")),
                Arguments.of("aws-glue", "json",
                        "com.amazonaws.services.schemaregistry.serializers.GlueSchemaRegistryKafkaSerializer",
                        "com.amazonaws.services.schemaregistry.deserializers.GlueSchemaRegistryKafkaDeserializer",
                        Map.of("endpoint", URL, "dataFormat", "JSON", "schemaAutoRegistrationEnabled", true),
                        Map.of("endpoint", URL, "dataFormat", "JSON")),
                Arguments.of("aws-glue", "protobuf",
                        "com.amazonaws.services.schemaregistry.serializers.GlueSchemaRegistryKafkaSerializer",
                        "com.amazonaws.services.schemaregistry.deserializers.GlueSchemaRegistryKafkaDeserializer",
                        Map.of("endpoint", URL, "dataFormat", "PROTOBUF", "schemaAutoRegistrationEnabled", true),
                        Map.of("endpoint", URL, "dataFormat", "PROTOBUF")),
                Arguments.of("azure", "avro",
                        "com.microsoft.azure.schemaregistry.kafka.avro.KafkaAvroSerializer",
                        "com.microsoft.azure.schemaregistry.kafka.avro.KafkaAvroDeserializer",
                        Map.of("schema.registry.url", URL, "auto.register.schemas", true, "use.azure.credential", true),
                        Map.of("schema.registry.url", URL, "use.azure.credential", true)));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("vendorsAndFormats")
    void vendorAndFormatSetTheSerdeAndRegistryProperties(
            String vendor, String format, String serializer, String deserializer,
            Map<String, Object> producerProperties, Map<String, Object> consumerProperties) {
        KafkaConfiguration config = new KafkaConfiguration();
        config.setSchemaRegistryVendor(vendor);
        config.setSchemaFormat(format);
        config.setSchemaRegistryURL(URL);

        Properties producer = config.createProducerProperties();
        assertEquals(serializer, producer.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
        assertEquals(STRING_SERIALIZER, producer.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG));
        assertEquals(new TreeMap<>(producerProperties), registryProperties(producer));

        Properties consumer = config.createConsumerProperties();
        assertEquals(deserializer, consumer.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
        assertEquals(STRING_DESERIALIZER, consumer.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG));
        assertEquals(new TreeMap<>(consumerProperties), registryProperties(consumer));
    }

    @Test
    void producerConvertsTheBodyForTheSerializerOfTheVendor() {
        KafkaConfiguration config = new KafkaConfiguration();
        assertEquals(STRING_SERIALIZER, config.resolveValueSerializer());

        config.setSchemaRegistryVendor("confluent");
        // the body is not converted to a String for the vendor serializer
        assertEquals("io.confluent.kafka.serializers.KafkaAvroSerializer", config.resolveValueSerializer());

        config.setValueSerializer("com.example.MySerializer");
        assertEquals("com.example.MySerializer", config.resolveValueSerializer());
    }

    @Test
    void formatDefaultsToAvro() {
        KafkaConfiguration config = new KafkaConfiguration();
        config.setSchemaRegistryVendor("confluent");

        assertEquals("io.confluent.kafka.serializers.KafkaAvroSerializer",
                config.createProducerProperties().get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
        assertEquals("io.confluent.kafka.serializers.KafkaAvroDeserializer",
                config.createConsumerProperties().get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
    }

    @Test
    void withoutVendorTheUrlAndSpecificAvroReaderAreSetAsBefore() {
        KafkaConfiguration config = new KafkaConfiguration();
        config.setSchemaRegistryURL(URL);
        config.setSpecificAvroReader(true);
        config.setSchemaFormat("protobuf");

        Properties producer = config.createProducerProperties();
        assertEquals(STRING_SERIALIZER, producer.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
        assertEquals(Map.of("schema.registry.url", URL), registryProperties(producer));

        Properties consumer = config.createConsumerProperties();
        assertEquals(STRING_DESERIALIZER, consumer.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
        assertEquals(new TreeMap<>(Map.of("schema.registry.url", URL, "specific.avro.reader", true)),
                registryProperties(consumer));
    }

    @Test
    void specificAvroReaderIsSetUnderTheKeyOfTheVendor() {
        assertEquals(Boolean.TRUE, specificAvroReaderConsumer("confluent").get("specific.avro.reader"));
        assertEquals(Boolean.TRUE, specificAvroReaderConsumer("azure").get("specific.avro.reader"));
        Properties apicurio = specificAvroReaderConsumer("apicurio");
        assertEquals(Boolean.TRUE, apicurio.get("apicurio.registry.use-specific-avro-reader"));
        assertFalse(apicurio.containsKey("specific.avro.reader"));
        Properties glue = specificAvroReaderConsumer("aws-glue");
        assertEquals("SPECIFIC_RECORD", glue.get("avroRecordType"));
        assertFalse(glue.containsKey("specific.avro.reader"));
    }

    private static Properties specificAvroReaderConsumer(String vendor) {
        KafkaConfiguration config = new KafkaConfiguration();
        config.setSchemaRegistryVendor(vendor);
        config.setSpecificAvroReader(true);
        return config.createConsumerProperties();
    }

    @Test
    void explicitValueSerdeWins() {
        KafkaConfiguration config = new KafkaConfiguration();
        config.setSchemaRegistryVendor("apicurio");
        config.setValueSerializer("com.example.MySerializer");
        config.setValueDeserializer("com.example.MyDeserializer");

        Properties producer = config.createProducerProperties();
        assertEquals("com.example.MySerializer", producer.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
        // the registry properties of the vendor are still set
        assertEquals(Boolean.TRUE, producer.get("apicurio.registry.auto-register"));

        Properties consumer = config.createConsumerProperties();
        assertEquals("com.example.MyDeserializer", consumer.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
    }

    @Test
    void additionalPropertiesWin() {
        KafkaConfiguration config = new KafkaConfiguration();
        config.setSchemaRegistryVendor("apicurio");
        config.setSchemaRegistryURL(URL);
        config.getAdditionalProperties().put("apicurio.registry.url", "http://other:8080/apis/registry/v3");
        config.getAdditionalProperties().put("apicurio.registry.auto-register", "false");
        config.getAdditionalProperties().put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "com.example.MySerializer");
        config.getAdditionalProperties().put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "com.example.MyDeserializer");

        Properties producer = config.createProducerProperties();
        assertEquals("http://other:8080/apis/registry/v3", producer.get("apicurio.registry.url"));
        assertEquals("false", producer.get("apicurio.registry.auto-register"));
        assertEquals("com.example.MySerializer", producer.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));

        Properties consumer = config.createConsumerProperties();
        assertEquals("http://other:8080/apis/registry/v3", consumer.get("apicurio.registry.url"));
        assertEquals("com.example.MyDeserializer", consumer.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
    }

    @Test
    void azureCredentialAndSchemaGroupFromAdditionalProperties() {
        Object credential = new Object();
        context.getRegistry().bind("myCredential", credential);

        KafkaEndpoint endpoint = context.getEndpoint(
                "kafka:mytopic?schemaRegistryVendor=azure&schemaRegistryURL=my-namespace.servicebus.windows.net"
                                                     + "&additionalProperties.schema.group=my-group"
                                                     + "&additionalProperties.schema.registry.credential=#bean:myCredential",
                KafkaEndpoint.class);

        Properties producer = endpoint.getConfiguration().createProducerProperties();
        assertEquals("com.microsoft.azure.schemaregistry.kafka.avro.KafkaAvroSerializer",
                producer.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
        assertEquals("my-namespace.servicebus.windows.net", producer.get("schema.registry.url"));
        assertEquals("my-group", producer.get("schema.group"));
        assertSame(credential, producer.get("schema.registry.credential"));
    }

    @Test
    void endpointUriOptions() {
        KafkaEndpoint endpoint = context.getEndpoint(
                "kafka:mytopic?schemaRegistryVendor=confluent&schemaFormat=protobuf&schemaRegistryURL=" + URL,
                KafkaEndpoint.class);

        assertEquals("confluent", endpoint.getConfiguration().getSchemaRegistryVendor());
        assertEquals("protobuf", endpoint.getConfiguration().getSchemaFormat());
        assertEquals("io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer",
                endpoint.getConfiguration().createProducerProperties().get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
    }

    @Test
    void vendorOnTheComponentIsUsedByTheEndpoints() {
        KafkaComponent kafka = context.getComponent("kafka", KafkaComponent.class);
        kafka.getConfiguration().setSchemaRegistryVendor("aws-glue");
        kafka.getConfiguration().setSchemaFormat("json");

        KafkaEndpoint endpoint = context.getEndpoint("kafka:mytopic", KafkaEndpoint.class);
        Properties consumer = endpoint.getConfiguration().createConsumerProperties();
        assertEquals("com.amazonaws.services.schemaregistry.deserializers.GlueSchemaRegistryKafkaDeserializer",
                consumer.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
        assertEquals("JSON", consumer.get("dataFormat"));
    }

    @Test
    void azureSupportsOnlyAvro() {
        for (String format : new String[] { "json", "protobuf" }) {
            KafkaConfiguration config = new KafkaConfiguration();
            config.setSchemaRegistryVendor("azure");
            config.setSchemaFormat(format);

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, config::createProducerProperties);
            assertTrue(e.getMessage().contains("schemaRegistryVendor azure supports only schemaFormat avro, not " + format),
                    e.getMessage());
            assertThrows(IllegalArgumentException.class, config::createConsumerProperties);
        }
    }

    @Test
    void unknownVendorOrFormatFails() {
        KafkaConfiguration config = new KafkaConfiguration();
        config.setSchemaRegistryVendor("custom");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, config::createProducerProperties);
        assertTrue(e.getMessage().startsWith("Unknown schemaRegistryVendor: custom."), e.getMessage());

        KafkaConfiguration config2 = new KafkaConfiguration();
        config2.setSchemaRegistryVendor("confluent");
        config2.setSchemaFormat("xml");
        e = assertThrows(IllegalArgumentException.class, config2::createConsumerProperties);
        assertTrue(e.getMessage().startsWith("Unknown schemaFormat: xml."), e.getMessage());
    }

    @Test
    void vendorAndFormatAreCaseInsensitive() {
        KafkaConfiguration config = new KafkaConfiguration();
        config.setSchemaRegistryVendor("AWS-Glue");
        config.setSchemaFormat("Protobuf");

        Properties producer = config.createProducerProperties();
        assertEquals("PROTOBUF", producer.get("dataFormat"));
    }

    @Test
    void producerFailsToStartForAnUnsupportedCombination() throws Exception {
        KafkaEndpoint endpoint = context.getEndpoint(
                "kafka:mytopic?brokers=localhost:39092&schemaRegistryVendor=azure&schemaFormat=protobuf",
                KafkaEndpoint.class);
        Producer producer = endpoint.createProducer();
        try {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, producer::start);
            assertTrue(e.getMessage().contains("azure supports only schemaFormat avro"), e.getMessage());
        } finally {
            producer.stop();
        }
    }

    /**
     * The properties that are not Kafka client options with a fixed value in these tests: the registry properties.
     */
    private static Map<String, Object> registryProperties(Properties props) {
        Map<String, Object> answer = new TreeMap<>();
        for (String key : new String[] {
                "schema.registry.url", "specific.avro.reader", "auto.register.schemas", "use.azure.credential",
                "apicurio.registry.url", "apicurio.registry.auto-register", "apicurio.registry.use-specific-avro-reader",
                "endpoint", "dataFormat", "schemaAutoRegistrationEnabled", "avroRecordType" }) {
            if (props.containsKey(key)) {
                answer.put(key, props.get(key));
            }
        }
        return answer;
    }
}
