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

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    public void testUnmarshalMarshalWithSingleDataFormatPassesCanonicalValidation() throws Exception {
        // CAMEL-24482: a single data format (e.g. json) on unmarshal/marshal must not require
        // every other data format to also be present.
        var report = canonicalValidator.validate(new File("src/test/resources/canonical-valid-dataformat.yaml"));
        assertThat(report).as("Single data format should pass canonical validation but got: %s", report).isEmpty();
    }

    @Test
    public void testUnmarshalMarshalWithSingleDataFormatPassesClassicValidation() throws Exception {
        var report = classicValidator.validate(new File("src/test/resources/canonical-valid-dataformat.yaml"));
        assertThat(report).as("Single data format should pass classic validation but got: %s", report).isEmpty();
    }

    @Test
    public void testUnmarshalWithoutDataFormatFailsCanonicalValidation() throws Exception {
        // The canonical schema itself has no oneOf/anyOf constructs to express "exactly one of these N
        // properties is required", so YamlValidator re-checks this cardinality itself, driven by the same
        // catalog "oneOf" metadata the classic schema is generated from.
        var report = canonicalValidator.validate(new File("src/test/resources/canonical-invalid-missing-dataformat.yaml"));
        assertThat(report).as("unmarshal without a data format should fail canonical validation").isNotEmpty();
    }

    @Test
    public void testUnmarshalWithoutDataFormatFailsClassicValidation() throws Exception {
        // Unlike canonical mode, the classic schema keeps its oneOf group and still requires exactly
        // one data format to be chosen.
        var report = classicValidator.validate(new File("src/test/resources/canonical-invalid-missing-dataformat.yaml"));
        assertThat(report).as("unmarshal without a data format should fail classic validation").isNotEmpty();
    }

    @Test
    public void testResequenceWithoutBatchOrStreamConfigFailsCanonicalValidation() throws Exception {
        // The "exactly one of" cardinality check is generic, not special-cased to marshal/unmarshal:
        // resequence must pick exactly one of batchConfig/streamConfig too.
        var route = """
                - route:
                    from:
                      uri: "direct:start"
                      steps:
                        - resequence:
                            expression:
                              simple:
                                expression: "${header.seqnum}"
                """;
        var report = canonicalValidator.validate(route);
        assertThat(report).as("resequence without batchConfig/streamConfig should fail canonical validation").isNotEmpty();
    }

    @Test
    public void testLoadBalanceWithTwoLoadBalancersFailsCanonicalValidation() throws Exception {
        var route = """
                - route:
                    from:
                      uri: "direct:start"
                      steps:
                        - loadBalance:
                            roundRobinLoadBalancer: {}
                            randomLoadBalancer: {}
                """;
        var report = canonicalValidator.validate(route);
        assertThat(report).as("loadBalance with two load balancers should fail canonical validation").isNotEmpty();
    }
}
