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
    public void testRequiredAlternativeIsExcludedFromNotBranch() {
        // resequence and setHeaders entries have a required expression; with the "expression:" wrapper the
        // "not" branch of the group must not match as well, so it has to list "expression" too
        for (String name : List.of("org.apache.camel.model.ResequenceDefinition",
                "org.apache.camel.model.PropertyExpressionDefinition")) {
            JsonNode group = definitions.path(name).path("anyOf").get(0).path("oneOf");
            JsonNode not = null;
            for (JsonNode branch : group) {
                if (branch.has("not")) {
                    not = branch.path("not").path("anyOf");
                }
            }
            Assertions.assertNotNull(not, name + " should have a not branch");
            boolean excluded = false;
            for (JsonNode entry : not) {
                if (entry.path("required").toString().contains("\"expression\"")) {
                    excluded = true;
                }
            }
            Assertions.assertTrue(excluded, name + " not branch should exclude expression");
        }
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

    private static List<String> messages(List<Error> report) {
        return report.stream().map(Error::getMessage).toList();
    }
}
