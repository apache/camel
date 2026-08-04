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

import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hintLast;

class ProcessControlPopup {

    enum ControlAction {
        RUN(TuiIcons.CAMEL, "Run"),
        CLOSE_PROJECT(TuiIcons.FAIL, "Close Project"),
        STOP_ROUTES(TuiIcons.SLEEP, "Stop Routes"),
        START_ROUTES(TuiIcons.CAMEL, "Start Routes"),
        RESTART(TuiIcons.RESET, "Restart"),
        STOP(TuiIcons.STOP, "Stop"),
        KILL(TuiIcons.FAIL, "Kill"),
        STOP_ALL(TuiIcons.STOP, "Stop All");

        final String icon;
        final String label;

        ControlAction(String icon, String label) {
            this.icon = icon;
            this.label = label;
        }
    }

    interface ControlActions {
        void sendRouteCommand(String pid, String routeId, String command);

        void stopSelectedProcess(boolean forceKill);

        void restartSelectedProcess();

        void onRunPhantom(IntegrationInfo phantom);

        void onStopAll();

        boolean hasRunningProcesses();
    }

    private final MonitorContext ctx;
    private ControlActions actions;

    private boolean visible;
    private final List<ControlAction> currentOptions = new ArrayList<>();
    private final ListState listState = new ListState();

    ProcessControlPopup(MonitorContext ctx) {
        this.ctx = ctx;
    }

    void setActions(ControlActions actions) {
        this.actions = actions;
    }

    boolean isVisible() {
        return visible;
    }

    void open() {
        currentOptions.clear();
        listState.select(0);

        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info != null) {
            if (info.phantom) {
                currentOptions.add(ControlAction.RUN);
                currentOptions.add(ControlAction.CLOSE_PROJECT);
            } else {
                if (info.routeStarted > 0) {
                    currentOptions.add(ControlAction.STOP_ROUTES);
                } else if (info.routeTotal > 0) {
                    currentOptions.add(ControlAction.START_ROUTES);
                }
                currentOptions.add(ControlAction.RESTART);
                currentOptions.add(ControlAction.STOP);
                currentOptions.add(ControlAction.KILL);
            }
        } else if (ctx.findSelectedInfra() != null) {
            currentOptions.add(ControlAction.STOP);
            currentOptions.add(ControlAction.KILL);
        }

        if (actions != null && actions.hasRunningProcesses()) {
            currentOptions.add(ControlAction.STOP_ALL);
        }

        if (!currentOptions.isEmpty()) {
            visible = true;
        }
    }

    void close() {
        visible = false;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (!visible) {
            return false;
        }
        if (ke.isCancel()) {
            visible = false;
        } else if (ke.isUp()) {
            listState.selectPrevious();
        } else if (ke.isDown()) {
            listState.selectNext(currentOptions.size());
        } else if (ke.isConfirm()) {
            Integer sel = listState.selected();
            if (sel != null && sel < currentOptions.size()) {
                visible = false;
                executeAction(currentOptions.get(sel));
            }
        }
        return true;
    }

    void render(Frame frame, Rect area) {
        if (!visible || currentOptions.isEmpty()) {
            return;
        }

        int popupW = 32;
        int popupH = currentOptions.size() + 2;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height()));

        frame.renderWidget(Clear.INSTANCE, popup);

        ListItem[] items = new ListItem[currentOptions.size()];
        for (int i = 0; i < currentOptions.size(); i++) {
            ControlAction action = currentOptions.get(i);
            items[i] = ListItem.from(TuiIcons.menuItem(action.icon, action.label));
        }

        ListWidget list = ListWidget.builder()
                .items(items)
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .scrollMode(ScrollMode.NONE)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED)
                        .borders(Borders.ALL)
                        .title(" Control ")
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, listState);
    }

    void renderFooter(List<Span> spans) {
        hint(spans, "Enter", "select");
        hintLast(spans, "Esc", "cancel");
    }

    private void executeAction(ControlAction action) {
        if (actions == null) {
            return;
        }
        switch (action) {
            case RUN -> {
                IntegrationInfo info = ctx.findSelectedIntegration();
                if (info != null && info.phantom) {
                    actions.onRunPhantom(info);
                }
            }
            case CLOSE_PROJECT -> {
                IntegrationInfo info = ctx.findSelectedIntegration();
                if (info != null && info.phantom) {
                    ctx.removePhantom(info.pid);
                    ctx.selectedPid = null;
                }
            }
            case STOP_ROUTES -> actions.sendRouteCommand(ctx.selectedPid, "*", "stop");
            case START_ROUTES -> actions.sendRouteCommand(ctx.selectedPid, "*", "start");
            case RESTART -> actions.restartSelectedProcess();
            case STOP -> actions.stopSelectedProcess(false);
            case KILL -> actions.stopSelectedProcess(true);
            case STOP_ALL -> actions.onStopAll();
        }
    }
}
