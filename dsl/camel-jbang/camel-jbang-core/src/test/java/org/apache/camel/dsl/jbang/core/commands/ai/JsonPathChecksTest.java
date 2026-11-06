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
                .contains("leave the index out and set resultType: java.util.Map (the element when one matches, null"
                          + " when none or several do)"));
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
        // in a when, the filter alone is the condition: a Map would be null also when several elements match
        assertThat(errors).singleElement().satisfies(e -> assertThat(e)
                .startsWith("Line 9: && and || work inside a filter only")
                .contains("in a when or filter, the filter alone is the condition (true when an element matches), so"
                          + " move the other conditions inside it")
                .doesNotContain("java.util.Map"));
    }

    @Test
    void orOutsideAFilterAsAValue() {
        String yaml = """
                - route:
                    from:
                      uri: direct:get-order
                      steps:
                        - setHeader:
                            name: found
                            expression:
                              jsonpath:
                                expression: "$[?(@.orderId == '${header.id}')] || $[?(@.sku == 'X')]"
                """;
        List<String> errors = JsonPathChecks.validateYamlJsonPath(yaml, catalog);
        assertThat(errors).singleElement().satisfies(e -> assertThat(e)
                .startsWith("Line 9: && and || work inside a filter only")
                .contains("to test whether an element matches, use the filter as the condition"));
    }

    @Test
    void anIndexAfterAFilterThenAFieldSaysToLeaveTheIndexOut() {
        // the Map advice gives [42] converted to a Map here: the field wants the type of the field
        String yaml = """
                - route:
                    from:
                      uri: direct:stock
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$[?(@.sku == 'X')][0].qty"
                """;
        List<String> errors = JsonPathChecks.validateYamlJsonPath(yaml, catalog);
        assertThat(errors).singleElement().satisfies(e -> assertThat(e)
                .startsWith("Line 8: an index after a filter gives an empty list in JsonPath")
                .contains("leave the index out, as in $[?(@.sku == 'X')].qty, and set resultType to the type of that"
                          + " value, such as java.lang.Integer")
                .doesNotContain("java.util.Map"));
    }

    @Test
    void anIndexAfterAFilterInAWhenSaysTheFilterIsTheCondition() {
        String yaml = """
                - route:
                    from:
                      uri: direct:get-order
                      steps:
                        - filter:
                            expression:
                              jsonpath:
                                expression: "$[?(@.orderId == '${header.id}')][0]"
                """;
        List<String> errors = JsonPathChecks.validateYamlJsonPath(yaml, catalog);
        assertThat(errors).singleElement().satisfies(e -> assertThat(e)
                .startsWith("Line 8: an index after a filter gives an empty list in JsonPath")
                .contains("leave the index out: in a when or filter, the filter alone is the condition")
                .doesNotContain("java.util.Map"));
    }

    @Test
    void anIndexInAFieldAfterAFilterIsFine() {
        String yaml = """
                - route:
                    from:
                      uri: direct:get-order
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$.orders[?(@.id == 1)].items[0]"
                """;
        assertThat(JsonPathChecks.validateYamlJsonPath(yaml, catalog)).isEmpty();
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

    /**
     * r28n-3 contracts-rest-code-first step 1 [19] (shortened): [] after the filter does not compile, which only the
     * runtime said, 16 times ("Could not parse token starting at position 29").
     */
    @Test
    void anExpressionThatDoesNotCompile() {
        String yaml = """
                - route:
                    id: get-order-by-id
                    from:
                      uri: direct:get-order-by-id
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$[?(@.orderId == '${header.id}')][]"
                                resultType: java.util.List
                """;
        assertThat(JsonPathChecks.validateYamlJsonPath(yaml, catalog)).singleElement().isEqualTo(
                "Line 9: jsonpath $[?(@.orderId == '${header.id}')][] does not compile: Could not parse token starting at"
                                                                                                 + " position 33 (at []). Expected ?, ', 0-9, *; JsonPath has no [], and a filter"
                                                                                                 + " or [*] gives a list already: write $[?(@.orderId == '${header.id}')]");
        // without a placeholder, and in a split
        assertThat(JsonPathChecks.validateYamlJsonPath(yaml.replace("$[?(@.orderId == '${header.id}')][]", "$.lines[]"),
                catalog)).singleElement().asString()
                .startsWith("Line 9: jsonpath $.lines[] does not compile: Could not parse token starting at position 7"
                            + " (at [])")
                .endsWith("write $.lines");
        assertThat(JsonPathChecks.validateYamlJsonPath("""
                - route:
                    from:
                      uri: timer:t
                      steps:
                        - split:
                            jsonpath: "$.lines[*]."
                            steps:
                              - log: "${body}"
                """, catalog)).singleElement().isEqualTo(
                "Line 6: jsonpath $.lines[*]. does not compile: Path must not end with a '.' or '..'");
    }

    /** Camel fills in the ${...} first: a placeholder can be an index, a number, a text or a name. */
    @Test
    void placeholdersAreFilledInFirst() {
        String yaml = """
                - route:
                    from:
                      uri: direct:stock
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "%s"
                """;
        for (String path : List.of("$.items[${header.index}]", "$[?(@.qty > ${header.min})]",
                "$[?(@.sku == '${header.sku}')].qty", "$.${header.field}", "$.{{stock.field}}", "$[?(${header.filter})]",
                "$..[?(@.sku in ['${header.a}', '${header.b}'])]", "$.lines.length()")) {
            assertThat(JsonPathChecks.validateYamlJsonPath(yaml.formatted(path), catalog)).as(path).isEmpty();
        }
        // r28n-4: the two characters \n after the closing quote, a line with no value to compile, which other checks say
        assertThat(JsonPathChecks.compileProblem(catalog,
                "\"$[?(@.orderId == '${header.id}')]\"\\n    resultType: java.util.List", false)).isNull();
        // the easy predicate syntax of a when or filter is turned into a filter first, so it is not compiled as written
        assertThat(JsonPathChecks.validateYamlJsonPath("""
                - route:
                    from:
                      uri: direct:stock
                      steps:
                        - filter:
                            expression:
                              jsonpath: "$.qty > ${header.min}"
                            steps:
                              - log: "low"
                """, catalog)).isEmpty();
    }
}
