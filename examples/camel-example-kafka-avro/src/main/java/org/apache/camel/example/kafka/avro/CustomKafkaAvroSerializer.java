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
package org.apache.camel.example.kafka.avro;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerializer;
import io.confluent.kafka.serializers.AvroSchemaUtils;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;


import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomKafkaAvroSerializer extends AbstractKafkaAvroSerializer  implements Serializer<Object> {

    private static final Logger LOG = LoggerFactory.getLogger(CustomKafkaAvroSerializer.class);
    private static final String SCHEMA_REGISTRY_URL = "http://localhost:8081";
    private boolean useSpecificAvroReader = true;
    private boolean isKey;

    @Override
    public void close() {
    }


    @Override
    public byte[] serialize(String topic, Object record) {
        LOG.info("****************serialize*******************************");
        LOG.info("Serialize method: topic " + topic);
        LOG.info("Serialize method: byte " + record);
        return serializeImpl(
             getSubjectName(topic, isKey, record, AvroSchemaUtils.getSchema(record)), record);
    }

    @Override
    public void configure(KafkaAvroSerializerConfig config) {
        LOG.info("ENTER CustomKafkaAvroDeserializer  : configure method ");
        LOG.info("ENTER CustomKafkaAvroDeserializer  : SCHEMA_REGISTRY_URL " + SCHEMA_REGISTRY_URL);

        if (SCHEMA_REGISTRY_URL == null) {
            throw new org.apache.kafka.common.config.ConfigException("No schema registry provided");
        }
        try {
            final List<String> schemas = Collections.singletonList(SCHEMA_REGISTRY_URL);
            this.schemaRegistry = new CachedSchemaRegistryClient(schemas, Integer.MAX_VALUE);
            this.useSpecificAvroReader = true;

        } catch (ConfigException e) {
            e.printStackTrace();
            throw new org.apache.kafka.common.config.ConfigException(e.getMessage());
        }
        LOG.info("EXIT CustomKafkaAvroserializer  : configure method ");
    }


    @Override
    public void configure(Map<String, ?> arg0, boolean arg1) {
        configure(null);
    }
}