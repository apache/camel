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
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that the F10 process-control popup gates Stop, Restart and Kill behind a confirm dialog when confirmations are
 * enabled, and runs them directly when they are disabled.
 */
class ProcessControlPopupTest {

    private MonitorContext ctx;
    private ProcessControlPopup popup;
    private final List<String> calls = new ArrayList<>();
    private final List<Runnable> pendingConfirms = new ArrayList<>();

    @BeforeEach
    void setUp() {
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "4242";
        info.name = "my-app";

        ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = "4242";

        popup = new ProcessControlPopup(ctx);
        popup.setActions(new ProcessControlPopup.ControlActions() {
            @Override
            public void sendRouteCommand(String pid, String routeId, String command) {
                calls.add("route:" + command);
            }

            @Override
            public void stopSelectedProcess(boolean forceKill) {
                calls.add(forceKill ? "kill" : "stop");
            }

            @Override
            public void restartSelectedProcess() {
                calls.add("restart");
            }

            @Override
            public void showKillConfirm() {
                calls.add("confirm:kill");
            }

            @Override
            public void showConfirm(String title, String message, Runnable onConfirm) {
                calls.add("confirm:" + title + ":" + message.trim());
                pendingConfirms.add(onConfirm);
            }

            @Override
            public void onRunPhantom(IntegrationInfo phantom) {
                calls.add("run-phantom");
            }

            @Override
            public void onStopAll() {
                calls.add("stop-all");
            }

            @Override
            public boolean hasRunningProcesses() {
                return false;
            }
        });
    }

    // A non-phantom integration without routes lists: Restart (0), Stop (1), Kill (2)

    private void select(int index) {
        popup.open();
        assertThat(popup.isVisible()).isTrue();
        for (int i = 0; i < index; i++) {
            popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        }
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertThat(popup.isVisible()).isFalse();
    }

    @Test
    void killAsksForConfirmationWhenEnabled() {
        ctx.confirmActions = true;
        select(2);
        assertThat(calls).containsExactly("confirm:kill");
    }

    @Test
    void killRunsDirectlyWhenConfirmationsDisabled() {
        ctx.confirmActions = false;
        select(2);
        assertThat(calls).containsExactly("kill");
    }

    @Test
    void stopAsksForConfirmationAndRunsOnAccept() {
        ctx.confirmActions = true;
        select(1);
        assertThat(calls).containsExactly("confirm:Confirm Stop:Stop my-app (PID: 4242)?");

        pendingConfirms.get(0).run();
        assertThat(calls).containsExactly("confirm:Confirm Stop:Stop my-app (PID: 4242)?", "stop");
    }

    @Test
    void restartAsksForConfirmationAndRunsOnAccept() {
        ctx.confirmActions = true;
        select(0);
        assertThat(calls).containsExactly("confirm:Confirm Restart:Restart my-app (PID: 4242)?");

        pendingConfirms.get(0).run();
        assertThat(calls).last().isEqualTo("restart");
    }

    @Test
    void stopAndRestartRunDirectlyWhenConfirmationsDisabled() {
        ctx.confirmActions = false;
        select(1);
        select(0);
        assertThat(calls).containsExactly("stop", "restart");
    }

    @Test
    void escapeClosesWithoutRunningAnything() {
        ctx.confirmActions = false;
        popup.open();
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));
        assertThat(popup.isVisible()).isFalse();
        assertThat(calls).isEmpty();
    }
}
