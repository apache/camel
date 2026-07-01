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

    default void onTabSelected() {
    }

    default void onIntegrationChanged() {
    }

    default SelectionContext getSelectionContext() {
        return null;
    }

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
}
