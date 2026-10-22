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

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonPathChecksTest {

    private static CamelCatalog catalog;

    @BeforeAll
    static void loadCatalog() {
        catalog = new DefaultCamelCatalog();
    }

    @Test
    void comparisonAsAnExpression() {
        // CAMEL-24841: setHeader wants a value; the comparison is a condition
        String yaml = """
                - route:
                    from:
                      uri: "timer:t?repeatCount=1"
                      steps:
                        - setHeader:
                            name: paid
                            expression:
                              jsonpath:
                                expression: "$.status == 'paid'"
                """;
        List<String> errors = JsonPathChecks.validateYamlJsonPath(yaml, catalog);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).startsWith("Line 9: jsonpath is a path, not a comparison")
                .contains("write the path $.status and compare it in simple (${body[status]} == 'paid')")
                .contains("read as $[?(@.status == 'paid')]");
    }

    /** The one order of a list by its id (the REST example of the r20 runs): an index after the filter gives []. */
    @Test
    void anIndexAfterAFilterGivesAnEmptyList() {
        String yaml = """
                - route:
                    from:
                      uri: direct:get-order
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$[?(@.orderId == '${header.id}')][0]"
                """;
        List<String> errors = JsonPathChecks.validateYamlJsonPath(yaml, catalog);
        assertThat(errors).singleElement().satisfies(e -> assertThat(e)
                .startsWith("Line 8: an index after a filter gives an empty list in JsonPath")
                .contains("leave the index out and set resultType: java.util.Map"));
    }

    @Test
    void andOutsideAFilterIsNoJsonPath() {
        String yaml = """
                - route:
                    from:
                      uri: direct:get-order
                      steps:
                        - choice:
                            when:
                              - expression:
                                  jsonpath:
                                    expression: "$[?(@.orderId == '${header.id}')] && size() > 0"
                """;
        List<String> errors = JsonPathChecks.validateYamlJsonPath(yaml, catalog);
        assertThat(errors).singleElement().satisfies(e -> assertThat(e)
                .startsWith("Line 9: && and || work inside a filter only")
                .contains("set resultType: java.util.Map").contains("check ${body} == null"));
    }

    @Test
    void theOneElementRecipeIsFine() {
        String yaml = """
                - route:
                    from:
                      uri: direct:get-order
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$[?(@.orderId == '${header.id}' && @.status == 'paid')]"
                                resultType: java.util.Map
                """;
        assertThat(JsonPathChecks.validateYamlJsonPath(yaml, catalog)).isEmpty();
    }

    @Test
    void nestedPathHasNoSimpleForm() {
        String yaml = """
                - route:
                    from:
                      uri: "timer:t?repeatCount=1"
                      steps:
                        - setBody:
                            expression:
                              jsonpath: "$.store.book.price < 10"
                """;
        List<String> errors = JsonPathChecks.validateYamlJsonPath(yaml, catalog);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("write the path $.store.book.price;").doesNotContain("${body[");
    }

    @Test
    void comparisonAsACondition() {
        // a when or filter reads it as the easy predicate: nothing to say
        String yaml = """
                - route:
                    from:
                      uri: "timer:t?repeatCount=1"
                      steps:
                        - choice:
                            when:
                              - expression:
                                  jsonpath:
                                    expression: "$.status == 'paid'"
                                steps:
                                  - log: "paid"
                        - filter:
                            expression:
                              jsonpath: "$.status == 'paid'"
                            steps:
                              - log: "paid"
                """;
        assertThat(JsonPathChecks.validateYamlJsonPath(yaml, catalog)).isEmpty();
    }

    @Test
    void pathsAndFiltersAreKept() {
        String yaml = """
                - route:
                    from:
                      uri: "timer:t?repeatCount=1"
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$.lines[?(@.qty > 1)]"
                        - split:
                            expression:
                              jsonpath: "$.lines[*]"
                            steps:
                              - log: "${body}"
                """;
        assertThat(JsonPathChecks.validateYamlJsonPath(yaml, catalog)).isEmpty();
    }
}
