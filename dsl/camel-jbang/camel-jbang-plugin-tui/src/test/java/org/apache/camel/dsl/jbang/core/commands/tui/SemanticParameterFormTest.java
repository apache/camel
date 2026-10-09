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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticParameterFormTest {
    @Test
    void namedInputUpdatesPopulateTypedControlsAndInvalidCollectionsPreserveTheDraft() throws Exception {
        var form = form("""
                [{"name":"criteria","type":"Map","itemType":"String","required":true},
                 {"name":"enabled","type":"Boolean"}]
                """);
        assertThat(form.setInputValue("criteria", "{\"billing\":\"Invoices\"}")).isTrue();
        assertThat(form.setInputValue("enabled", "true")).isTrue();
        assertThat(form.values()).containsEntry("criteria", Map.of("billing", "Invoices")).containsEntry("enabled", true);
        assertThatThrownBy(() -> form.setInputValue("criteria", "[1,2]"))
                .hasMessageContaining("JSON object");
        assertThatThrownBy(() -> form.setInputValue("enabled", "maybe"))
                .hasMessageContaining("true or false");
        assertThat(form.values()).containsEntry("criteria", Map.of("billing", "Invoices")).containsEntry("enabled", true);
        assertThat(form.setInputValue("unknown", "value")).isFalse();
        form.setInputValue("enabled", "");
        assertThat(form.values()).doesNotContainKey("enabled");
    }

    @Test
    void categoryRowsSerializeWithoutJsonEntryAndRejectDuplicateOrIncompleteKeys() throws Exception {
        var form = form("""
                [{"name":"criteria","type":"Map","itemType":"String","required":true,
                  "description":"Named categories","minSize":1,"maxSize":3}]
                """);
        assertThat(render(form, 60, 12)).contains("criteria", "required", "Key", "Value", "Add entry");
        form.handlePaste("billing");
        assertThat(form.next(false)).isTrue();
        form.handlePaste("Invoices, payments and refunds");
        form.handleKeyEvent(ctrl('n'));
        form.handlePaste("billing");
        form.next(false);
        form.handlePaste("Bugs and outages");
        assertThatThrownBy(form::values).hasMessageContaining("duplicate key 'billing'");
        form.next(false);
        form.next(false);
        form.handleKeyEvent(ctrl('l'));
        assertThatThrownBy(form::values).hasMessageContaining("complete each entry");
        form.next(false);
        form.next(false);
        form.handlePaste("technical");
        assertThat(form.values()).containsEntry("criteria", Map.of("billing", "Invoices, payments and refunds",
                "technical", "Bugs and outages"));
        assertThat(render(form, 60, 12)).contains("billing", "technical");
        long revision = form.revision();
        form.handleKeyEvent(KeyEvent.ofKey(KeyCode.LEFT));
        form.next(false);
        assertThat(form.revision()).isEqualTo(revision);
        assertThat(render(form, 22, 4)).contains("Parameters");
    }

    @Test
    void orderedRowsCanBeAddedReorderedAndRemoved() throws Exception {
        var form = form("""
                [{"name":"criteria","type":"List","itemType":"String","required":true,
                  "description":"Ordered score levels","minSize":1,"maxSize":2}]
                """);
        form.handlePaste("Routine request");
        form.handleKeyEvent(ctrl('n'));
        form.handlePaste("Urgent request");
        assertThat(form.values()).containsEntry("criteria", List.of("Routine request", "Urgent request"));
        assertThat(render(form, 60, 12)).contains("[0] Routine request", "[1] Urgent request");
        form.handleKeyEvent(KeyEvent.ofKey(KeyCode.UP, KeyModifiers.CTRL));
        assertThat(form.values()).containsEntry("criteria", List.of("Urgent request", "Routine request"));
        form.handleKeyEvent(ctrl('n'));
        form.handlePaste("One too many");
        assertThatThrownBy(form::values).hasMessageContaining("maximum size is 2");
        form.next(false);
        form.handleKeyEvent(ctrl('d'));
        assertThat(form.values()).containsEntry("criteria", List.of("Urgent request", "Routine request"));
        form.focus(true);
        form.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));
        form.handlePaste("Added using button");
        form.handleKeyEvent(ctrl('d'));
        assertThat(form.values()).containsEntry("criteria", List.of("Urgent request", "Routine request"));
    }

    @Test
    void numbersBooleansAndDeclaredChoicesRetainTypesAndOmitUnsetOptions() throws Exception {
        var form = form("""
                [{"name":"threshold","type":"Number","minimum":0,"maximum":1,"omission":"Use 0.5"},
                 {"name":"enabled","type":"Boolean"},
                 {"name":"policy","type":"String","values":["fail","non-match"]}]
                """);
        assertThat(form.values()).isEmpty();
        form.handlePaste("not a number");
        assertThatThrownBy(form::values).hasMessageContaining("threshold: enter a number");
        form.handleKeyEvent(ctrl('l'));
        form.handlePaste("1.2");
        assertThatThrownBy(form::values).hasMessageContaining("maximum is 1");
        form.handleKeyEvent(ctrl('l'));
        form.handlePaste("0.7");
        form.next(false);
        form.handleKeyEvent(KeyEvent.ofChar(' '));
        form.next(false);
        form.handleKeyEvent(KeyEvent.ofChar(' '));
        assertThat(form.values()).containsEntry("threshold", new BigDecimal("0.7"))
                .containsEntry("enabled", false).containsEntry("policy", "fail");
        form.handleKeyEvent(KeyEvent.ofKey(KeyCode.RIGHT));
        assertThat(form.values()).containsEntry("policy", "non-match");
        form.handleKeyEvent(ctrl('l'));
        assertThat(form.values()).doesNotContainKey("policy");
        assertThat(form.next(false)).isFalse();
        assertThat(form.next(true)).isTrue();
    }

    @Test
    void requiredInstructionsAndIntegerConstraintsValidateBeforeCallingTheExpert() throws Exception {
        var form = form("""
                [{"name":"instructions","type":"String","required":true,"minSize":4},
                 {"name":"count","type":"Number","integer":true}]
                """);
        assertThatThrownBy(form::values).hasMessageContaining("instructions: minimum size is 4");
        form.handlePaste("a");
        assertThatThrownBy(form::values).hasMessageContaining("minimum size is 4");
        form.handleKeyEvent(ctrl('l'));
        form.handlePaste("Choose a category\nUse the supplied evidence.");
        form.next(false);
        form.handlePaste("1.5");
        assertThatThrownBy(form::values).hasMessageContaining("enter a whole number");
        form.handleKeyEvent(ctrl('l'));
        form.handlePaste("2");
        assertThat(form.values()).containsEntry("instructions", "Choose a category\nUse the supplied evidence.")
                .containsEntry("count", new BigDecimal("2"));
    }

    @Test
    void emptyRequiredValuesAreAllowedWhenTheContractAllowsThem() throws Exception {
        var form = form("""
                [{"name":"text","type":"String","required":true},
                 {"name":"items","type":"List","itemType":"String","required":true}]
                """);
        assertThat(form.values()).containsEntry("text", "").containsEntry("items", List.of());
    }

    @Test
    void complexCollectionValuesFollowTheirPublishedItemType() throws Exception {
        var form = form("""
                [{"name":"documents","type":"List","itemType":"Map","required":true}]
                """);
        form.handlePaste("[]");
        assertThatThrownBy(form::values).hasMessageContaining("JSON object");
        form.handleKeyEvent(ctrl('l'));
        form.handlePaste("{\"name\":\"invoice\"}");
        assertThat(form.values()).containsEntry("documents", List.of(Map.of("name", "invoice")));
    }

    @Test
    void helpAndLongValuesWrapWithoutLosingTheirBeginning() throws Exception {
        var form = form("""
                [{"name":"threshold","type":"Number","description":"Inclusive injection probability threshold",
                  "omission":"Use 0.5","minimum":0,"maximum":1},
                 {"name":"criteria","type":"Map","itemType":"String"}]
                """);
        String help = render(form, 38, 24);
        assertThat(help).contains("Inclusive injection probability", "threshold", "blank: Use 0.5", "0 … 1");
        form.next(false);
        form.handlePaste("billing");
        form.next(false);
        form.handlePaste("Invoices, payments, refunds and duplicate charges");
        String values = render(form, 42, 24);
        assertThat(values).contains("Invoices, payments,", "refunds and duplicate", "charges");
        assertThat(form.values()).containsEntry("criteria",
                Map.of("billing", "Invoices, payments, refunds and duplicate charges"));
    }

    @Test
    void prefillingPreservesTypedCollectionsAndReplacesOldValues() throws Exception {
        var form = form("""
                [{"name":"criteria","type":"Map","itemType":"String"},
                 {"name":"levels","type":"List","itemType":"String"},
                 {"name":"state","type":"List","itemType":"Object"},
                 {"name":"enabled","type":"Boolean"}]
                """);
        JsonObject values = (JsonObject) Jsoner.deserialize("""
                {"criteria":{"billing":"Payments and refunds"},"levels":["Routine","Urgent"],
                 "state":["literal text",{"priority":3}],"enabled":true}
                """);
        form.load(values);
        assertThat(form.values()).isEqualTo(values);
        form.load(Map.of("levels", List.of("New level")));
        assertThat(form.values()).containsOnlyKeys("levels").containsEntry("levels", List.of("New level"));
    }

    @Test
    void previewingIncompleteParametersDoesNotMoveFocus() throws Exception {
        var form = form("""
                [{"name":"required","type":"String","required":true,"minSize":1},{"name":"note","type":"String"}]
                """);
        form.next(false);
        assertThatThrownBy(() -> form.values(false)).hasMessageContaining("required");
        form.handlePaste("Keep typing here");
        form.next(true);
        form.handlePaste("Ready");
        assertThat(form.values()).containsEntry("note", "Keep typing here").containsEntry("required", "Ready");
    }

    private static SemanticParameterForm form(String parameters) throws Exception {
        JsonObject operation = (JsonObject) Jsoner.deserialize("{\"contract\":{\"parameters\":" + parameters + "}}");
        return new SemanticParameterForm(operation);
    }

    private static KeyEvent ctrl(char key) {
        return KeyEvent.ofChar(key, KeyModifiers.CTRL);
    }

    private static String render(SemanticParameterForm form, int width, int height) {
        Rect area = new Rect(0, 0, width, height);
        Buffer buffer = Buffer.empty(area);
        form.render(Frame.forTesting(buffer), area, true, true, Theme.title());
        return TuiTestHelper.bufferToString(buffer);
    }
}
