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
package org.apache.camel.dsl.jbang.core.common;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExampleHelperTest {

    @Test
    void shouldLoadCatalog() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        assertFalse(catalog.isEmpty());
    }

    @Test
    void shouldFindExample() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        JsonObject entry = ExampleHelper.findExample(catalog, "fail-well/circuit-breaker");
        assertNotNull(entry);
        assertEquals("Circuit breaker", entry.getString("title"));
    }

    @Test
    void shouldFindExampleByShortName() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        JsonObject entry = ExampleHelper.findExample(catalog, "circuit-breaker");
        assertNotNull(entry);
        assertEquals("fail-well/circuit-breaker", entry.getString("name"));
    }

    @Test
    void shouldFindExampleByShortNameGroovy() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        JsonObject entry = ExampleHelper.findExample(catalog, "groovy");
        assertNotNull(entry);
        assertEquals("transform/groovy", entry.getString("name"));
    }

    @Test
    void shouldReturnNullForUnknownExample() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        JsonObject entry = ExampleHelper.findExample(catalog, "does-not-exist");
        assertNull(entry);
    }

    @Test
    void shouldFilterByTag() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        List<JsonObject> filtered = ExampleHelper.filterExamples(catalog, "security");
        assertFalse(filtered.isEmpty());
        for (JsonObject entry : filtered) {
            String name = entry.getString("name");
            assertTrue(name.contains("keycloak") || name.contains("pqc") || name.contains("ocsf")
                    || name.contains("pii"),
                    "Expected security-related example but got: " + name);
        }
    }

    @Test
    void shouldFilterByName() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        List<JsonObject> filtered = ExampleHelper.filterExamples(catalog, "mqtt");
        assertEquals(1, filtered.size());
        assertEquals("connect-service/mqtt", filtered.get(0).getString("name"));
    }

    @Test
    void shouldReturnAllWhenFilterEmpty() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        List<JsonObject> filtered = ExampleHelper.filterExamples(catalog, "");
        assertEquals(catalog.size(), filtered.size());
    }

    @Test
    void shouldDetectBundled() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        JsonObject circuitBreaker = ExampleHelper.findExample(catalog, "fail-well/circuit-breaker");
        assertTrue(ExampleHelper.isBundled(circuitBreaker));

        JsonObject mqtt = ExampleHelper.findExample(catalog, "connect-service/mqtt");
        assertFalse(ExampleHelper.isBundled(mqtt));
    }

    @Test
    void shouldDetectDocker() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        JsonObject mqtt = ExampleHelper.findExample(catalog, "connect-service/mqtt");
        assertTrue(ExampleHelper.requiresDocker(mqtt));

        JsonObject circuitBreaker = ExampleHelper.findExample(catalog, "fail-well/circuit-breaker");
        assertFalse(ExampleHelper.requiresDocker(circuitBreaker));
    }

    @Test
    void shouldGetFiles() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        JsonObject routes = ExampleHelper.findExample(catalog, "quick-start/routes");
        List<String> files = ExampleHelper.getFiles(routes);
        assertTrue(files.contains("routes.camel.yaml"));
        assertTrue(files.contains("Greeter.java"));
        assertTrue(files.contains("beans.yaml"));
    }

    @Test
    void shouldExtractBundledExample() throws Exception {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        JsonObject entry = ExampleHelper.findExample(catalog, "fail-well/circuit-breaker");
        Path tempDir = ExampleHelper.extractBundledExample(entry);

        assertTrue(Files.exists(tempDir.resolve("circuit-breaker.camel.yaml")));
        String content = Files.readString(tempDir.resolve("circuit-breaker.camel.yaml"));
        assertFalse(content.isEmpty());
    }

    @Test
    void shouldExtractBundledExampleWithJavaAndBeans() throws Exception {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        JsonObject entry = ExampleHelper.findExample(catalog, "quick-start/routes");
        Path tempDir = ExampleHelper.extractBundledExample(entry);

        assertTrue(Files.exists(tempDir.resolve("routes.camel.yaml")));
        assertTrue(Files.exists(tempDir.resolve("Greeter.java")));
        assertTrue(Files.exists(tempDir.resolve("beans.yaml")));
    }

    @Test
    void shouldGroupByLevelInLadderOrder() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        java.util.Map<String, List<JsonObject>> groups = ExampleHelper.groupByLevel(catalog);
        List<String> levels = new java.util.ArrayList<>(groups.keySet());
        assertEquals("quick-start", levels.get(0));
        assertTrue(levels.indexOf("route") < levels.indexOf("fail-well"));
        assertTrue(levels.indexOf("connect") < levels.indexOf("connect-service"));
        assertEquals("showcase", levels.get(levels.size() - 1));
        assertEquals("Quick start", ExampleHelper.getGroupTitle("quick-start"));
        assertEquals("Connect to one service", ExampleHelper.getGroupTitle("connect-service"));
        assertFalse(ExampleHelper.getGroupIntro("fail-well").isEmpty());
        for (List<JsonObject> entries : groups.values()) {
            assertFalse(entries.isEmpty());
        }
    }

    @Test
    void shouldSummarizeTeachesAndCiSkip() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        JsonObject aggregator = ExampleHelper.findExample(catalog, "route/aggregator");
        String teaches = ExampleHelper.getTeachesSummary(aggregator);
        assertTrue(teaches.contains("eips: "), teaches);
        assertTrue(teaches.contains("aggregate"), teaches);
        assertFalse(ExampleHelper.isCiSkip(aggregator));
        JsonObject chat = ExampleHelper.findExample(catalog, "ai/langchain4j-chat");
        assertTrue(ExampleHelper.isCiSkip(chat));
    }

    @Test
    void shouldFindAmbiguousShortNames() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        assertEquals(1, ExampleHelper.findExamplesByShortName(catalog, "aggregator").size());
        assertTrue(ExampleHelper.findExamplesByShortName(catalog, "no-such-example").isEmpty());
        // two groups with the same short name is the case the ambiguity error in Run is for
        JsonObject a = new JsonObject();
        a.put("name", "group-a/foo");
        JsonObject b = new JsonObject();
        b.put("name", "group-b/foo");
        assertEquals(2, ExampleHelper.findExamplesByShortName(List.of(a, b), "foo").size());
    }

    @Test
    void shouldWrapText() {
        assertTrue(ExampleHelper.wrap("", 40).isEmpty());
        assertTrue(ExampleHelper.wrap(null, 40).isEmpty());
        // the width is never taken below 20, so a narrow terminal still gets readable lines
        assertEquals(List.of("one two three four five six"), ExampleHelper.wrap("one two three four five six", 27));
        assertEquals(List.of("one two three four", "five six"), ExampleHelper.wrap("one two three four five six", 20));
        assertEquals(List.of("one two three four", "five six"), ExampleHelper.wrap("one two three four five six", 5));
        String longWord = "x".repeat(50);
        assertEquals(List.of("a", longWord, "b"), ExampleHelper.wrap("a " + longWord + " b", 20));
        for (String line : ExampleHelper.wrap("the quick brown fox jumps over the lazy dog", 20)) {
            assertTrue(line.length() <= 20, line);
        }
    }

    @Test
    void shouldSkipNonStringTeaches() {
        JsonObject teaches = new JsonObject();
        teaches.put("components", new org.apache.camel.util.json.JsonArray(List.of("timer", 42, "log")));
        teaches.put("eips", "not-a-list");
        JsonObject entry = new JsonObject();
        entry.put("teaches", teaches);
        assertEquals("components: timer, log", ExampleHelper.getTeachesSummary(entry));
    }

    @Test
    void shouldGetGithubUrl() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        JsonObject entry = ExampleHelper.findExample(catalog, "connect-service/mqtt");
        String url = ExampleHelper.getGithubUrl(entry);
        assertEquals("https://github.com/apache/camel-jbang-examples/tree/main/connect-service/mqtt", url);
    }

    @Test
    void shouldGetGithubUrlForNestedExample() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        JsonObject entry = ExampleHelper.findExample(catalog, "cloud/aws-sqs");
        String url = ExampleHelper.getGithubUrl(entry);
        assertEquals("https://github.com/apache/camel-jbang-examples/tree/main/cloud/aws-sqs", url);
    }

    @Test
    void shouldDetectCitrusTests() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        JsonObject mqtt = ExampleHelper.findExample(catalog, "connect-service/mqtt");
        assertTrue(ExampleHelper.hasCitrusTests(mqtt));

        JsonObject circuitBreaker = ExampleHelper.findExample(catalog, "fail-well/circuit-breaker");
        assertTrue(ExampleHelper.hasCitrusTests(circuitBreaker));
    }

    @Test
    void shouldGetExampleNames() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        List<String> names = ExampleHelper.getExampleNames(catalog);
        assertTrue(names.contains("fail-well/circuit-breaker"));
        assertTrue(names.contains("connect-service/mqtt"));
        assertTrue(names.contains("cloud/aws-sqs"));
        // short names should also be included
        assertTrue(names.contains("circuit-breaker"));
        assertTrue(names.contains("mqtt"));
        assertTrue(names.contains("aws-sqs"));
    }
}
