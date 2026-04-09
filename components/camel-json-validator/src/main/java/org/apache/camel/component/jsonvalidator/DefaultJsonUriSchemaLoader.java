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
package org.apache.camel.component.jsonvalidator;

import java.io.*;
import java.net.*;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import org.apache.camel.CamelContext;
import org.apache.camel.support.ResourceHelper;

public class DefaultJsonUriSchemaLoader implements JsonUriSchemaLoader {

    protected ObjectMapper mapper = new ObjectMapper();

    protected SchemaRegistryConfig config = SchemaRegistryConfig.builder().build();

    protected SpecificationVersion defaultVersion = SpecificationVersion.DRAFT_2019_09;

    @Override
    public Schema createSchema(CamelContext camelContext, String schemaUri) throws Exception {
        // Load the schema content
        InputStream stream = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, schemaUri);
        JsonNode node = mapper.readTree(stream);

        // Determine schema version from $schema property or use the default
        SpecificationVersion version = getSpecificationVersion(node).orElse(defaultVersion);

        // Create schema registry with the detected version
        SchemaRegistry schemaRegistry = SchemaRegistry.withDefaultDialect(version,
                builder -> builder.schemaRegistryConfig(config));

        // the URI based method will correctly resolve relative schema references to other schema in the same directory
        URI uri;
        if (ResourceHelper.hasScheme(schemaUri)) {
            uri = URI.create(schemaUri);
        } else {
            uri = URI.create("classpath:" + schemaUri);
        }

        SchemaLocation schemaLocation = SchemaLocation.of(uri.toString());
        return schemaRegistry.getSchema(schemaLocation, node);
    }

    static Optional<SpecificationVersion> getSpecificationVersion(JsonNode schemaNode) {
        if (schemaNode != null) {
            JsonNode schema = schemaNode.get("$schema");
            if (schema != null && schema.isTextual()) {
                return SpecificationVersion.fromDialectId(schema.asText());
            }
        }
        return Optional.empty();
    }
}
