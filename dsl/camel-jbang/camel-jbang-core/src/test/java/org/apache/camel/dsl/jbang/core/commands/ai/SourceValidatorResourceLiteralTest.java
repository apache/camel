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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24882: a script that returns 'resource:file:x' returns that text; the validator says to add resolveResource:
 * true or to use constant/simple.
 */
class SourceValidatorResourceLiteralTest {

    @Test
    void aScriptReturningAResourceLiteralGetsTheHint() {
        String yaml = """
                - route:
                    from:
                      uri: timer:orders
                      steps:
                        - setBody:
                            expression:
                              groovy: |
                                def counter = exchange.getProperty('CamelTimerCounter', Integer) ?: 0
                                counter == 0 ? 'resource:file:order.json' : 'resource:file:order-bad-email.json'
                        - setBody:
                            expression:
                              jq:
                                expression: "\\"resource:classpath:templates/\\" + .type + \\".json\\""
                """;
        List<String> errors = BeanRefChecks.validateReturnedResourceLiterals(yaml);
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0))
                .startsWith("Line 7: groovy: the script returns the text 'resource:file:order.json', not the file")
                .contains("Add resolveResource: true under groovy:")
                .contains("constant: \"resource:file:order.json\"");
        assertThat(errors.get(1)).startsWith("Line 12: jq: the script returns the text 'resource:classpath:templates/'");
    }

    @Test
    void theResourcePrefixOnTheTextAndResolveResourceAreFine() {
        String yaml = """
                - route:
                    from:
                      uri: timer:orders
                      steps:
                        - setBody:
                            expression:
                              groovy: "resource:file:shipment-mapping.groovy"
                        - setBody:
                            expression:
                              groovy:
                                expression: "counter == 1 ? 'resource:file:order.json' : 'resource:file:other.json'"
                                resolveResource: true
                        - setBody:
                            expression:
                              constant:
                                expression: "resource:file:order.json"
                        - setBody:
                            expression:
                              simple: "resource:file:${header.name}"
                """;
        assertThat(BeanRefChecks.validateReturnedResourceLiterals(yaml)).isEmpty();
    }

    @Test
    void theDiagnoserNamesTheReturnedLiteral() {
        var out = ErrorDiagnoser.diagnose(
                "com.fasterxml.jackson.core.JsonParseException: Unrecognized token 'resource': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')",
                new org.apache.camel.catalog.DefaultCamelCatalog());
        assertThat(out.toJson()).contains("JsonParseException").contains("resolveResource: true");
    }
}
