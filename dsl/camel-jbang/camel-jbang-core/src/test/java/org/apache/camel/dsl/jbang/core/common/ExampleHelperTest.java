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
        JsonObject entry = ExampleHelper.findExample(catalog, "circuit-breaker");
        assertNotNull(entry);
        assertEquals("Circuit Breaker", entry.getString("title"));
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
        assertEquals("mqtt", filtered.get(0).getString("name"));
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
        JsonObject circuitBreaker = ExampleHelper.findExample(catalog, "circuit-breaker");
        assertTrue(ExampleHelper.isBundled(circuitBreaker));

        JsonObject mqtt = ExampleHelper.findExample(catalog, "mqtt");
        assertFalse(ExampleHelper.isBundled(mqtt));
    }

    @Test
    void shouldDetectDocker() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        JsonObject mqtt = ExampleHelper.findExample(catalog, "mqtt");
        assertTrue(ExampleHelper.requiresDocker(mqtt));

        JsonObject circuitBreaker = ExampleHelper.findExample(catalog, "circuit-breaker");
        assertFalse(ExampleHelper.requiresDocker(circuitBreaker));
    }

    @Test
    void shouldGetFiles() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        JsonObject routes = ExampleHelper.findExample(catalog, "routes");
        List<String> files = ExampleHelper.getFiles(routes);
        assertTrue(files.contains("routes.camel.yaml"));
        assertTrue(files.contains("Greeter.java"));
        assertTrue(files.contains("beans.yaml"));
    }

    @Test
    void shouldExtractBundledExample() throws Exception {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        JsonObject entry = ExampleHelper.findExample(catalog, "circuit-breaker");
        Path tempDir = ExampleHelper.extractBundledExample(entry);

        assertTrue(Files.exists(tempDir.resolve("route.camel.yaml")));
        String content = Files.readString(tempDir.resolve("route.camel.yaml"));
        assertFalse(content.isEmpty());
    }

    @Test
    void shouldExtractBundledExampleWithSubdirectory() throws Exception {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        JsonObject entry = ExampleHelper.findExample(catalog, "xslt");
        Path tempDir = ExampleHelper.extractBundledExample(entry);

        assertTrue(Files.exists(tempDir.resolve("consumer.camel.yaml")));
        assertTrue(Files.exists(tempDir.resolve("stylesheet.xsl")));
        assertTrue(Files.exists(tempDir.resolve("input/account.xml")));
    }

    @Test
    void shouldGetGithubUrl() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        JsonObject entry = ExampleHelper.findExample(catalog, "mqtt");
        String url = ExampleHelper.getGithubUrl(entry);
        assertEquals("https://github.com/apache/camel-jbang-examples/tree/main/mqtt", url);
    }

    @Test
    void shouldGetGithubUrlForNestedExample() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        JsonObject entry = ExampleHelper.findExample(catalog, "aws/aws-sqs");
        String url = ExampleHelper.getGithubUrl(entry);
        assertEquals("https://github.com/apache/camel-jbang-examples/tree/main/aws/aws-sqs", url);
    }

    @Test
    void shouldGetExampleNames() {
        List<JsonObject> catalog = ExampleHelper.loadCatalog();
        List<String> names = ExampleHelper.getExampleNames(catalog);
        assertTrue(names.contains("circuit-breaker"));
        assertTrue(names.contains("mqtt"));
        assertTrue(names.contains("aws/aws-sqs"));
    }
}
