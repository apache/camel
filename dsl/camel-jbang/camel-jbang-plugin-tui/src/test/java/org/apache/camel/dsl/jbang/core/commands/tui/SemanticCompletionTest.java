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

import dev.tamboui.widgets.input.TextAreaState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticCompletionTest {
    private SourceEditAssist assist() {
        return new SourceEditAssist(
                new MonitorContext(
                        new AtomicReference<List<IntegrationInfo>>(List.of()),
                        new AtomicReference<List<InfraInfo>>(List.of())));
    }

    @ParameterizedTest
    @CsvSource({ "department,/semantic/question/department", "support/team,/semantic/question/support%2Fteam" })
    void completionFollowsNamedQuestionMapAndOffersVariantFields(String question, String expectedPath) {
        TextAreaState state = new TextAreaState("- semantic:\n    question:\n      " + question + ":\n        ");
        SourceEditorNavigation.positionCursor(state, 3, 8);
        String path = new YamlSourceContext(state).findParentYamlPath(3);
        assertEquals(expectedPath, path);
        SourceEditAssist assist = assist();
        List<String> keys = assist.provideTreeCompletions(path).stream().map(AutocompletePopup.CompletionItem::key).toList();
        assertTrue(keys.containsAll(List.of("type", "instructions", "state", "criteria", "threshold", "uncertaintyPolicy")),
                keys.toString());
        assertFalse(assist.provideTreeCompletions(path + ":instructions").stream()
                .anyMatch(item -> item.key().equals("instructions")));
        List<String> types = assist.provideTreeValueCompletions(path + ":type").stream()
                .map(AutocompletePopup.CompletionItem::key).toList();
        assertTrue(types.containsAll(List.of("boolean", "choice", "score")), types.toString());
        assertFalse(String.valueOf(assist.getTreeNode("semantic").get("label")).contains("language"));
    }

    @Test
    void ordinaryRouteCompletionStillResolvesThroughItsAncestors() {
        TextAreaState state = new TextAreaState(
                "- route:\n    from:\n      uri: direct:start\n      steps:\n        - log:\n            ");
        SourceEditorNavigation.positionCursor(state, 5, 12);
        String path = new YamlSourceContext(state).findParentYamlPath(5);
        assertEquals("/route/from/steps/log", path);
        SourceEditAssist assist = assist();
        assertEquals(assist.provideTreeCompletions("log"), assist.provideTreeCompletions(path));
        assertEquals(assist.provideTreeValueCompletions("log:loggingLevel"),
                assist.provideTreeValueCompletions(path + ":loggingLevel"));
        assertFalse(assist.provideTreeValueCompletions(path + ":loggingLevel").isEmpty());
    }
}
