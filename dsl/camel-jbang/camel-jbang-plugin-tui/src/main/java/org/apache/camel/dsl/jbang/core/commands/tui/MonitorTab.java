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

    default boolean handleEscape() {
        return false;
    }

    default void navigateUp() {
    }

    default void navigateDown() {
    }

    void render(Frame frame, Rect area);

    default void renderFooter(List<Span> spans) {
    }

    /**
     * Contributes tab-specific F-key hints that should render grouped with the global F-key hints (F1/F2/F10) in the
     * footer, rather than at the tail with the other tab hints. Appended right after the global F-keys.
     */
    default void renderFKeyHints(List<Span> spans) {
    }

    default void onTabSelected() {
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

    default boolean isOverlayActive() {
        return false;
    }

    default Boolean isDetailFocused() {
        return null;
    }
}
