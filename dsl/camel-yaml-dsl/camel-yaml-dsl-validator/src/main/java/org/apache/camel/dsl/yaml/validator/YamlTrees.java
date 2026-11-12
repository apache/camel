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

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLReadFeature;
import tools.jackson.dataformat.yaml.YAMLSchema;

/**
 * The mapper that reads YAML into a tree for the validators.
 */
public final class YamlTrees {

    private YamlTrees() {
    }

    /**
     * A YAML mapper with the Jackson 2 defaults. The YAML 1.2 core schema reads ~, Null and 0x1F as null and numbers,
     * as Jackson 2 did. Unlike Jackson 2, YAML 1.1 forms such as 1_000 and yes stay text, and a number with a leading 0
     * is decimal: octal parsing would fail on 08540, which Jackson 2 read as text.
     */
    public static ObjectMapper newMapper() {
        YAMLFactory factory = YAMLFactory.builder()
                .yamlSchema(YAMLSchema.CORE)
                .disable(YAMLReadFeature.PARSE_OCTAL_NUMBERS)
                .build();
        return YAMLMapper.builder(factory).configureForJackson2().build();
    }
}
