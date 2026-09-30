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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24921: what the runtime accepts, the tools that write files must accept too - a route with a predicate inside
 * the braces is not refused before it ever runs.
 */
class PredicateInBracesValidatorTest {

    private static final String ROUTE = """
            - route:
                from:
                  uri: direct:start
                  steps:
                    - choice:
                        when:
                          - expression:
                              simple: "${body != null && body.size() > 0}"
                            steps:
                              - log:
                                  message: "some"
                        otherwise:
                          steps:
                            - log:
                                message: "none"
            """;

    @Test
    void aPredicateInsideTheBracesValidates() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        List<String> problems = SourceValidator.validateCamelYaml(ROUTE, catalog);
        assertThat(problems).as("the runtime accepts this route, so the validator must too").isEmpty();
    }
}
