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

import java.util.Locale;
import java.util.Properties;

import org.apache.camel.util.ObjectHelper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;

/**
 * The value serializer, value deserializer and registry properties that the schemaRegistryVendor and schemaFormat
 * options set up for a schema registry vendor.
 * <p/>
 * The serializer and deserializer are class names and the properties are keys of the vendor serde libraries, so that
 * Camel does not depend on any vendor library: the user adds the serde jar of the vendor. The keys and class names are
 * the ones of the vendor libraries (Confluent kafka-avro-serializer 8.x, Apicurio Registry 3.x serdes, AWS Glue
 * schema-registry-serde 1.1 and 2.0, and Azure azure-schemaregistry-kafka-avro 1.1).
 */
final class KafkaSchemaRegistry {

    static final String CONFLUENT = "confluent";
    static final String APICURIO = "apicurio";
    static final String AWS_GLUE = "aws-glue";
    static final String AZURE = "azure";

    static final String AVRO = "avro";
    static final String JSON = "json";
    static final String PROTOBUF = "protobuf";

    static final String VENDORS = CONFLUENT + "," + APICURIO + "," + AWS_GLUE + "," + AZURE;
    static final String FORMATS = AVRO + "," + JSON + "," + PROTOBUF;

    // Confluent (and Azure, which uses the same key names)
    static final String CONFLUENT_URL = "schema.registry.url";
    static final String CONFLUENT_SPECIFIC_AVRO_READER = "specific.avro.reader";
    // Apicurio Registry 3.x (SchemaResolverConfig and AvroSerdeConfig)
    static final String APICURIO_URL = "apicurio.registry.url";
    static final String APICURIO_AUTO_REGISTER = "apicurio.registry.auto-register";
    static final String APICURIO_SPECIFIC_AVRO_READER = "apicurio.registry.use-specific-avro-reader";
    // AWS Glue (AWSSchemaRegistryConstants)
    static final String GLUE_ENDPOINT = "endpoint";
    static final String GLUE_DATA_FORMAT = "dataFormat";
    static final String GLUE_AUTO_REGISTRATION = "schemaAutoRegistrationEnabled";
    static final String GLUE_AVRO_RECORD_TYPE = "avroRecordType";
    // Azure (AbstractKafkaSerdeConfig and KafkaAvroSerializerConfig)
    static final String AZURE_USE_AZURE_CREDENTIAL = "use.azure.credential";
    static final String AZURE_CREDENTIAL = "schema.registry.credential";
    static final String AZURE_AUTO_REGISTER = "auto.register.schemas";

    private static final String GLUE_SERIALIZER
            = "com.amazonaws.services.schemaregistry.serializers.GlueSchemaRegistryKafkaSerializer";
    private static final String GLUE_DESERIALIZER
            = "com.amazonaws.services.schemaregistry.deserializers.GlueSchemaRegistryKafkaDeserializer";

    private KafkaSchemaRegistry() {
    }

    /**
     * The vendor in lower case, or null when no vendor is set. Fails for an unknown vendor, an unknown format, or a
     * format the vendor does not support.
     */
    static String vendor(String vendor, String format) {
        if (ObjectHelper.isEmpty(vendor)) {
            return null;
        }
        String v = vendor.trim().toLowerCase(Locale.ROOT);
        if (!CONFLUENT.equals(v) && !APICURIO.equals(v) && !AWS_GLUE.equals(v) && !AZURE.equals(v)) {
            throw new IllegalArgumentException(
                    "Unknown schemaRegistryVendor: " + vendor + ". The supported values are " + VENDORS
                                               + ". Leave schemaRegistryVendor unset to configure the serializer, "
                                               + "deserializer and registry properties yourself.");
        }
        String f = format(format);
        if (AZURE.equals(v) && !AVRO.equals(f)) {
            throw new IllegalArgumentException(
                    "schemaRegistryVendor azure supports only schemaFormat avro, not " + f
                                               + ": the Azure Schema Registry serde for Kafka "
                                               + "(com.microsoft.azure:azure-schemaregistry-kafka-avro) is for Avro only.");
        }
        return v;
    }

