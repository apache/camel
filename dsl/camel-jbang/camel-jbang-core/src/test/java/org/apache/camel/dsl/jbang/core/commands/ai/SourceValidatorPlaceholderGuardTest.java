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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-24692: a placeholder used as a value is validated (the catalog substitutes it), a placeholder used as an
 * operand of a logical operator is not (it can expand to a whole predicate the catalog cannot know).
 */
class SourceValidatorPlaceholderGuardTest {

    private static final CamelCatalog CATALOG = new DefaultCamelCatalog();

    @Test
    void placeholderAsValueIsValidated() {
        assertFalse(SimpleChecks.hasPlaceholderAsLogicalOperand("{{hot.threshold}}"));
        assertFalse(SimpleChecks.hasPlaceholderAsLogicalOperand("${body} >= {{hot.threshold}}"));
        assertFalse(SimpleChecks.hasPlaceholderAsLogicalOperand("{{a}} == {{b}}"));
        assertFalse(SimpleChecks.hasPlaceholderAsLogicalOperand("${body} >= {{t}} && ${body} < {{u}}"));
        assertFalse(SimpleChecks.hasPlaceholderAsLogicalOperand("${body} == 'abc'"));
        assertFalse(SimpleChecks.hasPlaceholderAsLogicalOperand(null));
    }

    @Test
    void placeholderAsLogicalOperandIsSkipped() {
        assertTrue(SimpleChecks.hasPlaceholderAsLogicalOperand("{{a}} && {{b}}"));
        assertTrue(SimpleChecks.hasPlaceholderAsLogicalOperand("${body} > 1 && {{flag}}"));
        assertTrue(SimpleChecks.hasPlaceholderAsLogicalOperand("{{flag}} || ${body} > 1"));
        // inside a quoted literal it is text
        assertFalse(SimpleChecks.hasPlaceholderAsLogicalOperand("${body} == '{{a}} && {{b}}'"));
    }

    @Test
    void placeholderPredicatesAreNotReportedAsErrors() {
        String yaml = """
                - from:
                    uri: timer:tick
                    steps:
                      - filter:
                          simple: "${body} >= {{hot.threshold}}"
                          steps:
                            - log: hot
                      - filter:
                          simple: "{{flag}} && ${body} > 1"
                          steps:
                            - log: flagged
                      - filter:
                          simple: "${body"
                          steps:
                            - log: broken
                """;
        List<String> errors = SourceValidator.validateYamlSimple(yaml, CATALOG);
        assertTrue(errors.stream().noneMatch(e -> e.contains("hot.threshold") || e.contains("flag")), String.valueOf(errors));
        assertTrue(errors.stream().anyMatch(e -> e.contains("Line 13")), String.valueOf(errors));
    }
}
