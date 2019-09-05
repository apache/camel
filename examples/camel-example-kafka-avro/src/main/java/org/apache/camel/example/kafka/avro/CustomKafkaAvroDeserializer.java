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

import io.confluent.common.config.ConfigException;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.serializers.AbstractKafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;

import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomKafkaAvroDeserializer extends AbstractKafkaAvroDeserializer  implements Deserializer<Object> {
    private static final Logger LOG = LoggerFactory.getLogger(CustomKafkaAvroDeserializer.class);
    private static final String SCHEMA_REGISTRY_URL = "http://localhost:8081";

    @Override
    public void configure(KafkaAvroDeserializerConfig config) {
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
        LOG.info("EXIT CustomKafkaAvroDeserializer  : configure method ");

    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        configure(null);
    }

    @Override
    public Object deserialize(String s, byte[] bytes) {
        LOG.info("ENTER CustomKafkaAvroDeserializer  : deserialize method ");
        return deserialize(bytes).toString();
    }
  
    @Override
    public void close() {
    }
}
