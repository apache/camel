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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * YAML DSL validator that tooling can use to validate Camel source files if they can be parsed and are valid according
 * to the Camel YAML DSL spec.
 */
public class YamlValidator {

    private static final String DRAFT = "http://json-schema.org/draft-04/schema#";
    private static final String LOCATION = "/schema/camelYamlDsl.json";

    private final ObjectMapper mapper = YAMLMapper.builder().build();
    private Schema schema;

    public List<Error> validate(File file) throws Exception {
        if (schema == null) {
            init();
        }
        try {
            var target = mapper.readTree(file);
            return new ArrayList<>(schema.validate(target));
        } catch (Exception e) {
            Error error = Error.builder()
                    .keyword("parser")
                    .messageSupplier(() -> e.getClass().getName() + ": " + e.getMessage())
                    .build();
            return List.of(error);
        }
    }

    public void init() throws Exception {
        var model = mapper.readTree(YamlValidator.class.getResourceAsStream(LOCATION));

        // Detect version from $schema field if present
        SpecificationVersion version = SpecificationVersion.DRAFT_4;
        if (model.has("$schema")) {
            String dialectId = model.get("$schema").asString();
            version = SpecificationVersion.fromDialectId(dialectId).orElse(SpecificationVersion.DRAFT_4);
        }

        // Create schema registry with config
        SchemaRegistryConfig config = SchemaRegistryConfig.builder()
                .locale(Locale.ENGLISH)
                .build();

        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(version, builder -> builder
                .schemaRegistryConfig(config));

        SchemaLocation location = SchemaLocation.of("classpath:" + LOCATION);
        schema = registry.getSchema(location, model);
    }

}
