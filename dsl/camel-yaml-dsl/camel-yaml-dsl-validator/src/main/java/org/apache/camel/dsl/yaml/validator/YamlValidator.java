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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.NonValidationKeyword;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersionDetector;
import com.networknt.schema.ValidationMessage;

/**
 * YAML DSL validator that tooling can use to validate Camel source files if they can be parsed and are valid according
 * to the Camel YAML DSL spec.
 */
public class YamlValidator {

    private static final String DRAFT = "http://json-schema.org/draft-04/schema#";
    private static final String LOCATION = "/schema/camelYamlDsl.json";

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private JsonSchema schema;

    public List<ValidationMessage> validate(File file) throws Exception {
        if (schema == null) {
            init();
        }
        try {
            var target = mapper.readTree(file);
            return new ArrayList<>(schema.validate(target));
        } catch (Exception e) {
            ValidationMessage vm = ValidationMessage.builder().type("parser")
                    .messageSupplier(() -> e.getClass().getName() + ": " + e.getMessage()).build();
            return List.of(vm);
        }
    }

    public void init() throws Exception {
        var model = mapper.readTree(YamlValidator.class.getResourceAsStream(LOCATION));
        var factory = JsonSchemaFactory.getInstance(SpecVersionDetector.detect(model));
        var config = SchemaValidatorsConfig.builder().locale(Locale.ENGLISH).build();
        // include deprecated as an unknown keyword so the validator does not WARN log about this
        JsonMetaSchema jms = factory.getMetaSchema(DRAFT, null);
        jms.getKeywords().put("deprecated", new NonValidationKeyword("deprecated"));
        schema = factory.getSchema(model, config);
    }

}
