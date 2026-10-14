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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The example tools of the shared registry (list_examples, get_example_file) as an AI agent calls them (CAMEL-25535).
 */
class ExampleToolsRegistryTest {

    @Test
    void readsOneFileOfEveryBundledExampleByItsShortName() throws Exception {
        List<JsonObject> catalog = rawCatalog();
        List<String> bundled = new ArrayList<>();
        for (JsonObject entry : catalog) {
            String name = entry.getString("name");
            String shortName = name.substring(name.indexOf('/') + 1);
            List<String> files = entry.getCollection("files");
            assertThat(files).as("files of %s", name).isNotEmpty();
            // a route file when there is one, the file an agent asks for first
            String file = files.stream().filter(f -> f.contains(".camel.")).findFirst().orElse(files.get(0));

            JsonObject answer = exampleFile(shortName, file);

            assertThat(answer.getString("example")).as("example of %s", shortName).isEqualTo(name);
            assertThat(answer.getString("file")).isEqualTo(file);
            if (entry.getBooleanOrDefault("bundled", false)) {
                bundled.add(name);
                assertThat(answer.getString("content")).as("content of %s/%s", name, file)
                        .isEqualTo(resource("examples/" + name + "/" + file));
                assertThat(answer.getString("githubUrl")).isNull();
            } else {
                assertThat(answer.getString("content")).isNull();
                assertThat(answer.getString("githubUrl")).as("githubUrl of %s", name).endsWith("/" + file);
            }
        }
        assertThat(bundled).as("bundled examples").contains("quick-start/timer-log").hasSizeGreaterThan(10);
    }

    @Test
    void anUnknownExampleListsTheClosestNames() {
        // the group of 4.22 (beginner), a typo, and a name that is nowhere near an example
        assertThatThrownBy(() -> exampleFile("beginner/timer-log", "timer-log.camel.yaml"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Example not found: beginner/timer-log")
                .hasMessageContaining("Did you mean: ")
                .hasMessageContaining("quick-start/timer-log");
        assertThatThrownBy(() -> exampleFile("timer-lgo", "timer-log.camel.yaml"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("quick-start/timer-log");
        assertThatThrownBy(() -> exampleFile("zzzzzzzzzzzz", "x.yaml"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Example not found: zzzzzzzzzzzz")
                .hasMessageContaining("list_examples")
                .hasMessageNotContaining("Did you mean");
    }

    @Test
    void anUnknownFileListsTheFilesOfTheExample() {
        assertThatThrownBy(() -> exampleFile("timer-log", "route.camel.yaml"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("File 'route.camel.yaml' not found in example 'quick-start/timer-log'")
                .hasMessageContaining("timer-log.camel.yaml");
    }

    @Test
    void anUnknownLevelSaysWhichLevelsExist() {
        assertThatThrownBy(() -> ToolRegistry.execute("list_examples", new ToolContext(), Map.of("level", "beginner")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("No example at level beginner")
                .hasMessageContaining("quick-start")
                .hasMessageContaining("showcase");

        Object fine = ToolRegistry.execute("list_examples", new ToolContext(), Map.of("level", "Quick-Start"));
        assertThat(fine.toString()).contains("\"level\":\"quick-start\"");
    }

    @Test
    void theArgumentDescriptionsNameAnExampleAndAFileThatExist() {
        ToolDescriptor tool = ToolRegistry.findTool("get_example_file");
        assertThat(tool).isNotNull();
        assertThat(ToolRegistry.EXAMPLE_NAME_DESC).contains("quick-start/timer-log");
        assertThat(ToolRegistry.EXAMPLE_FILE_DESC).contains("timer-log.camel.yaml");
        JsonObject answer = exampleFile("quick-start/timer-log", "timer-log.camel.yaml");
        assertThat(answer.getString("example")).isEqualTo("quick-start/timer-log");
        assertThat(answer.getString("content")).contains("uri: timer");
    }

    private static JsonObject exampleFile(String example, String file) {
        Object result = ToolRegistry.execute("get_example_file", new ToolContext(),
                Map.of("example", example, "file", file));
        try {
            return (JsonObject) Jsoner.deserialize(result.toString());
        } catch (Exception e) {
            throw new AssertionError("not a JSON object: " + result, e);
        }
    }

    private static List<JsonObject> rawCatalog() throws Exception {
        JsonArray array = (JsonArray) Jsoner.deserialize(resource("examples/camel-jbang-example-catalog.json"));
        List<JsonObject> catalog = new ArrayList<>();
        for (Object o : array) {
            catalog.add((JsonObject) o);
        }
        assertThat(catalog).isNotEmpty();
        return catalog;
    }

    private static String resource(String path) throws Exception {
        try (InputStream is = ExampleToolsRegistryTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(is).as("resource %s", path).isNotNull();
            return IOHelper.loadText(is);
        }
    }
}
