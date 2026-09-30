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
package org.apache.camel.dsl.yaml;

import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpressionTest extends YamlTestSupport {

    @Test
    void errorDuplicateInlineExpressions() throws Exception {
        var route = """
                    - from:
                        uri: "direct:start"
                        steps:
                          - choice:
                              when:
                                - simple: "${body.size()} == 1"
                                  jq: ".size == 1"
                                  steps:
                                    - to: "log:when-a"
                """;
        try {
            loadRoutes(route);
            Assertions.fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage().contains("2 are valid")).as(e.getMessage()).isTrue();
        }
    }

    @Test
    void errorDuplicateExplicitExpressions() throws Exception {
        var route = """
                    - from:
                        uri: "direct:start"
                        steps:
                          - choice:
                              when:
                                - expression:
                                    simple: "${body.size()} == 1"
                                    jq: ".size == 1"
                                  steps:
                                    - to: "log:when-a"
                """;
        try {
            loadRoutes(route);
            Assertions.fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage().contains("2 are valid")).as(e.getMessage()).isTrue();
        }
    }

    @Test
    void errorInlineAndExplicitExpressions() throws Exception {
        var route = """
                    - from:
                        uri: "direct:start"
                        steps:
                          - choice:
                              when:
                                - simple: "${body.size()} == 1"
                                  expression:
                                    jq: ".size == 1"
                                  steps:
                                    - to: "log:when-a"
                """;
        try {
            loadRoutes(route);
            Assertions.fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage().contains("2 are valid")).as(e.getMessage()).isTrue();
        }
    }

    @Test
    void errorInlineNotExisting() {
        var route = """
                    - from:
                        uri: "direct:start"
                        steps:
                          - choice:
                              when:
                                - notsimple: "${body.size()} == 1"
                                  steps:
                                    - to: "log:when-a"
                """;
        try {
            loadRoutes(route);
            Assertions.fail("Should have thrown exception");
        } catch (Exception e) {
            assertThat(e.getMessage().contains("additional properties")).as(e.getMessage()).isTrue();
        }
    }

    @Test
    void errorExplicitNotExisting() {
        var route = """
                    - from:
                        uri: "direct:start"
                        steps:
                          - choice:
                              when:
                                - expression:
                                    notsimple: "${body.size()} == 1"
                                  steps:
                                    - to: "log:when-a"
                """;
        try {
            loadRoutes(route);
            Assertions.fail("Should have thrown exception");
        } catch (Exception e) {
            assertThat(e.getMessage().contains("0 are valid")).as(e.getMessage()).isTrue();
        }
    }

    @Test
    void noExpression() {
        var route = """
                    - from:
                        uri: "direct:start"
                        steps:
                          - choice:
                              when:
                                - steps:
                                    - to: "log:when-a"
                """;
        try {
            loadRoutes(route);
            Assertions.fail("Should have thrown exception");
        } catch (Exception e) {
            assertThat(e.getMessage().contains("when/0")).as(e.getMessage()).isTrue();
        }
    }

    @Test
    void sortWithoutExpressionIsAllowed() throws Exception {
        loadRoutes("""
                    - from:
                        uri: "direct:start"
                        steps:
                          - sort:
                              comparator: "#myComparator"
                          - to: "log:sorted"
                """);
        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);
    }

    @Test
    void errorExplicitNotExistingSaysDidYouMean() {
        Exception e = assertThrows(Exception.class, () -> loadRoutesNoValidate("""
                    - from:
                        uri: "direct:start"
                        steps:
                          - setBody:
                              expression:
                                simpel: "${body}"
                """));

        boolean found = false;
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage()
                    .contains("Unknown expression with id: simpel (not a built-in Camel language; did you mean 'simple'?)")) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }
}
