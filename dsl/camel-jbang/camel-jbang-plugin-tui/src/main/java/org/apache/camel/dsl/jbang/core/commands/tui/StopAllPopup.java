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

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.hintLast;

class StopAllPopup {

    private final Supplier<List<IntegrationInfo>> integrations;
    private final Supplier<List<InfraInfo>> infraServices;
    private final Runnable burstCallback;
    private final Set<String> stoppingPids;

    private boolean visible;
    private boolean checkIntegrations = true;
    private boolean checkInfra = true;
    private int selectedRow;
    private int integrationCount;
    private int infraCount;

    private String notification;

    StopAllPopup(Supplier<List<IntegrationInfo>> integrations, Supplier<List<InfraInfo>> infraServices,
                 Runnable burstCallback, Set<String> stoppingPids) {
        this.integrations = integrations;
        this.infraServices = infraServices;
        this.burstCallback = burstCallback;
        this.stoppingPids = stoppingPids;
    }

    boolean isVisible() {
        return visible;
    }

    boolean hasBothGroups() {
        List<IntegrationInfo> ints = integrations.get();
        List<InfraInfo> infras = infraServices.get();
        long ic = ints.stream().filter(i -> !i.vanishing).count();
        long fc = infras.stream().filter(i -> !i.vanishing).count();
        return ic > 0 && fc > 0;
    }

    void open() {
        List<IntegrationInfo> ints = integrations.get();
        List<InfraInfo> infras = infraServices.get();
        integrationCount = (int) ints.stream().filter(i -> !i.vanishing).count();
        infraCount = (int) infras.stream().filter(i -> !i.vanishing).count();

        if (integrationCount == 0 && infraCount == 0) {
            notification = "No running processes to stop";
            return;
        }

        if (integrationCount > 0 && infraCount == 0) {
            stopIntegrations();
            burstCallback.run();
            return;
        }
        if (infraCount > 0 && integrationCount == 0) {
            stopInfraServices();
            burstCallback.run();
            return;
        }

        checkIntegrations = true;
        checkInfra = true;
        selectedRow = 0;
        visible = true;
    }

    void close() {
        visible = false;
    }

    String consumeNotification() {
        String msg = notification;
        notification = null;
        return msg;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (!visible) {
            return false;
        }
        if (ke.isCancel()) {
            visible = false;
        } else if (ke.isUp()) {
            selectedRow = 0;
        } else if (ke.isDown()) {
            selectedRow = 1;
        } else if (ke.isChar(' ')) {
            if (selectedRow == 0) {
                checkIntegrations = !checkIntegrations;
            } else {
                checkInfra = !checkInfra;
            }
        } else if (ke.isConfirm()) {
            visible = false;
            executeStop();
        }
        return true;
    }

    void render(Frame frame, Rect area) {
        int popupW = Math.min(42, area.width() - 4);
        int popupH = 6;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + Math.max(0, (area.height() - popupH) / 2);
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height()));

        frame.renderWidget(Clear.INSTANCE, popup);

        String intLabel = (checkIntegrations ? "[x]" : "[ ]") + " All integrations (" + integrationCount + ")";
        String infraLabel = (checkInfra ? "[x]" : "[ ]") + " All infra services (" + infraCount + ")";

        Style normalStyle = Style.EMPTY;
        Style selectedStyle = Style.EMPTY.bold().reversed();

        Line intLine = Line.from(Span.styled("  " + intLabel, selectedRow == 0 ? selectedStyle : normalStyle));
        Line infraLine = Line.from(Span.styled("  " + infraLabel, selectedRow == 1 ? selectedStyle : normalStyle));

        Paragraph para = Paragraph.builder()
                .text(Text.from(Line.from(""), intLine, infraLine))
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED)
                        .title(" 🛑 Stop All ")
                        .build())
                .build();
        frame.renderWidget(para, popup);
    }

    void renderFooter(List<Span> spans) {
        hint(spans, "Space", "toggle");
        hint(spans, "Enter", "confirm");
        hintLast(spans, "Esc", "cancel");
    }

    private void executeStop() {
        int stoppedInt = 0;
        int stoppedInfra = 0;
        if (checkIntegrations) {
            stoppedInt = stopIntegrations();
        }
        if (checkInfra) {
            stoppedInfra = stopInfraServices();
        }
        if (stoppedInt > 0 || stoppedInfra > 0) {
            burstCallback.run();
        }
        if (stoppedInt == 0 && stoppedInfra == 0 && notification == null) {
            notification = "Nothing selected to stop";
        }
    }

    private int stopIntegrations() {
        List<IntegrationInfo> ints = integrations.get();
        int count = 0;
        for (IntegrationInfo info : ints) {
            if (info.vanishing || info.pid == null) {
                continue;
            }
            try {
                long pid = Long.parseLong(info.pid);
                ProcessHandle.of(pid).ifPresent(ph -> {
                    stoppingPids.add(info.pid);
                    ph.destroy();
                });
                count++;
            } catch (NumberFormatException e) {
                // skip
            }
        }
        if (count > 0) {
            notification = "Stopping " + count + " integration" + (count > 1 ? "s" : "");
        }
        return count;
    }

    private int stopInfraServices() {
        List<InfraInfo> infras = infraServices.get();
        Path camelDir = CommandLineHelper.getCamelDir();
        int count = 0;
        for (InfraInfo info : infras) {
            if (info.vanishing || info.pid == null) {
                continue;
            }
            PathUtils.deleteFile(camelDir.resolve("infra-" + info.alias + "-" + info.pid + ".json"));
            try {
                long pid = Long.parseLong(info.pid);
                ProcessHandle.of(pid).ifPresent(ProcessHandle::destroy);
                count++;
            } catch (NumberFormatException e) {
                // skip
            }
        }
        if (count > 0) {
            String prev = notification;
            String msg = "Stopping " + count + " infra service" + (count > 1 ? "s" : "");
            notification = prev != null ? prev + " and " + msg.substring(0, 1).toLowerCase() + msg.substring(1) : msg;
        }
        return count;
    }
}
