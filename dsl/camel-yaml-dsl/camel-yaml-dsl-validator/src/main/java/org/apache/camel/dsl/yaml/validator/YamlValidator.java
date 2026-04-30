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
package org.apache.camel.dsl.yaml.validator;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;

/**
 * YAML DSL validator that tooling can use to validate Camel source files if they can be parsed and are valid according
 * to the Camel YAML DSL spec.
 */
public class YamlValidator {

    private static final String LOCATION = "/schema/camelYamlDsl.json";
    private static final String LOCATION_CANONICAL = "/schema/camelYamlDsl-canonical.json";

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private final boolean canonical;
    private Schema schema;

    public YamlValidator() {
        this(false);
    }

    public YamlValidator(boolean canonical) {
        this.canonical = canonical;
    }

    public boolean isCanonical() {
        return canonical;
    }

    public List<Error> validate(File file) throws Exception {
        if (schema == null) {
            init();
        }
        try {
            var target = mapper.readTree(file);
            return new ArrayList<>(schema.validate(target));
        } catch (Exception e) {
            Error error = Error.builder()
                    .messageKey("parser")
                    .format(new MessageFormat(e.getClass().getName() + ": " + e.getMessage()))
                    .build();
            return List.of(error);
        }
    }

    public void init() throws Exception {
        String location = canonical ? LOCATION_CANONICAL : LOCATION;
        var model = mapper.readTree(YamlValidator.class.getResourceAsStream(location));
        var version = getSpecificationVersion(model).orElse(SpecificationVersion.DRAFT_4);
        var config = SchemaRegistryConfig.builder().locale(Locale.ENGLISH).build();

        var schemaRegistry = SchemaRegistry.withDefaultDialect(version,
                builder -> builder.schemaRegistryConfig(config));

        // Use a proper URI for the schema location to ensure $ref resolution works
        var schemaLocation = SchemaLocation.of(location);
        schema = schemaRegistry.getSchema(schemaLocation, model);
    }

    private static Optional<SpecificationVersion> getSpecificationVersion(JsonNode schemaNode) {
        var schemaField = schemaNode.get("$schema");
        if (schemaField != null && schemaField.isTextual()) {
            return SpecificationVersion.fromDialectId(schemaField.asText());
        }
        return Optional.empty();
    }

}
