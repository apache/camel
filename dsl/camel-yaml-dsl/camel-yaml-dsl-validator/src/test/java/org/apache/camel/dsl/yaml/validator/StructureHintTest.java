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
 * CAMEL-25372: the two shapes of a misplaced key, as a local model wrote them. A top-level key without its list item
 * marker is YAML, not prose; a key at the column of the step's EIP says where it goes.
 */
public class StructureHintTest {

    private static YamlValidator validator;

    @BeforeAll
    public static void setup() throws Exception {
        validator = new YamlValidator();
        validator.init();
    }

    @Test
    public void aTopLevelKeyWithoutTheListItemMarker() {
        String yaml = """
                - route:
                    id: parked
                    from:
                      uri: direct:parked
                      steps:
                        - log:
                            message: "parked ${body}"

                errorHandler:
                  deadLetterChannel:
                    deadLetterUri: direct:parked
                """;
        assertThat(lines(yaml)).singleElement().satisfies(m -> {
            assertThat(m).contains("line 9: errorHandler: is a top-level key in a file whose entries are list items");
            assertThat(m).contains("write it as a list item, - errorHandler:");
            assertThat(m).doesNotContain("put explanations in a # comment");
        });
    }

    @Test
    public void anOptionAtTheColumnOfItsEip() {
        String yaml = """
                - route:
                    from:
                      uri: file:orders
                      steps:
                        - to:
                            uri: "sql:INSERT INTO customers (id) VALUES (:#customer)"
                          parameters:
                            allowNamedParameters: true
                """;
        assertThat(lines(yaml)).anySatisfy(m -> assertThat(m)
                .contains("parameters: lines up with to:, so it is read as a second key of the - item")
                .contains("indent parameters: and the lines under it two spaces more, so parameters: lines up with"
                          + " uri:"));
    }

    @Test
    public void stepsAtTheColumnOfSplit() {
        // as qwen3.6 wrote it ten times in a row: the hint named "the column of - split:", and two spaces more than
        // the dash is where steps: already was; the second error said to move the items up a level
        String yaml = """
                - route:
                    from:
                      uri: timer:orders
                      steps:
                        - split:
                            expression:
                              simple: "${body[lines]}"
                          steps:
                            - log:
                                message: "pick ${body[qty]} x ${body[sku]}"
                """;
        assertThat(lines(yaml)).singleElement().satisfies(m -> assertThat(m)
                .startsWith("Line 5: ")
                .contains("steps: lines up with split:")
                .contains("so steps: lines up with expression:")
                .doesNotContain("move the items up"));
    }

    @Test
    public void anotherEipAtTheColumnOfTheFirst() {
        String yaml = """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - log:
                            message: "a"
                          to:
                            uri: log:b
                """;
        assertThat(lines(yaml)).anySatisfy(m -> assertThat(m)
                .contains("to: is another EIP: start it as its own item, - to:"));
    }

    @Test
    public void doCatchAtTheColumnOfDoTry() {
        // CAMEL-26874, r32n-4 route-batch-file step 2 (shortened): told that doCatch: is another EIP to start as its
        // own - item, the model sent the same lines 19 times. doCatch: is an option of doTry, and an EIP too.
        String yaml = """
                - route:
                    from:
                      uri: file:inbox
                      steps:
                        - doTry:
                            steps:
                              - unmarshal:
                                  csv:
                                    useMaps: true
                          doCatch:
                            - exception:
                                - java.lang.IllegalArgumentException
                              steps:
                                - log: "bad line ${body}"
                """;
        // doCatch: read as a - doCatch: step, which is a mapping, also gave "array found, object expected" at line 10
        assertThat(lines(yaml)).singleElement().satisfies(m -> assertThat(m)
                .startsWith("Line 5: ")
                .contains("doCatch: lines up with doTry:, so it is read as a second key of the - item; it is an option"
                          + " of doTry: indent doCatch: and the lines under it two spaces more, so doCatch: lines up with"
                          + " steps:")
                .doesNotContain("another EIP"));
    }

    @Test
    public void doFinallyAtTheColumnOfDoTry() {
        String yaml = """
                - route:
                    from:
                      uri: direct:batch
                      steps:
                        - doTry:
                            steps:
                              - to: file:out
                          doFinally:
                            steps:
                              - log: "batch done"
                """;
        assertThat(lines(yaml)).singleElement().satisfies(m -> assertThat(m)
                .contains("doFinally: lines up with doTry:, so it is read as a second key of the - item")
                .doesNotContain("another EIP"));
    }

    @Test
    public void aDoFinallyListIsWrongAsAnOptionToo() {
        // the option is a mapping as well: the error of the list stays, with its own hint
        String yaml = """
                - route:
                    from:
                      uri: direct:batch
                      steps:
                        - doTry:
                            steps:
                              - to: file:out
                          doFinally:
                            - log: "batch done"
                """;
        assertThat(lines(yaml)).hasSize(2)
                .anySatisfy(m -> assertThat(m).contains("doFinally: lines up with doTry:"))
                .anySatisfy(m -> assertThat(m).startsWith("Line 9: ").contains("array found, object expected"));
    }

    @Test
    public void aFilterTextAtTheColumnOfClaimCheck() {
        // filter: of claimCheck takes a text; the errors of filter: read as the filter EIP go with it
        String yaml = """
                - route:
                    from:
                      uri: direct:claim
                      steps:
                        - claimCheck:
                            operation: Get
                            key: order
                          filter: "header:order*"
                """;
        assertThat(lines(yaml)).singleElement().satisfies(m -> assertThat(m)
                .contains("filter: lines up with claimCheck:, so it is read as a second key of the - item")
                .contains("so filter: lines up with operation:"));
    }

    @Test
    public void aFilterWithStepsAfterClaimCheckIsAnotherEip() {
        // with a mapping, filter: is not the text option of claimCheck but the filter EIP
        String yaml = """
                - route:
                    from:
                      uri: direct:claim
                      steps:
                        - claimCheck:
                            operation: Push
                          filter:
                            simple: "${header.vip}"
                            steps:
                              - to: log:vip
                """;
        assertThat(lines(yaml)).singleElement().satisfies(m -> assertThat(m)
                .contains("filter: is another EIP: start it as its own item, - filter:"));
    }

    private static List<String> lines(String yaml) {
        try {
            return YamlValidator.describeAll(yaml, validator.validate(yaml));
        } catch (Exception e) {
            throw new AssertionError("Failed to validate:\n" + yaml, e);
        }
    }
}