    private static String format(String format) {
        if (ObjectHelper.isEmpty(format)) {
            return AVRO;
        }
        String f = format.trim().toLowerCase(Locale.ROOT);
        if (!AVRO.equals(f) && !JSON.equals(f) && !PROTOBUF.equals(f)) {
            throw new IllegalArgumentException(
                    "Unknown schemaFormat: " + format + ". The supported values are " + FORMATS + ".");
        }
        return f;
    }

    /**
     * The value serializer class name of the vendor and format.
     */
    static String serializer(String vendor, String format) {
        return serializerOf(vendor, format(format));
    }

    private static String serializerOf(String vendor, String f) {
        return switch (vendor) {
            case CONFLUENT -> switch (f) {
                case JSON -> "io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer";
                case PROTOBUF -> "io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer";
                default -> "io.confluent.kafka.serializers.KafkaAvroSerializer";
            };
            case APICURIO -> switch (f) {
                case JSON -> "io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaSerializer";
                case PROTOBUF -> "io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer";
                default -> "io.apicurio.registry.serde.avro.AvroKafkaSerializer";
            };
            case AWS_GLUE -> GLUE_SERIALIZER;
            default -> "com.microsoft.azure.schemaregistry.kafka.avro.KafkaAvroSerializer";
        };
    }

    /**
     * The value deserializer class name of the vendor and format.
     */
    static String deserializer(String vendor, String format) {
        return deserializerOf(vendor, format(format));
    }

    private static String deserializerOf(String vendor, String f) {
        return switch (vendor) {
            case CONFLUENT -> switch (f) {
                case JSON -> "io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer";
                case PROTOBUF -> "io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer";
                default -> "io.confluent.kafka.serializers.KafkaAvroDeserializer";
            };
            case APICURIO -> switch (f) {
                case JSON -> "io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaDeserializer";
                case PROTOBUF -> "io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer";
                default -> "io.apicurio.registry.serde.avro.AvroKafkaDeserializer";
            };
            case AWS_GLUE -> GLUE_DESERIALIZER;
            default -> "com.microsoft.azure.schemaregistry.kafka.avro.KafkaAvroDeserializer";
        };
    }

    /**
     * The Maven artifact (groupId:artifactId) of the serializer and deserializer of the vendor and format.
     */
    static String artifact(String vendor, String format) {
        String f = format(format);
        return switch (vendor) {
            case CONFLUENT -> switch (f) {
                case JSON -> "io.confluent:kafka-json-schema-serializer";
                case PROTOBUF -> "io.confluent:kafka-protobuf-serializer";
                default -> "io.confluent:kafka-avro-serializer";
            };
            case APICURIO -> switch (f) {
                case JSON -> "io.apicurio:apicurio-registry-jsonschema-serde-kafka";
                case PROTOBUF -> "io.apicurio:apicurio-registry-protobuf-serde-kafka";
                default -> "io.apicurio:apicurio-registry-avro-serde-kafka";
            };
            case AWS_GLUE -> "software.amazon.glue:schema-registry-serde";
            default -> "com.microsoft.azure:azure-schemaregistry-kafka-avro";
        };
    }

