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

import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import org.apache.camel.util.json.JsonObject;

/**
 * Interface for TUI monitor tabs. Each tab handles its own events, rendering, and footer hints.
 */
interface MonitorTab {

    boolean handleKeyEvent(KeyEvent ke);

    /**
     * Handle a mouse event within the tab's content area.
     *
     * @param  me   the mouse event
     * @param  area the content area where this tab is rendered
     * @return      true if the event was consumed
     */
    default boolean handleMouseEvent(MouseEvent me, Rect area) {
        return false;
    }

    /**
     * Esc on the tab. CamelMonitor handles Esc before the keys go to {@link #handleKeyEvent}, so Esc never reaches it:
     * a tab closes its open text field, popup or detail view here and returns {@code true}. When it returns
     * {@code false}, Esc goes back to the Overview tab.
     */
    default boolean handleEscape() {
        return false;
    }

    /**
     * Whether a text field of the tab (a filter, search, probe or editor field, or a popup of the tab that takes typed
     * text) has the keyboard. While it has, the global keys that are plain characters ({@code q} quits, the digits
     * switch tabs, {@code ?} opens help) are typed into it, and a paste goes to {@link #handlePaste(String)}.
     * CamelMonitor asks only the active tab, so a tab with a text field must override this, or {@code q} quits the TUI
     * while the user types.
     */
    default boolean isTextInputActive() {
        return false;
    }

    /**
     * Whether the tab shows a dialog that asks the user to confirm an action, such as discarding changes or deleting a
     * file. Only the user answers it: the keys an MCP client sends never reach it (see {@link Confirmations}).
     */
    default boolean isConfirming() {
        return false;
    }

    /**
     * Pastes text into the text field that has the keyboard; returns {@code false} when none has, as the default does.
     */
    default boolean handlePaste(String text) {
        return false;
    }

    /**
     * Whether the tab takes this key although it is a global shortcut, which then leaves it to {@link #handleKeyEvent}:
     * a key an editor of the tab uses while it has the keyboard, such as Ctrl+L that clears the Semantic tab's input
     * but elsewhere pins the log.
     */
    default boolean claimsKey(KeyEvent ke) {
        return false;
    }

    default void navigateUp() {
    }

    default void navigateDown() {
    }

    void render(Frame frame, Rect area);

    /**
     * The views of the tab and its view settings, shown as a bar on the first row of the tab (the levels of the Diagram
     * tab, the views of the Inspect tab); null for a tab without views. The settings shown there are left out of the
     * footer.
     */
    default SubViewBar.Spec subViewBar() {
        return null;
    }

    /**
     * The view settings of the tab and their state, as key hints with {@code [state]}: shown in the tab's view bar
     * rather than the footer, and listed with the footer actions for MCP agents.
     */
    default void renderViewToggles(List<Span> spans) {
        SubViewBar.Spec spec = subViewBar();
        if (spec != null) {
            for (SubViewBar.Toggle t : spec.toggles()) {
                TuiHelper.hint(spans, t.key(), t.label() + " [" + t.state() + "]");
            }
        }
    }

    default void renderFooter(List<Span> spans) {
    }

    /**
     * Contributes tab-specific F-key hints that should render grouped with the global F-key hints (F1/F2/F10) in the
     * footer, rather than at the tail with the other tab hints. Appended right after the global F-keys.
     */
    default void renderFKeyHints(List<Span> spans) {
    }

    /**
     * Called on the UI thread when the tab becomes the one on screen (a digit key, a click on the tab bar, the More
     * menu, Go to), before it is drawn. A tab that shows data fetched on demand fetches it here, or starts the fetch,
     * so it does not open on stale data.
     */
    default void onTabSelected() {
    }

    /**
     * Fetches the data only this tab shows (errors, activity, history, spans), on every data refresh while the tab is
     * the one on screen. Runs on the background refresh thread ({@code camel-tui-bg}) after the integrations were read,
     * so it may read files and ask the integration, but must not draw. The data service is {@code ctx.dataService}.
     */
    default void refreshWhileVisible() {
    }

    /**
     * For tabs that fetch their data only when opened (classpath, dependencies, catalog, CVE audit, startup): starts
     * the fetch for the selected integration if it has not happened yet and returns {@code true}, so a caller that
     * reads the tab without opening it (the AI panel, an MCP client) can wait for the data. Tabs whose data is always
     * current return {@code false}.
     */
    default boolean ensureDataLoaded() {
        return false;
    }

    /**
     * After an on-demand load finished without producing table data: the error, or a message saying the tab is empty.
     * {@code null} while the load is still running or when the tab does not load on demand.
     */
    default String dataLoadError() {
        return null;
    }

    default void onIntegrationChanged() {
    }

    default SelectionContext getSelectionContext() {
        return null;
    }

    String description();

    default String getHelpText() {
        return null;
    }

    default JsonObject getTableDataAsJson() {
        return null;
    }

    default boolean setFilter(String filter) {
        return false;
    }

    default boolean setInputValue(String field, String value) {
        return false;
    }

    default Boolean isDetailFocused() {
        return null;
    }
}
