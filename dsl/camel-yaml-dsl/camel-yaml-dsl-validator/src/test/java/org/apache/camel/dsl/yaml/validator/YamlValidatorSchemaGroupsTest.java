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

import java.io.File;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * CAMEL-24697: the generated schema must accept what the runtime accepts for oneOf groups with a required alternative,
 * and for the inheritErrorHandler option of circuitBreaker and failoverLoadBalancer.
 */
public class YamlValidatorSchemaGroupsTest {

    private static YamlValidator validator;
    private static JsonNode definitions;

    @BeforeAll
    public static void setup() throws Exception {
        validator = new YamlValidator();
        validator.init();
        try (var is = YamlValidator.class.getResourceAsStream("/schema/camelYamlDsl.json")) {
            definitions = new ObjectMapper().readTree(is).path("items").path("definitions");
        }
    }

    @Test
    public void testExpressionWrapperMatchesOneBranch() throws Exception {
        List<Error> report = validator.validate(new File("src/test/resources/oneof-group-expression-wrapper.yaml"));
        Assertions.assertTrue(report.isEmpty(), "Expected no errors but got: " + messages(report));
    }

    @Test
    public void testRequiredExpressionHasNoNotBranch() {
        // CAMEL-24707: the expression is required on the expression nodes, so their oneOf has no "not" branch (one
        // alternative must match); sort is the one node whose expression is optional and keeps the branch
        for (String name : List.of("org.apache.camel.model.ResequenceDefinition",
                "org.apache.camel.model.PropertyExpressionDefinition", "org.apache.camel.model.SplitDefinition",
                "org.apache.camel.model.SetBodyDefinition", "org.apache.camel.model.WhenDefinition")) {
            JsonNode group = expressionGroup(definitions.path(name));
            Assertions.assertNotNull(group, name + " should have an expression oneOf group");
            for (JsonNode branch : group) {
                Assertions.assertFalse(branch.has("not"), name + " must not have a not branch");
            }
        }
        JsonNode sort = expressionGroup(definitions.path("org.apache.camel.model.SortDefinition"));
        boolean sortHasNot = false;
        for (JsonNode branch : sort) {
            sortHasNot |= branch.has("not");
        }
        Assertions.assertTrue(sortHasNot, "sort keeps the not branch: its expression is optional");
    }

    private static JsonNode expressionGroup(JsonNode definition) {
        for (JsonNode any : definition.path("anyOf")) {
            if (any.has("oneOf")) {
                for (JsonNode branch : any.path("oneOf")) {
                    if (branch.path("required").toString().contains("\"expression\"")) {
                        return any.path("oneOf");
                    }
                }
            }
        }
        return null;
    }

    @Test
    public void testDataFormatGroupStillRequiresADataFormat() throws Exception {
        // marshal has no optional alternative, so it must not gain a "not" branch that would allow marshal: {}
        List<Error> report = validator.validate(new File("src/test/resources/marshal-without-dataformat.yaml"));
        Assertions.assertFalse(report.isEmpty(), "marshal without a data format should not validate");
    }

    @Test
    public void testInheritErrorHandler() throws Exception {
        List<Error> report = validator.validate(new File("src/test/resources/inherit-error-handler.yaml"));
        Assertions.assertTrue(report.isEmpty(), "Expected no errors but got: " + messages(report));
    }

    @Test
    public void testInheritErrorHandlerPlaceholder() throws Exception {
        List<Error> report = validator.validate(new File("src/test/resources/inherit-error-handler-placeholder.yaml"));
        Assertions.assertTrue(report.isEmpty(), "Expected no errors but got: " + messages(report));
    }

    private static List<String> messages(List<Error> report) {
        return report.stream().map(Error::getMessage).toList();
    }
}
