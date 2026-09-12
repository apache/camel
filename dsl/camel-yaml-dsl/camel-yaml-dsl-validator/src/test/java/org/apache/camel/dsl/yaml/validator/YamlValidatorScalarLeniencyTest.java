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

import com.networknt.schema.Error;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24694: the generated schema types scalar attributes from the model metadata, but the model fields are String
 * and the runtime converts (and resolves property placeholders) when the route starts. The validator must therefore
 * accept the string forms the runtime accepts, in both classic and canonical mode, while staying strict about values
 * the runtime would genuinely reject.
 */
public class YamlValidatorScalarLeniencyTest {

    private static YamlValidator classic;
    private static YamlValidator canonical;

    @BeforeAll
    public static void setup() throws Exception {
        classic = new YamlValidator();
        classic.init();
        canonical = new YamlValidator(true);
        canonical.init();
    }

    @Test
    public void testQuotedBooleanAccepted() {
        assertAccepted("split", "parallelProcessing: \"true\"");
    }

    @Test
    public void testNativeBooleanStillAccepted() {
        assertAccepted("split", "parallelProcessing: true");
    }

    @Test
    public void testPlaceholderAtBooleanAccepted() {
        assertAccepted("split", "parallelProcessing: \"{{myParallel}}\"");
    }

    @Test
    public void testQuotedIntegerAccepted() {
        assertAccepted("split", "group: \"100\"");
    }

    @Test
    public void testPlaceholderAtIntegerAccepted() {
        assertAccepted("split", "group: \"{{myGroup}}\"");
    }

    @Test
    public void testQuotedNumberAccepted() {
        assertAccepted("split", "errorThreshold: \"0.5\"");
    }

    @Test
    public void testIntegerAtDurationAccepted() {
        // timeout is a duration, which the schema emits as string - the runtime converts any scalar to text
        assertAccepted("split", "timeout: 5000");
    }

    @Test
    public void testPlaceholderAtDurationAccepted() {
        assertAccepted("split", "timeout: \"{{myTimeout}}\"");
    }

    @Test
    public void testUnparsableStringAtBooleanStillRejected() {
        assertRejected("split", "parallelProcessing: \"yes please\"", "boolean expected");
    }

    @Test
    public void testUnparsableStringAtIntegerStillRejected() {
        assertRejected("split", "group: \"a lot\"", "number expected");
    }

    @Test
    public void testUnknownPropertyStillRejected() {
        assertRejected("split", "cheese: true", "cheese");
    }

    private void assertAccepted(String eip, String attribute) {
        for (YamlValidator validator : List.of(classic, canonical)) {
            String mode = validator.isCanonical() ? "canonical" : "classic";
            assertThat(validate(validator, eip, attribute))
                    .as("%s: '%s' is accepted by the runtime and must validate in %s mode", eip, attribute, mode)
                    .isEmpty();
        }
    }

    private void assertRejected(String eip, String attribute, String expectedInMessage) {
        for (YamlValidator validator : List.of(classic, canonical)) {
            String mode = validator.isCanonical() ? "canonical" : "classic";
            assertThat(validate(validator, eip, attribute))
                    .as("%s: '%s' must still be rejected in %s mode", eip, attribute, mode)
                    .isNotEmpty()
                    .anyMatch(e -> e.getMessage().contains(expectedInMessage));
        }
    }

    private List<Error> validate(YamlValidator validator, String eip, String attribute) {
        String yaml = """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - %s:
                            %s
                            steps:
                              - log:
                                  message: "${body}"
                """.formatted(eip, attribute);
        try {
            return validator.validate(yaml);
        } catch (Exception e) {
            throw new AssertionError("Failed to validate:\n" + yaml, e);
        }
    }
}
