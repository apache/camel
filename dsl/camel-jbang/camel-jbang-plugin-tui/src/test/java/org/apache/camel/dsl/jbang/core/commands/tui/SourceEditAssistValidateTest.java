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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceEditAssistValidateTest {

    private static SourceEditAssist assist() {
        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of());
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        return new SourceEditAssist(new MonitorContext(data, infraData));
    }

    @Test
    void validRouteHasNoErrors() {
        List<String> errors = assist().validateSource("timer-log.camel.yaml", """
                - route:
                    id: timer-log
                    from:
                      uri: timer:tick
                      steps:
                        - log:
                            message: "${body}"
                            loggingLevel: WARN
                """);

        assertTrue(errors.isEmpty(), String.valueOf(errors));
    }

    @Test
    void misspelledOptionIsReported() {
        List<String> errors = assist().validateSource("timer-log.camel.yaml", """
                - route:
                    id: timer-log
                    from:
                      uri: timer:tick
                      steps:
                        - log:
                            message: "${body}"
                            logLevel: WARN
                """);

        assertFalse(errors.isEmpty(), "logLevel is not an option of the log EIP");
        assertTrue(errors.stream().anyMatch(e -> e.contains("logLevel")), String.valueOf(errors));
    }

    @Test
    void brokenYamlIsReported() {
        List<String> errors = assist().validateCamelYaml("- route:\n  from: [unclosed\n");

        assertFalse(errors.isEmpty());
        assertTrue(assist().validateCamelYaml("").isEmpty());
    }

    @Test
    void otherFileTypesAreNotValidated() {
        assertTrue(assist().validateSource("README.md", "# whatever").isEmpty());
        assertTrue(SourceEditAssist.isValidatableFile("application.properties"));
        assertTrue(SourceEditAssist.isValidatableFile("routes.YAML"));
        assertFalse(SourceEditAssist.isValidatableFile("Foo.java"));
    }
}
