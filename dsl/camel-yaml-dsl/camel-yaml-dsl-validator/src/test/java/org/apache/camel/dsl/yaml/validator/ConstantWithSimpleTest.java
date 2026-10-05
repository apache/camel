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

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A constant is used as written: ${header.sku} in it is not evaluated, and the route answers the literal text.
 */
public class ConstantWithSimpleTest {

    private static YamlValidator validator;

    @BeforeAll
    public static void setup() throws Exception {
        validator = new YamlValidator();
        validator.init();
    }

    @Test
    public void testSimpleInAConstantIsReported() {
        // what the benchmark wrote for the 404 answer of an unknown sku
        String yaml = """
                - route:
                    from:
                      uri: direct:getStock
                      steps:
                        - setHeader:
                            name: CamelHttpResponseCode
                            constant: 404
                        - setBody:
                            expression:
                              constant: '{"error": "unknown sku ${header.sku}"}'
                """;
        assertThat(lines(yaml)).singleElement().satisfies(m -> {
            assertThat(m).startsWith("Line 10: ");
            assertThat(m).contains("/constant: ${header.sku} is not evaluated, a constant is used as written");
            assertThat(m).contains("use simple: {expression: \"{'error': 'unknown sku ${header.sku}'}\"}");
        });
    }

    @Test
    public void testTheShortFormIsReported() {
        String yaml = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - setHeader:
                            name: greeting
                            constant: "Hello ${body}"
                """;
        assertThat(lines(yaml)).singleElement()
                .satisfies(m -> assertThat(m).contains("/constant: ${body} is not evaluated"));
    }

    @Test
    public void testPlainConstantsAreFine() {
        // no simple function: text, a property placeholder, an environment style ${HOME}, a resource; and a template
        // header, whose ${...} the template engine evaluates
        String yaml = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - setHeader:
                            name: a
                            constant: "unknown sku"
                        - setHeader:
                            name: b
                            constant: "{{app.name}}"
                        - setHeader:
                            name: c
                            constant: "${HOME}/data"
                        - setBody:
                            constant: resource:file:stock.json
                        - setBody:
                            simple: "unknown sku ${header.sku}"
                        - setHeader:
                            name: CamelVelocityTemplate
                            expression:
                              constant: "Hi this is a velocity template that can do templating ${body}"
                """;
        assertThat(lines(yaml)).isEmpty();
    }

    private static List<String> lines(String yaml) {
        try {
            return YamlValidator.describeAll(yaml, validator.validate(yaml));
        } catch (Exception e) {
            throw new AssertionError("Failed to validate:\n" + yaml, e);
        }
    }
}
