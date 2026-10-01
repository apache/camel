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

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The schema errors of a YAML route are reported on their line, as the other checks are, so the TUI marks them on it.
 */
class YamlPointerLinesTest {

    private static final String YAML = """
            - route:
                from:
                  uri: "timer:tick"
                  steps:
                    - filter:
                        expression:
                          simple:
                            expression: "${header.foo} == 'bar'"
                        steps:
                          - log:
                              message: "${body}"
                              logLevel: WARN
            """;

    @Test
    void thePointerOfANestedStep() {
        var root = YamlPointerLines.root(YAML);
        assertThat(YamlPointerLines.line(root, "/0/route/from/uri", null)).isEqualTo(3);
        assertThat(YamlPointerLines.line(root, "/0/route/from/steps/0/filter/steps/0/log", null)).isEqualTo(11);
        // an unknown property: the line of its key
        assertThat(YamlPointerLines.line(root, "/0/route/from/steps/0/filter/steps/0/log",
                "property 'logLevel' is not defined in the schema")).isEqualTo(12);
        assertThat(YamlPointerLines.line(root, "/0/route/nope", null)).isZero();
        assertThat(YamlPointerLines.line(YamlPointerLines.root("- route: [unclosed"), "/0", null)).isZero();
    }

    @Test
    void aMisspelledOptionIsReportedOnItsLine() {
        List<String> errors = SourceValidator.validate("route.camel.yaml", YAML, new DefaultCamelCatalog(), null, null);
        assertThat(errors).anySatisfy(e -> assertThat(e).startsWith("Line 12: ").contains("logLevel"));
    }
}
