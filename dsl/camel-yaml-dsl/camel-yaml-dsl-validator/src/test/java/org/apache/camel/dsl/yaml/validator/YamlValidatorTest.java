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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class YamlValidatorTest {

    private static YamlValidator validator;

    @BeforeAll
    public static void setup() throws Exception {
        validator = new YamlValidator();
        validator.init();
    }

    @Test
    public void testValidateOk() throws Exception {
        Assertions.assertTrue(validator.validate(new File("src/test/resources/foo.yaml")).isEmpty());
    }

    @Test
    public void testValidateOkPlaceholder() throws Exception {
        Assertions.assertTrue(validator.validate(new File("src/test/resources/foo2.yaml")).isEmpty());
    }

    @Test
    public void testValidateBad() throws Exception {
        var report = validator.validate(new File("src/test/resources/bad.yaml"));
        Assertions.assertFalse(report.isEmpty());
        Assertions.assertEquals(1, report.size());
        Assertions.assertTrue(report.get(0).getMessage().contains("setCheese"));
    }

    @Test
    public void testValidateBadPlaceholder() throws Exception {
        var report = validator.validate(new File("src/test/resources/bad2.yaml"));
        Assertions.assertFalse(report.isEmpty());
        Assertions.assertEquals(1, report.size());
        Assertions.assertTrue(report.get(0).getMessage().contains("setCheese"));
    }

    @Test
    public void testTypeMismatchFiltersOneOfNoise() throws Exception {
        var report = validator.validate(new File("src/test/resources/type-mismatch.yaml"));
        // should filter dozens of "required property 'X' not found" noise down to the real error
        Assertions.assertTrue(report.size() <= 3, "Expected at most 3 errors but got " + report.size());
        Assertions.assertTrue(report.stream().anyMatch(e -> e.getMessage().contains("integer found, boolean expected")),
                "Should contain the actual type error");
        Assertions.assertTrue(report.stream().noneMatch(e -> e.getMessage().contains("required property")),
                "Should not contain required property noise from oneOf branches");
    }

    @Test
    public void testUnknownEipOptionShowsPropertyError() throws Exception {
        var report = validator.validate(new File("src/test/resources/unknown-eip-option.yaml"));
        Assertions.assertFalse(report.isEmpty());
        // should show "cheese" as the unknown property, not "object found, string expected"
        Assertions.assertTrue(report.stream().anyMatch(e -> e.getMessage().contains("cheese")),
                "Should identify the unknown property 'cheese', got: " + report.stream().map(e -> e.getMessage()).toList());
        Assertions.assertTrue(report.stream().noneMatch(e -> e.getMessage().contains("string expected")),
                "Should not show misleading 'string expected' from the wrong oneOf branch");
    }

    @Test
    public void testValidateRuntimeCustomStepRejectedBySchema() throws Exception {
        var report = validator.validate(new File("src/test/resources/custom-parser-step.yaml"));
        Assertions.assertFalse(report.isEmpty());
        Assertions.assertTrue(report.stream().anyMatch(error -> error.getMessage().contains("parserStep")));
    }

    @Test
    public void testInlineExpressionObjectFormStillValidates() throws Exception {
        // Guards the fix for CAMEL-24479: restoring additionalProperties:false on ExpressionDefinition
        // must not break the inline shorthand merge (filter: { groovy: {...} } without an "expression:"
        // wrapper), which relies on ExpressionDefinition's properties being redeclared onto FilterDefinition.
        var report = validator.validate(new File("src/test/resources/inline-expression-object.yaml"));
        Assertions.assertTrue(report.isEmpty(), "Inline expression object form should still pass validation but got: "
                                                + report.stream().map(e -> e.getMessage()).toList());
    }

    @Test
    public void testUnknownPropertyInExpressionBlockFailsValidation() throws Exception {
        // CAMEL-24479: an explicit "expression: {...}" wrapper block with an unknown property (hello) must
        // be rejected. Before the fix, ExpressionDefinition's schema had lost its additionalProperties:false
        // as a side effect of also supporting inline expression shorthands (e.g. filter: { simple: "..." }),
        // so unknown/duplicate properties were silently accepted whenever any single valid language was present.
        var report = validator.validate(new File("src/test/resources/CAMEL-24479.yaml"));
        Assertions.assertFalse(report.isEmpty(), "Unknown property 'hello' in the expression block should be reported");
        Assertions.assertTrue(report.stream().anyMatch(e -> e.getMessage().contains("hello")),
                "Should identify the unknown property 'hello', got: " + report.stream().map(e -> e.getMessage()).toList());
    }
}
