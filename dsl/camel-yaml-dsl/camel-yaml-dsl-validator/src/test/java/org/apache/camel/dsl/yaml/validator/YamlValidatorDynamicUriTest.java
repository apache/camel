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
 * CAMEL-24917: to: with an expression in the uri path sends the text as it stands, url-encoded, and every call fails at
 * runtime. The validator says to write toD: instead.
 */
public class YamlValidatorDynamicUriTest {

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
    public void testToWithAnExpressionInThePath() {
        String yaml = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - to:
                            uri: "http://localhost:8080/stock/${header.sku}"
                """;
        assertHint(yaml, "${header.sku}", "write toD:");
    }

    @Test
    public void testToInItsShortForm() {
        // canonical mode has its own word about the short form, so the hint is the classic mode's
        String yaml = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - to: "http://localhost:8080/stock/${header.sku}"
                """;
        List<Error> errors = validate(classic, yaml);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("${header.sku}"))
                .anyMatch(e -> e.getMessage().contains("write toD:"));
    }

    @Test
    public void testToDIsWhatToWrite() {
        String yaml = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - toD:
                            uri: "http://localhost:8080/stock/${header.sku}"
                """;
        assertNoHint(yaml);
    }

    @Test
    public void testAnOptionThatTheProducerEvaluatesIsFine() {
        // the file component evaluates fileName per message, so this is correct on a plain to:
        String yaml = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - to:
                            uri: "file:out?fileName=${date:now:yyyyMMdd}.txt"
                """;
        assertNoHint(yaml);
    }

    @Test
    public void testASqlParameterIsFine() {
        // the sql component binds :#${...} per message; this is what the user manual shows
        String yaml = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - to:
                            uri: "sql:SELECT * FROM inventory WHERE id = :#${body.itemId}"
                """;
        assertNoHint(yaml);
    }

    @Test
    public void testALanguageScriptIsFine() {
        // the language component's path is the script it evaluates
        String yaml = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - to:
                            uri: "language:simple:Hello ${body}"
                """;
        assertNoHint(yaml);
    }

    @Test
    public void testAComponentWhosePathIsEvaluatedIsFine() {
        // CAMEL-24918: micrometer evaluates the metric name for each message, and its catalog metadata says so
        String yaml = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - to:
                            uri: "micrometer:counter:orders.${header.region}"
                """;
        assertNoHint(yaml);
    }

    @Test
    public void testAComponentWhosePathIsAnAddressIsReported() {
        // xslt takes a resource name, not an expression: a stylesheet per message needs toD
        String yaml = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - to:
                            uri: "xslt:styles/${header.style}.xsl"
                """;
        assertHint(yaml, "${header.style}", "write toD:");
    }

    @Test
    public void testAPropertyPlaceholderIsFine() {
        String yaml = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - to:
                            uri: "http://{{stock.host}}/stock"
                """;
        assertNoHint(yaml);
    }

    private void assertHint(String yaml, String... expectedInMessage) {
        for (YamlValidator validator : List.of(classic, canonical)) {
            String mode = validator.isCanonical() ? "canonical" : "classic";
            List<Error> errors = validate(validator, yaml);
            assertThat(errors).as("%s mode must report the expression:\n%s", mode, yaml).isNotEmpty();
            for (String expected : expectedInMessage) {
                assertThat(errors).as("%s mode must say '%s'", mode, expected)
                        .anyMatch(e -> e.getMessage().contains(expected));
            }
        }
    }

    private void assertNoHint(String yaml) {
        for (YamlValidator validator : List.of(classic, canonical)) {
            assertThat(validate(validator, yaml))
                    .as("%s mode must accept:\n%s", validator.isCanonical() ? "canonical" : "classic", yaml)
                    .noneMatch(e -> e.getMessage().contains("write toD:"));
        }
    }

    private List<Error> validate(YamlValidator validator, String yaml) {
        try {
            return validator.validate(yaml);
        } catch (Exception e) {
            throw new AssertionError("Failed to validate:\n" + yaml, e);
        }
    }
}
