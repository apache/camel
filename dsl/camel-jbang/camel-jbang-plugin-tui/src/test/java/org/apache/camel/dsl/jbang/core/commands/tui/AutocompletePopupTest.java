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

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AutocompletePopupTest {

    @Test
    void escClosesPopup() {
        var popup = new AutocompletePopup(sampleItems(), "", "");
        assertThat(popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE)))
                .isEqualTo(AutocompletePopup.Result.CLOSED);
    }

    @Test
    void enterSelectsFirstItem() {
        var popup = new AutocompletePopup(sampleItems(), "", "");
        assertThat(popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE)))
                .isEqualTo(AutocompletePopup.Result.CLOSED);

        var selected = popup.consumeSelectedItem();
        assertThat(selected).isNotNull();
        assertThat(selected.key()).isEqualTo("alpha");
    }

    @Test
    void downArrowThenEnterSelectsSecondItem() {
        var popup = new AutocompletePopup(sampleItems(), "", "");
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        var selected = popup.consumeSelectedItem();
        assertThat(selected).isNotNull();
        assertThat(selected.key()).isEqualTo("beta");
    }

    @Test
    void typingFiltersItems() {
        var popup = new AutocompletePopup(sampleItems(), "", "");
        popup.handleKeyEvent(KeyEvent.ofChar('b', KeyModifiers.NONE));

        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        var selected = popup.consumeSelectedItem();
        assertThat(selected).isNotNull();
        assertThat(selected.key()).isEqualTo("beta");
    }

    @Test
    void typingNonMatchingClosesOnBackspace() {
        var items = List.of(
                new AutocompletePopup.CompletionItem("alpha", "desc", "string", null, false, null, null));
        var popup = new AutocompletePopup(items, "", "");

        popup.handleKeyEvent(KeyEvent.ofChar('z', KeyModifiers.NONE));
        assertThat(popup.hasItems()).isFalse();

        assertThat(popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.BACKSPACE, KeyModifiers.NONE)))
                .isEqualTo(AutocompletePopup.Result.CONSUMED);
        assertThat(popup.hasItems()).isTrue();
    }

    @Test
    void backspaceOnEmptyFilterCloses() {
        var popup = new AutocompletePopup(sampleItems(), "", "");
        assertThat(popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.BACKSPACE, KeyModifiers.NONE)))
                .isEqualTo(AutocompletePopup.Result.CLOSED);
    }

    @Test
    void consumeSelectedItemReturnsNullAfterFirstCall() {
        var popup = new AutocompletePopup(sampleItems(), "", "");
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertThat(popup.consumeSelectedItem()).isNotNull();
        assertThat(popup.consumeSelectedItem()).isNull();
    }

    @Test
    void valueModeFlag() {
        var popup = new AutocompletePopup(sampleItems(), "", "", false);
        assertThat(popup.isValueMode()).isFalse();

        var valuePopup = new AutocompletePopup(sampleItems(), "", "", true);
        assertThat(valuePopup.isValueMode()).isTrue();
    }

    @Test
    void initialPrefixFiltersItems() {
        var popup = new AutocompletePopup(sampleItems(), "g", "");
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        var selected = popup.consumeSelectedItem();
        assertThat(selected).isNotNull();
        assertThat(selected.key()).isEqualTo("gamma");
    }

    @Test
    void hasItemsReflectsFilterState() {
        var items = List.of(
                new AutocompletePopup.CompletionItem("one", null, null, null, false, null, null));
        var popup = new AutocompletePopup(items, "", "");
        assertThat(popup.hasItems()).isTrue();

        popup.handleKeyEvent(KeyEvent.ofChar('z', KeyModifiers.NONE));
        assertThat(popup.hasItems()).isFalse();
    }

    @Test
    void completionItemRecordFields() {
        var item = new AutocompletePopup.CompletionItem(
                "myKey", "my description", "string", "default", true, "use other", "advanced");

        assertThat(item.key()).isEqualTo("myKey");
        assertThat(item.description()).isEqualTo("my description");
        assertThat(item.type()).isEqualTo("string");
        assertThat(item.defaultValue()).isEqualTo("default");
        assertThat(item.deprecated()).isTrue();
        assertThat(item.deprecationNote()).isEqualTo("use other");
        assertThat(item.group()).isEqualTo("advanced");
    }

    @Test
    void pageDownAndPageUpNavigate() {
        var popup = new AutocompletePopup(sampleItems(), "", "");
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.PAGE_DOWN, KeyModifiers.NONE));
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        var selected = popup.consumeSelectedItem();
        assertThat(selected).isNotNull();
        assertThat(selected.key()).isEqualTo("gamma");
    }

    @Test
    void homeSelectsFirst() {
        var popup = new AutocompletePopup(sampleItems(), "", "");
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.HOME, KeyModifiers.NONE));
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertThat(popup.consumeSelectedItem().key()).isEqualTo("alpha");
    }

    @Test
    void endSelectsLast() {
        var popup = new AutocompletePopup(sampleItems(), "", "");
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.END, KeyModifiers.NONE));
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertThat(popup.consumeSelectedItem().key()).isEqualTo("gamma");
    }

    private static List<AutocompletePopup.CompletionItem> sampleItems() {
        return List.of(
                new AutocompletePopup.CompletionItem("alpha", "First item", "string", null, false, null, null),
                new AutocompletePopup.CompletionItem("beta", "Second item", "integer", 42, false, null, "common"),
                new AutocompletePopup.CompletionItem("gamma", "Third item", "boolean", true, true, "use delta", "advanced"));
    }
}
