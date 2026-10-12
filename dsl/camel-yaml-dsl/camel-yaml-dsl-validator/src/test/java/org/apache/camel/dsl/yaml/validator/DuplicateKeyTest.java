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
 * CAMEL-25560: a key given twice in one mapping is reported with both lines; YAML keeps only the last one, so the route
 * would run without the first.
 */
public class DuplicateKeyTest {

    private static YamlValidator validator;

    @BeforeAll
    public static void setup() throws Exception {
        validator = new YamlValidator();
        validator.init();
    }

    @Test
    public void aMessageGivenTwiceInOneLog() {
        // an edit added the new message under the log and left the old one: the route logged the old one
        String yaml = """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - log:
                            message: "pick ${body[qty]} x ${body[sku]} for ${header.orderId}"
                            message: "pick ${body[qty]} x ${body[sku]}"
                """;
        assertThat(lines(yaml)).singleElement().satisfies(m -> {
            assertThat(m).contains("line 7: message: is given twice in this log: (lines 6 and 7)");
            assertThat(m).contains("YAML keeps only the last one, so the message: at line 6 is lost");
        });
    }

    @Test
    public void aWhenGivenTwiceInOneChoice() {
        String yaml = """
                - route:
                    from:
                      uri: direct:orders
                      steps:
                        - choice:
                            when:
                              - expression:
                                  simple:
                                    expression: "${header.country} == 'DK'"
                                steps:
                                  - log:
                                      message: local
                            when:
                              - expression:
                                  simple:
                                    expression: "${header.country} == 'DE'"
                                steps:
                                  - log:
                                      message: eu
                """;
        // a when for each branch, as XML and Java write them: in YAML the branches are the list of one when:
        assertThat(lines(yaml)).singleElement().satisfies(m -> assertThat(m)
                .contains("line 13: when: is given twice in this choice: (lines 6 and 13)")
                .contains("when: takes a list, so write when: once and put the - items of both under it"));
    }

    @Test
    public void aKeyGivenThreeTimesNamesBothLostOnes() {
        String yaml = """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - log:
                            message: "one"
                            message: "two"
                            message: "three"
                """;
        List<String> lines = lines(yaml);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).contains("line 7: message: is given twice in this log: (lines 6 and 7)")
                .contains("the message: at line 6 is lost");
        assertThat(lines.get(1)).contains("line 8: message: is given twice in this log: (lines 7 and 8)")
                .contains("the message: at line 7 is lost");
    }

    @Test
    public void theSameKeyInTwoStepsIsNotTwice() {
        String yaml = """
                - route:
                    id: orders
                    from:
                      uri: timer:tick
                      steps:
                        - log:
                            id: first
                            message: "one"
                        - log:
                            id: second
                            message: "two"
                """;
        assertThat(lines(yaml)).isEmpty();
    }

    @Test
    public void reportedBeforeTheSchemaErrors() {
        String yaml = """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - log:
                            message: "new"
                            message: "old"
                        - setBody:
                            constant: done
                            colour: red
                """;
        List<String> lines = lines(yaml);
        assertThat(lines).hasSizeGreaterThan(1);
        assertThat(lines.get(0)).contains("line 7: message: is given twice in this log: (lines 6 and 7)");
        assertThat(lines.get(1)).contains("colour");
    }

    private static List<String> lines(String yaml) {
        try {
            return YamlValidator.describeAll(yaml, validator.validate(yaml));
        } catch (Exception e) {
            throw new AssertionError("Failed to validate:\n" + yaml, e);
        }
    }
}
