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

class GotoSourceNodePopupTest {

    @Test
    void escClosesPopup() {
        var popup = new GotoSourceNodePopup();
        popup.open(sampleEntries());
        assertThat(popup.isVisible()).isTrue();

        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));
        assertThat(popup.isVisible()).isFalse();
    }

    @Test
    void enterSelectsRouteEntry() {
        var popup = new GotoSourceNodePopup();
        popup.open(sampleEntries());

        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        YamlRouteNodeScanner.NodeEntry selected = popup.consumeSelection();
        assertThat(selected).isNotNull();
        assertThat(selected.kind()).isEqualTo(YamlRouteNodeScanner.EntryKind.ROUTE);
        assertThat(selected.routeId()).isEqualTo("myRoute");
    }

    @Test
    void downThenEnterSelectsProcessor() {
        var popup = new GotoSourceNodePopup();
        popup.open(sampleEntries());

        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        YamlRouteNodeScanner.NodeEntry selected = popup.consumeSelection();
        assertThat(selected).isNotNull();
        assertThat(selected.kind()).isEqualTo(YamlRouteNodeScanner.EntryKind.PROCESSOR);
        assertThat(selected.type()).isEqualTo("log");
    }

    @Test
    void typingFiltersToMatchingProcessor() {
        var popup = new GotoSourceNodePopup();
        popup.open(sampleEntries());

        popup.handleKeyEvent(KeyEvent.ofChar('k', KeyModifiers.NONE));
        popup.handleKeyEvent(KeyEvent.ofChar('a', KeyModifiers.NONE));
        popup.handleKeyEvent(KeyEvent.ofChar('f', KeyModifiers.NONE));
        popup.handleKeyEvent(KeyEvent.ofChar('k', KeyModifiers.NONE));
        popup.handleKeyEvent(KeyEvent.ofChar('a', KeyModifiers.NONE));
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        YamlRouteNodeScanner.NodeEntry selected = popup.consumeSelection();
        assertThat(selected).isNotNull();
        assertThat(selected.type()).isEqualTo("uri");
        assertThat(selected.label()).contains("kafka");
    }

    @Test
    void typingRouteIdKeepsRouteHeaderAndMatchingProcessors() {
        var popup = new GotoSourceNodePopup();
        popup.open(sampleEntries());

        popup.handleKeyEvent(KeyEvent.ofChar('m', KeyModifiers.NONE));
        popup.handleKeyEvent(KeyEvent.ofChar('y', KeyModifiers.NONE));

        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        YamlRouteNodeScanner.NodeEntry selected = popup.consumeSelection();
        assertThat(selected).isNotNull();
        assertThat(selected.routeId()).isEqualTo("myRoute");
    }

    private static List<YamlRouteNodeScanner.NodeEntry> sampleEntries() {
        return List.of(
                new YamlRouteNodeScanner.NodeEntry(
                        YamlRouteNodeScanner.EntryKind.ROUTE,
                        "myRoute", "timer:tick", "route", "timer:tick",
                        "/tmp/route.camel.yaml", 2, 0),
                new YamlRouteNodeScanner.NodeEntry(
                        YamlRouteNodeScanner.EntryKind.PROCESSOR,
                        "myRoute", null, "log", "hello",
                        "/tmp/route.camel.yaml", 5, 1),
                new YamlRouteNodeScanner.NodeEntry(
                        YamlRouteNodeScanner.EntryKind.PROCESSOR,
                        "myRoute", null, "uri", "kafka:orders",
                        "/tmp/route.camel.yaml", 8, 1));
    }
}
