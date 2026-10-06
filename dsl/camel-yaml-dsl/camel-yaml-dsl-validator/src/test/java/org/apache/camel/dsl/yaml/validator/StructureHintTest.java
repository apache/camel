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
                .contains("parameters: is at the column of - to: as an option of to it is indented under to:"));
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

    private static List<String> lines(String yaml) {
        try {
            return YamlValidator.describeAll(yaml, validator.validate(yaml));
        } catch (Exception e) {
            throw new AssertionError("Failed to validate:\n" + yaml, e);
        }
    }
}
