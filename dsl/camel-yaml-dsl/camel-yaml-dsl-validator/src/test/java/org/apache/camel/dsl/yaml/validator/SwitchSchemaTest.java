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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SwitchSchemaTest {
    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void fallbackIsAnEndpointObject(boolean canonical) throws Exception {
        YamlValidator validator = new YamlValidator(canonical);
        String route = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - switch:
                            selector:
                              header:
                                expression: department
                            case:
                              - value: billing
                                uri: direct:billing
                            otherwise:
                              uri: direct:review
                """;
        assertThat(validator.validate(route)).isEmpty();
        assertThat(validator.validate(route.replace("value: billing", "id: billingCase\n                value: \"001\"")))
                .isEmpty();
        assertThat(validator.validate(route.replace("uri: direct:billing",
                "uri: direct\n                parameters:\n                  name: billing")
                .replace("uri: direct:review", "uri: direct\n              parameters:\n                name: review")))
                .isEmpty();
        assertThat(validator.validate(route.replace("                uri: direct:billing\n", ""))).isNotEmpty();
        assertThat(validator.validate(route.replace("uri: direct:billing", "steps: [{to: {uri: direct:billing}}]")))
                .isNotEmpty();
        assertThat(validator.validate(route.replace("case:", "keys: [department, urgent]\n            case:")))
                .isNotEmpty();
        assertThat(validator.validate(route.replace("value: billing", "values: [department: billing]")))
                .isNotEmpty();
        for (String fallback : new String[] { "direct:review", "{}", "{steps: [{to: {uri: direct:review}}]}" }) {
            String invalid = route.replace("otherwise:\n              uri: direct:review", "otherwise: " + fallback);
            assertThat(validator.validate(invalid)).as("fallback: %s", fallback).isNotEmpty();
        }
    }
}
