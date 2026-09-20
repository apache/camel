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
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GroovyImportChecksTest {

    private static final String YAML = """
            - route:
                from:
                  uri: "timer:t?repeatCount=1"
                  steps:
                    - choice:
                        when:
                          - expression:
                              groovy: |
                                import org.apache.commons.validator.routines.EmailValidator
                                import java.util.Locale
                                import com.example.Unknown
                                import org.apache.camel.Exchange
                                import com.example.MyBean
                                EmailValidator.getInstance().isValid(body.email)
                            steps:
                              - log: "ok"
            """;

    @Test
    void cliDownloadsAKnownLibraryAndReportsAnUnknownOne() {
        List<String> errors = GroovyImportChecks.validateYamlGroovyImports(YAML, null, Map.of("MyBean", "com.example.MyBean"));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).startsWith("Line 11: the Groovy import com.example.Unknown is not a class of the JDK")
                .contains("camel.jbang.dependencies=groupId:artifactId:version");
    }

    @Test
    void mavenRuntimeNamesTheKnownLibrary() {
        List<String> errors
                = GroovyImportChecks.validateYamlGroovyImports(YAML, "spring-boot", Map.of("MyBean", "com.example.MyBean"));
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0)).startsWith(
                "Line 9: the Groovy import org.apache.commons.validator.routines.EmailValidator is a class of commons-validator:commons-validator:")
                .contains("<artifactId>commons-validator</artifactId>");
        assertThat(errors.get(1)).startsWith("Line 11: ");
    }

    @Test
    void canonicalFormWithExpressionBlock() {
        // groovy: on its own line and the script under expression: | (what camel validate normalize writes)
        String yaml = """
                - route:
                    from:
                      uri: "timer:t?repeatCount=1"
                      steps:
                        - setBody:
                            expression:
                              groovy:
                                expression: |
                                  import com.example.Unknown
                                  import org.apache.commons.validator.routines.EmailValidator
                                  Unknown.of(body)
                """;
        List<String> errors = GroovyImportChecks.validateYamlGroovyImports(yaml, null, Map.of());
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).startsWith("Line 9: the Groovy import com.example.Unknown");
        assertThat(GroovyImportChecks.validateYamlGroovyImports(yaml, "main", Map.of())).hasSize(2);
    }

    @Test
    void inlineExpressionAndNoImports() {
        String yaml = """
                - route:
                    from:
                      uri: "timer:t?repeatCount=1"
                      steps:
                        - setBody:
                            groovy: "body.toUpperCase()"
                """;
        assertThat(GroovyImportChecks.validateYamlGroovyImports(yaml, null, Map.of())).isEmpty();
    }
}
