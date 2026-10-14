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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import dev.tamboui.text.Span;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.tabs.TabsState;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The {@code /} filter of the table tabs and the global keys: while a filter is typed it has the keyboard, so
 * {@code q}, the digits and {@code ?} are typed into it, and Esc closes it without leaving the tab. CamelMonitor asks
 * the active tab ({@link MonitorTab#isTextInputActive()}) and handles Esc before the tab's keys.
 */
class MonitorTabTextInputTest {

    private final MonitorContext ctx = new MonitorContext(
            new AtomicReference<>(List.of()), new AtomicReference<>(List.of()));

    static Stream<Function<MonitorContext, MonitorTab>> filterTabs() {
        return Stream.of(
                BeansTab::new, CatalogTab::new, ClasspathTab::new, MavenDependenciesTab::new,
                c -> new SpansTab(c, new AtomicReference<>(List.of())), TypeConvertersTab::new);
    }

    private static String footer(MonitorTab tab) {
        List<Span> spans = new ArrayList<>();
        tab.renderFooter(spans);
        return spans.stream().map(Span::content).reduce("", String::concat);
    }

    @ParameterizedTest
    @MethodSource("filterTabs")
    void theFilterHasTheKeyboardUntilEscClosesIt(Function<MonitorContext, MonitorTab> factory) {
        MonitorTab tab = factory.apply(ctx);
        assertThat(tab.isTextInputActive()).isFalse();
        assertThat(tab.handlePaste("ignored")).as("no field open, no paste").isFalse();

        assertThat(tab.handleKeyEvent(KeyEvent.ofChar('/'))).isTrue();
        assertThat(tab.isTextInputActive()).isTrue();
        tab.handleKeyEvent(KeyEvent.ofChar('q'));
        assertThat(tab.handlePaste("1?")).isTrue();
        assertThat(footer(tab)).startsWith(" /q1?");

        assertThat(tab.handleEscape()).as("Esc closes the field and stays on the tab").isTrue();
        assertThat(tab.isTextInputActive()).isFalse();
        assertThat(footer(tab)).doesNotContain("q1?");
    }

    @ParameterizedTest
    @MethodSource("filterTabs")
    void enterFiltersAndEscClearsTheFilterAfterwards(Function<MonitorContext, MonitorTab> factory) {
        MonitorTab tab = factory.apply(ctx);
        tab.handleKeyEvent(KeyEvent.ofChar('/'));
        tab.handleKeyEvent(KeyEvent.ofChar('x'));
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));

        assertThat(tab.isTextInputActive()).isFalse();
        assertThat(footer(tab)).contains("\"x\"");

        assertThat(tab.handleEscape()).as("Esc clears the filter").isTrue();
        assertThat(footer(tab)).doesNotContain("\"x\"");
    }

    // the dispatch of CamelMonitor: the global keys run before the tab gets the key

    private CamelMonitor monitorOn(MonitorTab active) throws Exception {
        TabRegistry registry = mock(TabRegistry.class);
        when(registry.selectedTabIndex()).thenReturn(TabRegistry.TAB_MORE);
        when(registry.getActiveMoreTab()).thenReturn(active);
        when(registry.activeTab()).thenReturn(active);
        CamelMonitor monitor = new CamelMonitor(new CamelJBangMain(), getClass().getClassLoader());
        field("tabRegistry").set(monitor, registry);
        field("ctx").set(monitor, ctx);
        tabs(monitor).select(TabRegistry.TAB_MORE);
        return monitor;
    }

    private static Field field(String name) throws Exception {
        Field field = CamelMonitor.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static TabsState tabs(CamelMonitor monitor) throws Exception {
        return (TabsState) field("tabsState").get(monitor);
    }

    private static boolean globalKey(CamelMonitor monitor, KeyEvent ke) throws Exception {
        Method global = CamelMonitor.class.getDeclaredMethod("handleGlobalKeys", KeyEvent.class, TuiRunner.class);
        global.setAccessible(true);
        return (boolean) global.invoke(monitor, ke, null);
    }

    @Test
    void qTheDigitsAndQuestionMarkAreTypedIntoTheTypeConvertersFilter() throws Exception {
        TypeConvertersTab tab = new TypeConvertersTab(ctx);
        CamelMonitor monitor = monitorOn(tab);
        tab.handleKeyEvent(KeyEvent.ofChar('/'));

        for (char ch : "seq1?".toCharArray()) {
            KeyEvent ke = KeyEvent.ofChar(ch);
            // not taken by the global keys, which made q ask to quit, 1 go to the Overview tab and ? open help
            assertThat(globalKey(monitor, ke)).as("global key %s", ch).isFalse();
            tab.handleKeyEvent(ke);
        }

        assertThat(footer(tab)).startsWith(" /seq1?");
        assertThat(tabs(monitor).selected()).isEqualTo(TabRegistry.TAB_MORE);
    }

    @Test
    void escWhileTypingAFilterClosesTheFieldAndStaysOnTheTab() throws Exception {
        BeansTab tab = new BeansTab(ctx);
        CamelMonitor monitor = monitorOn(tab);
        tab.handleKeyEvent(KeyEvent.ofChar('/'));
        tab.handleKeyEvent(KeyEvent.ofChar('x'));

        assertThat(globalKey(monitor, KeyEvent.ofKey(KeyCode.ESCAPE))).isTrue();

        // it used to go back to the Overview tab and leave the field open
        assertThat(tab.isTextInputActive()).isFalse();
        assertThat(tabs(monitor).selected()).isEqualTo(TabRegistry.TAB_MORE);
    }
}
