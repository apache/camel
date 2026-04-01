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

public class YamlCanonicalValidatorTest {

    private static YamlValidator canonicalValidator;
    private static YamlValidator classicValidator;

    @BeforeAll
    public static void setup() throws Exception {
        canonicalValidator = new YamlValidator(true);
        canonicalValidator.init();
        classicValidator = new YamlValidator();
        classicValidator.init();
    }

    @Test
    public void testExplicitFormPassesCanonicalValidation() throws Exception {
        // Explicit form (no shorthands, expression wrapper used) should pass canonical validation
        var report = canonicalValidator.validate(new File("src/test/resources/canonical-valid.yaml"));
        Assertions.assertTrue(report.isEmpty(),
                "Explicit form should pass canonical validation but got: " + report);
    }

    @Test
    public void testExplicitFormPassesClassicValidation() throws Exception {
        // Explicit form should also pass classic validation
        var report = classicValidator.validate(new File("src/test/resources/canonical-valid.yaml"));
        Assertions.assertTrue(report.isEmpty(),
                "Explicit form should pass classic validation but got: " + report);
    }

    @Test
    public void testLogStringShorthandFailsCanonicalValidation() throws Exception {
        // log: "${body}" is a string shorthand, not allowed in canonical mode
        var report = canonicalValidator.validate(new File("src/test/resources/canonical-invalid-log-shorthand.yaml"));
        Assertions.assertFalse(report.isEmpty(),
                "Log string shorthand should fail canonical validation");
    }

    @Test
    public void testLogStringShorthandPassesClassicValidation() throws Exception {
        // log: "${body}" should pass classic validation (string shorthand is allowed)
        var report = classicValidator.validate(new File("src/test/resources/canonical-invalid-log-shorthand.yaml"));
        Assertions.assertTrue(report.isEmpty(),
                "Log string shorthand should pass classic validation but got: " + report);
    }

    @Test
    public void testInlineExpressionFailsCanonicalValidation() throws Exception {
        // setBody: { simple: "..." } uses inline expression (no expression wrapper), not allowed in canonical mode
        var report = canonicalValidator.validate(new File("src/test/resources/canonical-invalid-inline-expression.yaml"));
        Assertions.assertFalse(report.isEmpty(),
                "Inline expression should fail canonical validation");
    }

    @Test
    public void testInlineExpressionPassesClassicValidation() throws Exception {
        // setBody: { simple: "..." } should pass classic validation (inline expression is allowed)
        var report = classicValidator.validate(new File("src/test/resources/canonical-invalid-inline-expression.yaml"));
        Assertions.assertTrue(report.isEmpty(),
                "Inline expression should pass classic validation but got: " + report);
    }

    @Test
    public void testClassicValidFilesPassCanonicalWhenExplicit() throws Exception {
        // foo.yaml uses implicit forms (log: "${body}", setBody: { simple: ... })
        // It should still pass classic validation
        var report = classicValidator.validate(new File("src/test/resources/foo.yaml"));
        Assertions.assertTrue(report.isEmpty(),
                "foo.yaml should pass classic validation but got: " + report);

        // foo.yaml uses implicit forms, so it should fail canonical validation
        var canonicalReport = canonicalValidator.validate(new File("src/test/resources/foo.yaml"));
        Assertions.assertFalse(canonicalReport.isEmpty(),
                "foo.yaml uses implicit forms and should fail canonical validation");
    }
}
