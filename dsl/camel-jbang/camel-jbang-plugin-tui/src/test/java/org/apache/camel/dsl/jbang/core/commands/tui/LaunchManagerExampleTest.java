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

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An example launched from the TUI runs in a directory of its own, with its files, so its routes find what they read
 * relative to it.
 */
class LaunchManagerExampleTest {

    @Test
    void theExampleOfARunIsFoundByItsName() {
        JsonObject example = LaunchManager.exampleOf(List.of("run", "--example=route/content-based-router"));
        assertThat(example).isNotNull();
        assertThat(example.getString("name")).isEqualTo("route/content-based-router");
        // by its short name too, when that is unique
        assertThat(LaunchManager.exampleOf(List.of("run", "--example=content-based-router"))).isNotNull();
    }

    @Test
    void otherCommandsAreNoExample() {
        assertThat(LaunchManager.exampleOf(List.of("run", "MyRoute.java"))).isNull();
        assertThat(LaunchManager.exampleOf(List.of("infra", "run", "kafka"))).isNull();
        assertThat(LaunchManager.exampleOf(List.of("run", "--example=does-not-exist"))).isNull();
    }

    @Test
    void theExampleArgumentBecomesItsFilesAndName() {
        List<String> args = List.of("run", "--example=route/content-based-router", "--logging-color=true");
        JsonObject example = LaunchManager.exampleOf(args);

        List<String> answer = LaunchManager.exampleArgs(args, example);

        assertThat(answer).startsWith("run").contains("--logging-color=true", "--name=content-based-router")
                .doesNotContain("--example=route/content-based-router")
                .anyMatch(a -> a.endsWith(".yaml") || a.endsWith(".java") || a.endsWith(".xml"))
                .anyMatch(a -> a.startsWith("orders/"));
        // a name given stays the only one
        assertThat(LaunchManager.exampleArgs(List.of("run", "--example=route/content-based-router", "--name=cbr"), example))
                .contains("--name=cbr").doesNotContain("--name=content-based-router");
    }
}
