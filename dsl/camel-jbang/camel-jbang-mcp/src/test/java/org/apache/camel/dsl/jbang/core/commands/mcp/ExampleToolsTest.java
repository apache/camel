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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.Comparator;
import java.util.List;

import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.dsl.jbang.core.common.ExampleHelper;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExampleToolsTest {

    @Test
    void listsTheWholeLadderInReadingOrderWithoutArguments() {
        JsonObject all = new ExampleTools().camel_catalog_examples(null, null, null);

        assertThat(all.getInteger("count")).isEqualTo(all.getInteger("total"));
        List<JsonObject> groups = objects(all, "groups");
        assertThat(groups).isNotEmpty();
        List<String> levels = groups.stream().map(g -> g.getString("level")).toList();
        assertThat(levels).isSubsetOf(ExampleHelper.getGroupOrder());
        assertThat(levels).isSortedAccordingTo((a, b) -> Integer.compare(
                ExampleHelper.getGroupOrder().indexOf(a), ExampleHelper.getGroupOrder().indexOf(b)));
        assertThat(levels.get(0)).isEqualTo("quick-start");
        JsonObject first = groups.get(0);
        assertThat(first.getString("title")).isEqualTo(ExampleHelper.getGroupTitle("quick-start"));
        assertThat(first.getString("intro")).isNotBlank();

        // the examples follow the groups in order
        List<JsonObject> examples = objects(all, "examples");
        List<String> exampleLevels = examples.stream().map(e -> e.getString("level")).distinct().toList();
        assertThat(exampleLevels).isEqualTo(levels);
        assertThat(examples.stream().map(e -> e.getString("name"))).contains("quick-start/timer-log");
        assertThat(examples).allMatch(e -> e.get("requiresDocker") instanceof Boolean);
    }

    @Test
    void filtersOneGroupAndReturnsWhatTheExamplesTeach() {
        JsonObject run = new ExampleTools().camel_catalog_examples(null, "run", null);

        List<JsonObject> groups = objects(run, "groups");
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getString("level")).isEqualTo("run");
        assertThat(groups.get(0).getInteger("count")).isEqualTo(run.getInteger("total"));
        List<JsonObject> examples = objects(run, "examples");
        assertThat(examples).allMatch(e -> "run".equals(e.getString("level")));
        assertThat(examples).isSortedAccordingTo(
                Comparator.comparingInt(e -> e.getInteger("order") != null ? e.getInteger("order") : Integer.MAX_VALUE));
        assertThat(examples).anyMatch(e -> !e.getMap("teaches").isEmpty());
    }

    @Test
    void searchesByNameAndHonoursTheLimit() {
        JsonObject kafka = new ExampleTools().camel_catalog_examples("kafka", null, 1);

        assertThat(kafka.getInteger("total")).isGreaterThanOrEqualTo(1);
        assertThat(kafka.getInteger("count")).isEqualTo(1);
        JsonObject first = objects(kafka, "examples").get(0);
        assertThat(first.getString("name")).contains("kafka");
        assertThat(first.<List<String>> getCollection("infraServices")).contains("kafka");
    }

    @Test
    void readsAFileOfABundledExampleByItsShortName() {
        // CAMEL-25535: the short name of the argument description used to end in "Could not read bundled file"
        JsonObject answer = new ExampleTools().camel_catalog_example_file("timer-log", "timer-log.camel.yaml");

        assertThat(answer.getString("example")).isEqualTo("quick-start/timer-log");
        assertThat(answer.getString("file")).isEqualTo("timer-log.camel.yaml");
        assertThat(answer.getString("content")).contains("uri: timer");
        assertThat(answer.getString("githubUrl")).isNull();

        JsonObject full = new ExampleTools().camel_catalog_example_file("quick-start/timer-log", "timer-log.camel.yaml");
        assertThat(full.getString("content")).isEqualTo(answer.getString("content"));
    }

    @Test
    void errorsAreToolErrors() {
        // the messages are tested on the registry tools; here only that they reach the client as tool errors
        ExampleTools tools = new ExampleTools();
        assertThatThrownBy(() -> tools.camel_catalog_examples(null, "beginner", null))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("No example at level beginner");
        assertThatThrownBy(() -> tools.camel_catalog_example_file("beginner/timer-log", "timer-log.camel.yaml"))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("Example not found");
    }

    @SuppressWarnings("unchecked")
    private static List<JsonObject> objects(JsonObject json, String key) {
        return (List<JsonObject>) json.get(key);
    }
}