    /**
     * The message for a serializer or deserializer of the vendor that cannot be loaded, which names the Maven artifact
     * to add (and for Confluent, the repository).
     *
     * @param kind      serializer or deserializer
     * @param className the class that cannot be loaded
     */
    static String missingSerdeMessage(String vendor, String format, String kind, String className) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("Cannot load the value ").append(kind).append(' ').append(className)
                .append(" of schemaRegistryVendor ").append(vendor).append(" and schemaFormat ").append(format(format))
                .append(". Add the Maven artifact ").append(artifact(vendor, format)).append(" to the classpath");
        switch (vendor) {
            case CONFLUENT -> sb.append(". The Confluent serde jars are not in Maven Central but in the Confluent Maven"
                                        + " repository https://packages.confluent.io/maven/ (with Camel JBang:"
                                        + " camel run --repos=confluent).");
            case APICURIO -> sb.append(" (version 3.x).");
            case AZURE -> sb.append(", with a recent com.azure:azure-core (its dependencies ask for an older one).");
            default -> sb.append('.');
        }
        return sb.toString();
    }

    /**
     * Adds the registry URL under the key of the vendor. Without a vendor, the URL is set as schema.registry.url, the
     * key of Confluent and of the registries that follow its serde configuration.
     */
    static void applyUrl(Properties props, String vendor, String url) {
        if (ObjectHelper.isEmpty(url)) {
            return;
        }
        if (vendor == null) {
            props.put(CONFLUENT_URL, url);
            return;
        }
        switch (vendor) {
            case APICURIO -> props.put(APICURIO_URL, url);
            // Glue is addressed by region and registry name, the URL is an optional endpoint override
            case AWS_GLUE -> props.put(GLUE_ENDPOINT, url);
            default -> props.put(CONFLUENT_URL, url);
        }
    }

    /**
     * Sets the value serializer (unless the user set one) and the producer properties of the vendor.
     */
    static void applyProducer(Properties props, String vendor, String format, boolean explicitSerializer) {
        String f = format(format);
        if (!explicitSerializer) {
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, serializerOf(vendor, f));
        }
        switch (vendor) {
            // Confluent registers the schemas by default (auto.register.schemas=true)
            case APICURIO -> props.put(APICURIO_AUTO_REGISTER, Boolean.TRUE);
            case AWS_GLUE -> {
                props.put(GLUE_DATA_FORMAT, f.toUpperCase(Locale.ROOT));
                props.put(GLUE_AUTO_REGISTRATION, Boolean.TRUE);
            }
            case AZURE -> {
                // the Azure serde reads its flags as java.lang.Boolean (a String fails)
                props.put(AZURE_AUTO_REGISTER, Boolean.TRUE);
                props.put(AZURE_USE_AZURE_CREDENTIAL, Boolean.TRUE);
            }
            default -> {
                // nothing more for confluent
            }
        }
    }

    /**
     * Sets the value deserializer (unless the user set one) and the consumer properties of the vendor.
     */
    static void applyConsumer(
            Properties props, String vendor, String format, boolean specificAvroReader, boolean explicitDeserializer) {
        String f = format(format);
        if (!explicitDeserializer) {
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializerOf(vendor, f));
        }
        boolean avro = AVRO.equals(f);
        switch (vendor) {
            case APICURIO -> {
                if (avro && specificAvroReader) {
                    props.put(APICURIO_SPECIFIC_AVRO_READER, Boolean.TRUE);
                }
            }
            case AWS_GLUE -> {
                props.put(GLUE_DATA_FORMAT, f.toUpperCase(Locale.ROOT));
                if (avro) {
                    // read as a String by the Glue serde
                    props.put(GLUE_AVRO_RECORD_TYPE, specificAvroReader ? "SPECIFIC_RECORD" : "GENERIC_RECORD");
                }
            }
            case AZURE -> {
                props.put(AZURE_USE_AZURE_CREDENTIAL, Boolean.TRUE);
                if (specificAvroReader) {
                    props.put(CONFLUENT_SPECIFIC_AVRO_READER, Boolean.TRUE);
                }
            }
            default -> {
                if (avro && specificAvroReader) {
                    props.put(CONFLUENT_SPECIFIC_AVRO_READER, Boolean.TRUE);
                }
            }
        }
    }

    /**
     * Converts the flags of the Azure serde that are given as a String, such as from additionalProperties in a URI or
     * YAML, to java.lang.Boolean: the Azure serde casts them to Boolean, and a String fails when the client is created.
     * Does nothing for the other vendors, whose serdes parse a String.
     */
    static void applyBooleanFlags(Properties props, String vendor) {
        if (!AZURE.equals(vendor)) {
            return;
        }
        for (String key : new String[] { AZURE_AUTO_REGISTER, AZURE_USE_AZURE_CREDENTIAL, CONFLUENT_SPECIFIC_AVRO_READER }) {
            if (props.get(key) instanceof String value) {
                props.put(key, Boolean.valueOf(value.trim()));
            }
        }
    }
}
