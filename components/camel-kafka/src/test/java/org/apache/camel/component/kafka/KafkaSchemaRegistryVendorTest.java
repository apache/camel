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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void producerConvertsTheBodyForTheSerializerOfTheVendor() throws Exception {
        Payload payload = new Payload("hello");

        // the body is not converted to a String for the vendor serializer
        assertSame(payload, recordValue("kafka:t?schemaRegistryVendor=confluent&schemaRegistryURL=" + URL, payload));
        assertSame(payload, batchRecordValue("kafka:t?schemaRegistryVendor=confluent", payload));
        assertEquals("Payload[name=hello]", recordValue("kafka:t", payload));
    }

    @Test
    void producerConvertsTheBodyForTheSerializerOfAdditionalProperties() throws Exception {
        Payload payload = new Payload("hello");

        // the serializer the Kafka producer gets, not the valueSerializer option, decides the conversion
        String avro = "kafka:t?schemaRegistryURL=" + URL
                      + "&additionalProperties.value.serializer=io.confluent.kafka.serializers.KafkaAvroSerializer";
        assertSame(payload, recordValue(avro, payload));
        assertSame(payload, batchRecordValue(avro, payload));

        String bytes
                = "kafka:t?additionalProperties.value.serializer=org.apache.kafka.common.serialization.ByteArraySerializer";
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), (byte[]) recordValue(bytes, "hello"));
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), (byte[]) batchRecordValue(bytes, "hello"));
    }

    private Object recordValue(String uri, Object body) throws Exception {
        KafkaProducer producer = startedProducer(uri);
        try {
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setBody(body);
            return producer.createRecord(exchange, exchange.getMessage()).value();
        } finally {
            producer.stop();
        }
    }

    private Object batchRecordValue(String uri, Object body) throws Exception {
        KafkaProducer producer = startedProducer(uri);
        try {
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setBody(List.of(body));
            return producer.createRecordIterable(exchange, exchange.getMessage()).next().getValue().value();
        } finally {
            producer.stop();
        }
    }

    private KafkaProducer startedProducer(String uri) {
        String brokers = (uri.contains("?") ? "&" : "?") + "brokers=localhost:39092";
        KafkaEndpoint endpoint = context.getEndpoint(uri + brokers, KafkaEndpoint.class);
        KafkaProducer producer = new KafkaProducer(endpoint);
        producer.setKafkaProducer(Mockito.mock(org.apache.kafka.clients.producer.Producer.class));
        producer.start();
        return producer;
    }

    record Payload(String name) {
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
    void azureFlagsFromAdditionalPropertiesAreBooleans() {
        KafkaEndpoint endpoint = context.getEndpoint(
                "kafka:mytopic?schemaRegistryVendor=azure&schemaRegistryURL=my-namespace.servicebus.windows.net"
                                                     + "&additionalProperties.auto.register.schemas=false"
                                                     + "&additionalProperties.use.azure.credential=false"
                                                     + "&additionalProperties.specific.avro.reader=true",
                KafkaEndpoint.class);

        // the Azure serde casts these flags to Boolean, so a String would fail when the client is created
        Properties producer = endpoint.getConfiguration().createProducerProperties();
        assertEquals(Boolean.FALSE, producer.get("auto.register.schemas"));
        assertEquals(Boolean.FALSE, producer.get("use.azure.credential"));

        Properties consumer = endpoint.getConfiguration().createConsumerProperties();
        assertEquals(Boolean.FALSE, consumer.get("use.azure.credential"));
        assertEquals(Boolean.TRUE, consumer.get("specific.avro.reader"));
    }

    @Test
    void emptyVendorOnTheEndpointOptsOutOfTheVendorOfTheComponent() {
        KafkaComponent kafka = context.getComponent("kafka", KafkaComponent.class);
        kafka.getConfiguration().setSchemaRegistryVendor("confluent");

        KafkaEndpoint dlq = context.getEndpoint(
                "kafka:dlq?valueSerializer=" + STRING_SERIALIZER + "&schemaRegistryVendor=", KafkaEndpoint.class);
        assertEquals(STRING_SERIALIZER,
                dlq.getConfiguration().createProducerProperties().get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));

        // a valueSerializer equal to the default cannot be told apart from the default
        KafkaEndpoint other = context.getEndpoint("kafka:other?valueSerializer=" + STRING_SERIALIZER, KafkaEndpoint.class);
        assertEquals("io.confluent.kafka.serializers.KafkaAvroSerializer",
                other.getConfiguration().createProducerProperties().get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
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

    static Stream<Arguments> artifacts() {
        return Stream.of(
                Arguments.of("confluent", "avro", "io.confluent:kafka-avro-serializer"),
                Arguments.of("confluent", "json", "io.confluent:kafka-json-schema-serializer"),
                Arguments.of("confluent", "protobuf", "io.confluent:kafka-protobuf-serializer"),
                Arguments.of("apicurio", "avro", "io.apicurio:apicurio-registry-avro-serde-kafka"),
                Arguments.of("apicurio", "json", "io.apicurio:apicurio-registry-jsonschema-serde-kafka"),
                Arguments.of("apicurio", "protobuf", "io.apicurio:apicurio-registry-protobuf-serde-kafka"),
                Arguments.of("aws-glue", "json", "software.amazon.glue:schema-registry-serde"),
                Arguments.of("azure", "avro", "com.microsoft.azure:azure-schemaregistry-kafka-avro"));
    }

    // the serde jars of Apicurio, AWS Glue and Azure are test dependencies (KafkaSchemaRegistryVendorClassesTest),
    // so only the Confluent ones, which are not in Maven Central, are missing from the test classpath
    static Stream<Arguments> missingArtifacts() {
        return artifacts().filter(a -> "confluent".equals(a.get()[0]));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("artifacts")
    void missingSerdeMessageNamesTheArtifact(String vendor, String format, String artifact) {
        assertEquals(artifact, KafkaSchemaRegistry.artifact(vendor, format));
        String className = KafkaSchemaRegistry.serializer(vendor, format);
        String msg = KafkaSchemaRegistry.missingSerdeMessage(vendor, format, "serializer", className);
        assertTrue(msg.startsWith("Cannot load the value serializer " + className + " of schemaRegistryVendor " + vendor
                                  + " and schemaFormat " + format + ". Add the Maven artifact " + artifact
                                  + " to the classpath"),
                msg);
        assertEquals("confluent".equals(vendor), msg.contains("camel run --repos=confluent"), msg);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("missingArtifacts")
    void producerFailsToStartWithoutTheSerializerAndNamesTheArtifact(String vendor, String format, String artifact)
            throws Exception {
        // the Confluent serde jars are not on the test classpath
        KafkaEndpoint endpoint = context.getEndpoint(
                "kafka:mytopic?brokers=localhost:39092&schemaRegistryVendor=" + vendor + "&schemaFormat=" + format,
                KafkaEndpoint.class);
        Producer producer = endpoint.createProducer();
        try {
            IllegalStateException e = assertThrows(IllegalStateException.class, producer::start);
            assertTrue(e.getMessage().startsWith("Cannot load the value serializer "
                                                 + KafkaSchemaRegistry.serializer(vendor, format)
                                                 + " of schemaRegistryVendor " + vendor + " and schemaFormat " + format
                                                 + ". Add the Maven artifact " + artifact + " to the classpath"),
                    e.getMessage());
            assertEquals("confluent".equals(vendor), e.getMessage().contains("camel run --repos=confluent"),
                    e.getMessage());
        } finally {
            producer.stop();
        }
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("missingArtifacts")
    void consumerFailsToStartWithoutTheDeserializerAndNamesTheArtifact(String vendor, String format, String artifact)
            throws Exception {
        KafkaEndpoint endpoint = context.getEndpoint(
                "kafka:mytopic?brokers=localhost:39092&groupId=g&schemaRegistryVendor=" + vendor + "&schemaFormat="
                                                     + format,
                KafkaEndpoint.class);
        Consumer consumer = endpoint.createConsumer(exchange -> {
        });
        try {
            IllegalStateException e = assertThrows(IllegalStateException.class, consumer::start);
            assertTrue(e.getMessage().startsWith("Cannot load the value deserializer "
                                                 + KafkaSchemaRegistry.deserializer(vendor, format)),
                    e.getMessage());
            assertTrue(e.getMessage().contains("Add the Maven artifact " + artifact + " to the classpath"),
                    e.getMessage());
        } finally {
            consumer.stop();
        }
    }

    @Test
    void serializerOfTheVendorIsLoadedThroughTheClassResolver() throws Exception {
        // Camel JBang downloads the jar of a known class that the class resolver is asked for and is not on the
        // classpath: the class the resolver returns is the one Kafka gets
        try (CamelContext camel = new DefaultCamelContext()) {
            camel.setClassResolver(new DefaultClassResolver(camel) {
                @Override
                public Class<?> resolveClass(String name) {
                    return "io.apicurio.registry.serde.avro.AvroKafkaSerializer".equals(name)
                            ? StringSerializer.class : super.resolveClass(name);
                }
            });
            camel.start();
            KafkaEndpoint endpoint = camel.getEndpoint(
                    "kafka:mytopic?brokers=localhost:39092&schemaRegistryVendor=apicurio", KafkaEndpoint.class);
            KafkaProducer producer = new KafkaProducer(endpoint);

            Properties props = producer.getProps();
            assertSame(StringSerializer.class, props.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
            assertNull(endpoint.getConfiguration().missingSchemaRegistrySerde(props,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
            endpoint.checkSchemaRegistrySerde(props, ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG);
        }
    }

    @Test
    void customClientFactoryOnlyWarnsAboutAMissingSerializer() {
        // a custom client factory may create the client without loading the class by its name
        KafkaEndpoint endpoint = context.getEndpoint(
                "kafka:mytopic?brokers=localhost:39092&schemaRegistryVendor=confluent", KafkaEndpoint.class);
        endpoint.setKafkaClientFactory(new DefaultKafkaClientFactory() {
        });
        Properties props = endpoint.getConfiguration().createProducerProperties();

        assertNotNull(endpoint.getConfiguration().missingSchemaRegistrySerde(props,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
        assertDoesNotThrow(() -> endpoint.checkSchemaRegistrySerde(props, ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
    }

    @Test
    void onlyTheMissingSerdeOfTheVendorIsReported() {
        // no vendor
        KafkaConfiguration config = new KafkaConfiguration();
        Properties props = new Properties();
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "com.example.MySerializer");
        assertNull(config.missingSchemaRegistrySerde(props, ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));

        // an explicit serializer that is not the one of the vendor is left to Kafka
        config.setSchemaRegistryVendor("confluent");
        assertNull(config.missingSchemaRegistrySerde(props, ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));

        // the serializer of the vendor that is still a class name could not be loaded
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "io.confluent.kafka.serializers.KafkaAvroSerializer");
        assertNotNull(config.missingSchemaRegistrySerde(props, ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
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
