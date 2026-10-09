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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.widgets.input.TextAreaState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticCompletionTest {
    private SourceEditAssist assist() {
        return new SourceEditAssist(
                new MonitorContext(
                        new AtomicReference<List<IntegrationInfo>>(List.of()),
                        new AtomicReference<List<InfraInfo>>(List.of())));
    }

    @ParameterizedTest
    @CsvSource({ "department,/semantic/evaluation/department", "support/team,/semantic/evaluation/support%2Fteam" })
    void completionFollowsNamedEvaluationMapAndOffersVariantFields(String evaluation, String expectedPath) {
        TextAreaState state = new TextAreaState("- semantic:\n    evaluation:\n      " + evaluation + ":\n        ");
        SourceEditorNavigation.positionCursor(state, 3, 8);
        String path = new YamlSourceContext(state).findParentYamlPath(3);
        assertThat(path).isEqualTo(expectedPath);
        SourceEditAssist assist = assist();
        List<String> keys = assist.provideTreeCompletions(path).stream().map(AutocompletePopup.CompletionItem::key).toList();
        assertThat(keys).as("completions for path %s", path).contains("type", "operation", "expert", "parameters",
                "instructions", "state", "criteria", "threshold",
                "uncertaintyPolicy");
        assertThat(assist.provideTreeCompletions(path + ":instructions"))
                .extracting(AutocompletePopup.CompletionItem::key).doesNotContain("instructions");
        List<String> types = assist.provideTreeValueCompletions(path + ":type").stream()
                .map(AutocompletePopup.CompletionItem::key).toList();
        assertThat(types).isEmpty();
        assertThat(String.valueOf(assist.getTreeNode("semantic").get("label")))
                .as("semantic node label should not mention language").doesNotContain("language");
    }

    @Test
    void typeSuggestionsPreserveCustomOperationPlaceholders(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("application.properties"), "custom.operation=detect");
        SourceEditAssist assist = assist();
        assist.setRootDir(directory);
        assertThat(assist.provideTreeValueCompletions("/semantic/evaluation/injection:type"))
                .extracting(AutocompletePopup.CompletionItem::key)
                .containsExactly("{{custom.operation}}");
    }

    @Test
    void shorthandSuggestionsAreLimitedToTheEvaluationType() {
        SourceEditAssist assist = assist();
        for (String context : List.of("/semantic/evaluation/department:operation",
                "/semantic/evaluation/department:uncertaintyPolicy", "/semantic/evaluation/department/parameters:type")) {
            assertThat(assist.provideTreeValueCompletions(context)).as(context).isEmpty();
        }
        assertThat(assist.provideTreeValueCompletions("param:type"))
                .extracting(AutocompletePopup.CompletionItem::key)
                .contains("header", "query", "path")
                .doesNotContain("boolean", "choice", "score", "classification");
    }

    @Test
    void ordinaryRouteCompletionStillResolvesThroughItsAncestors() {
        TextAreaState state = new TextAreaState(
                "- route:\n    from:\n      uri: direct:start\n      steps:\n        - log:\n            ");
        SourceEditorNavigation.positionCursor(state, 5, 12);
        String path = new YamlSourceContext(state).findParentYamlPath(5);
        assertThat(path).isEqualTo("/route/from/steps/log");
        SourceEditAssist assist = assist();
        assertThat(assist.provideTreeCompletions(path)).isEqualTo(assist.provideTreeCompletions("log"));
        assertThat(assist.provideTreeValueCompletions(path + ":loggingLevel"))
                .isEqualTo(assist.provideTreeValueCompletions("log:loggingLevel")).isNotEmpty();
    }
}
