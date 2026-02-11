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
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.ValidationMessage;

/**
 * YAML DSL parser that tooling can use to parse Camel source files to check if they can be YAML parsed.
 *
 * Notice that this parser can only check if the source can be parsed as YAML, but cannot check if any of the content is
 * correct Camel DSL, such as if EIPs have typos etc.
 */
public class YamlParser {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public List<ValidationMessage> parse(File file) throws Exception {
        try {
            mapper.readTree(file);
            return Collections.emptyList();
        } catch (Exception e) {
            ValidationMessage vm = ValidationMessage.builder().type("parser")
                    .messageSupplier(() -> e.getClass().getName() + ": " + e.getMessage()).build();
            return List.of(vm);
        }
    }

}
