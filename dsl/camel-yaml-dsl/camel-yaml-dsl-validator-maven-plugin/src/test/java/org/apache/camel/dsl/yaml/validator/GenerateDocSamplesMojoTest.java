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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerateDocSamplesMojoTest {

    private static final String ROUTE = """
            - route:
                from:
                  uri: timer:yaml
                  steps:
                    - log: "${body}"
            """;

    private static final YamlValidator VALIDATOR = new YamlValidator();

    @TempDir
    Path tempDir;

    @BeforeAll
    static void init() throws Exception {
        VALIDATOR.init();
    }

    @Test
    void eipKeyIsThePageNameWithoutSuffixInCamelCase() {
        assertEquals("split", GenerateDocSamplesMojo.eipKey("split-eip.adoc"));
        assertEquals("circuitBreaker", GenerateDocSamplesMojo.eipKey("circuitBreaker-eip.adoc"));
        assertEquals("contentFilter", GenerateDocSamplesMojo.eipKey("content-filter-eip.adoc"));
        assertEquals("deadLetterChannel", GenerateDocSamplesMojo.eipKey("dead-letter-channel.adoc"));
        assertEquals("keyValueRepository", GenerateDocSamplesMojo.eipKey("keyValueRepository.adoc"));
    }

    @Test
    void examplesAreTheBlocksStartingWithAListEntryWithoutCallouts() throws Exception {
        Path page = tempDir.resolve("page.adoc");
        Files.writeString(page, """
                Text.

                [source,yaml]
                ----
                - route: # <1>
                    from:
                      uri: timer:yaml
                      steps:
                        - log: "${body}"
                ----

                <1> the route

                A fragment:

                [source,yaml]
                ----
                steps:
                  - log: "${body}"
                ----
                """);
        List<String> examples = GenerateDocSamplesMojo.examples(page.toFile());
        assertEquals(1, examples.size());
        assertEquals(ROUTE, examples.get(0));
    }

    @Test
    void samplesAreKeyedByEipPageUserManualPageAndEntry() throws Exception {
        Path eips = Files.createDirectories(tempDir.resolve("eips"));
        Files.writeString(eips.resolve("log-eip.adoc"), block(ROUTE));
        Files.writeString(eips.resolve("dead-letter-channel.adoc"), block(ROUTE) + block(ROUTE));
        Files.writeString(eips.resolve("enterprise-integration-patterns.adoc"), "No examples here.\n");

        Path manual = Files.createDirectories(tempDir.resolve("manual"));
        Files.writeString(manual.resolve("routes.adoc"), block(ROUTE));
        Files.writeString(manual.resolve("variables.adoc"), block(ROUTE));
        Files.writeString(manual.resolve("camel-4x-upgrade-guide-4_1.adoc"), block("- route:\n    steps: []\n"));

        Path yamlDsl = tempDir.resolve("yaml-dsl.adoc");
        Files.writeString(yamlDsl, block("- beans:\n    - name: myBean\n      type: com.acme.MyBean\n") + block(ROUTE));

        List<String> failures = new ArrayList<>();
        Map<String, List<GenerateDocSamplesMojo.Sample>> samples = GenerateDocSamplesMojo.generate(
                VALIDATOR, eips.toFile(), manual.toFile(), Map.of("routes", "route"),
                Map.of(yamlDsl.toFile(), "beans"), List.of("camel-*upgrade-guide*.adoc"), failures);

        assertTrue(failures.isEmpty(), failures.toString());
        assertEquals(List.of("beans", "deadLetterChannel", "log", "route"), new ArrayList<>(samples.keySet()));
        assertEquals(2, samples.get("deadLetterChannel").size());
        assertEquals("dead-letter-channel.adoc", samples.get("deadLetterChannel").get(0).source());
        assertEquals(1, samples.get("route").size());
        assertEquals("routes.adoc", samples.get("route").get(0).source());
        assertEquals(1, samples.get("beans").size());
        assertTrue(samples.get("beans").get(0).yaml().startsWith("- beans:"));

        JsonObject json = (JsonObject) Jsoner.deserialize(GenerateDocSamplesMojo.toJson(samples));
        JsonObject first = (JsonObject) ((JsonArray) json.get("log")).get(0);
        assertEquals("log-eip.adoc", first.getString("source"));
        assertEquals(ROUTE, first.getString("yaml"));
    }

    @Test
    void anExampleThatDoesNotValidateIsAFailure() throws Exception {
        Path eips = Files.createDirectories(tempDir.resolve("eips"));
        Path manual = Files.createDirectories(tempDir.resolve("manual"));
        Files.writeString(manual.resolve("routes.adoc"), block(ROUTE) + block("- route:\n    steps: []\n"));

        List<String> failures = new ArrayList<>();
        Map<String, List<GenerateDocSamplesMojo.Sample>> samples = GenerateDocSamplesMojo.generate(
                VALIDATOR, eips.toFile(), manual.toFile(), Map.of("routes", "route"), Map.of(), null, failures);

        assertEquals(1, failures.size());
        assertTrue(failures.get(0).startsWith("routes.adoc: "), failures.get(0));
        assertEquals(1, samples.get("route").size());
    }

    private static String block(String yaml) {
        return "[source,yaml]\n----\n" + yaml + "----\n\n";
    }
}
