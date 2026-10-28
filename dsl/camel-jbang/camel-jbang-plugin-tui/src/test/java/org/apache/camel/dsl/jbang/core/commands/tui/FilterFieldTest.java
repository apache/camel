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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilterFieldTest {

    private final AtomicInteger changes = new AtomicInteger();
    private final FilterField filter = new FilterField(changes::incrementAndGet);

    private void type(String text) {
        for (char ch : text.toCharArray()) {
            assertThat(filter.handleKey(KeyEvent.ofChar(ch))).isTrue();
        }
    }

    private static String text(List<Span> spans) {
        return spans.stream().map(Span::content).reduce("", String::concat);
    }

    @Test
    void slashOpensTheFieldAndEnterFiltersWithTheTrimmedText() {
        assertThat(filter.handleKey(KeyEvent.ofChar('x'))).isFalse();
        assertThat(filter.handleKey(KeyEvent.ofChar('/'))).isTrue();
        assertThat(filter.isEditing()).isTrue();
        type(" seda ");
        assertThat(changes).hasValue(0);

        assertThat(filter.handleKey(KeyEvent.ofKey(KeyCode.ENTER))).isTrue();

        assertThat(filter.isEditing()).isFalse();
        assertThat(filter.term()).isEqualTo("seda");
        assertThat(changes).hasValue(1);
    }

    @Test
    void theOpenFieldTakesEveryKeyAndStartsWithTheCurrentTerm() {
        filter.setTerm("kafka");
        filter.handleKey(KeyEvent.ofChar('/'));
        // q and the digits are text here, not the global keys
        type("q1");
        filter.handleKey(KeyEvent.ofKey(KeyCode.ENTER));

        assertThat(filter.term()).isEqualTo("kafkaq1");
    }

    @Test
    void enterOnAnEmptyFieldClearsTheFilter() {
        filter.setTerm("kafka");
        filter.handleKey(KeyEvent.ofChar('/'));
        for (int i = 0; i < "kafka".length(); i++) {
            filter.handleKey(KeyEvent.ofKey(KeyCode.BACKSPACE));
        }
        filter.handleKey(KeyEvent.ofKey(KeyCode.ENTER));

        assertThat(filter.term()).isNull();
    }

    @Test
    void escClosesTheFieldFirstAndClearsTheTermSecond() {
        filter.setTerm("kafka");
        changes.set(0);
        filter.handleKey(KeyEvent.ofChar('/'));
        type("x");

        assertThat(filter.handleEscape()).as("closes the field").isTrue();
        assertThat(filter.isEditing()).isFalse();
        assertThat(filter.term()).as("what was typed is dropped").isEqualTo("kafka");
        assertThat(changes).hasValue(0);

        assertThat(filter.handleEscape()).as("clears the term").isTrue();
        assertThat(filter.term()).isNull();
        assertThat(changes).hasValue(1);

        assertThat(filter.handleEscape()).as("nothing left: Esc goes on to the tab").isFalse();
    }

    @Test
    void theEscapeKeyClosesTheOpenFieldToo() {
        filter.handleKey(KeyEvent.ofChar('/'));

        assertThat(filter.handleKey(KeyEvent.ofKey(KeyCode.ESCAPE))).isTrue();
        assertThat(filter.isEditing()).isFalse();
    }

    @Test
    void pasteGoesIntoTheOpenFieldOnly() {
        assertThat(filter.handlePaste("ignored")).isFalse();

        filter.handleKey(KeyEvent.ofChar('/'));
        assertThat(filter.handlePaste("org.apache\n")).isTrue();
        filter.handleKey(KeyEvent.ofKey(KeyCode.ENTER));

        assertThat(filter.term()).isEqualTo("org.apache");
    }

    @Test
    void setTermClosesTheFieldAndCallsBack() {
        filter.handleKey(KeyEvent.ofChar('/'));

        filter.setTerm("timer");
        assertThat(filter.isEditing()).isFalse();
        assertThat(filter.term()).isEqualTo("timer");
        assertThat(changes).hasValue(1);

        filter.setTerm("");
        assertThat(filter.term()).isNull();
        filter.setTerm(null);
        assertThat(filter.term()).isNull();
    }

    @Test
    void resetForgetsTheTermWithoutCallingBack() {
        filter.setTerm("timer");
        filter.handleKey(KeyEvent.ofChar('/'));
        changes.set(0);

        filter.reset();

        assertThat(filter.isEditing()).isFalse();
        assertThat(filter.term()).isNull();
        assertThat(changes).hasValue(0);
    }

    @Test
    void footerShowsTheFieldWhileTypedAndTheTermAfterwards() {
        List<Span> spans = new ArrayList<>();
        assertThat(filter.renderEditingFooter(spans)).isFalse();
        assertThat(spans).isEmpty();
        filter.renderHint(spans);
        assertThat(text(spans)).contains("/").contains("filter");
        assertThat(filter.escapeLabel()).isEqualTo("back");

        filter.handleKey(KeyEvent.ofChar('/'));
        type("jms");
        spans.clear();
        assertThat(filter.renderEditingFooter(spans)).isTrue();
        assertThat(text(spans)).startsWith(" /jms").contains("Enter").contains("Esc");

        filter.handleKey(KeyEvent.ofKey(KeyCode.ENTER));
        spans.clear();
        filter.renderHint(spans);
        assertThat(text(spans)).contains("\"jms\"");
        assertThat(filter.escapeLabel()).isEqualTo("clear");
    }
}
