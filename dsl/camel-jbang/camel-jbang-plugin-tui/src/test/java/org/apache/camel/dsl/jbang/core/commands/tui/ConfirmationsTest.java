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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.widgets.tabs.TabsState;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25444: the keys an MCP client sends (tui_send_keys) or a tape plays never answer a dialog that asks the user to
 * confirm an action; while a tape drives the monitor nothing asks at all.
 */
@Isolated
class ConfirmationsTest {

    private final Queue<McpFacade.PendingKey> pending = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean quitDialog = new AtomicBoolean();
    private final Confirmations confirmations = new Confirmations(pending).add("the quit confirmation", quitDialog::get);

    @Test
    void theKeysOfAnMcpClientAreDeliveredWhileNoDialogAsksToConfirm() {
        pending.add(due(KeyEvent.ofChar('a')));
        pending.add(due(KeyEvent.ofChar('b')));
        List<String> delivered = new ArrayList<>();

        boolean taken = confirmations.deliverDue(0, event -> {
            KeyEvent key = (KeyEvent) event;
            assertThat(confirmations.isDelivering()).isTrue();
            delivered.add(key.string());
        });

        assertThat(taken).isTrue();
        assertThat(delivered).containsExactly("a", "b");
        assertThat(confirmations.isDelivering()).isFalse();
    }

    @Test
    void aKeyOfAnMcpClientIsForTheActiveTabNotTheAiPanelInput() {
        // CAMEL-26864: tui_send_keys sent Enter while the AI panel was open, and the panel's input took it
        pending.add(new McpFacade.PendingKey(KeyEvent.ofChar('a'), 0, true));
        pending.add(due(KeyEvent.ofChar('b')));
        List<String> fromMcp = new ArrayList<>();

        confirmations.deliverDue(0, event -> {
            KeyEvent key = (KeyEvent) event;
            if (confirmations.isDeliveringMcpKey()) {
                fromMcp.add(key.string());
            }
        });

        // a tape plays the keys as the user typed them, into the panel too
        assertThat(fromMcp).containsExactly("a");
        assertThat(confirmations.isDeliveringMcpKey()).isFalse();

        // tui_send_keys marks its keys
        McpFacade facade = new McpFacade(
                new MonitorContext(new AtomicReference<>(List.of()), new AtomicReference<>(List.of())),
                new AtomicReference<>(List.of()), null, null, null, null, null, null, null, null, null, pending, null);
        assertThat(facade.injectKeys(List.of("Enter"), 100)).isEqualTo(1);
        assertThat(pending.poll().fromMcp()).isTrue();
    }

    @Test
    void aKeyThatComesWhileADialogAsksToConfirmIsDropped() {
        // q opens the quit confirmation; the Enter queued right behind it must not answer it
        pending.add(due(KeyEvent.ofChar('q')));
        pending.add(due(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE)));
        pending.add(new McpFacade.PendingKey(KeyEvent.ofChar('x'), 1000));
        List<String> delivered = new ArrayList<>();

        confirmations.deliverDue(0, event -> {
            KeyEvent key = (KeyEvent) event;
            delivered.add(key.isKey(KeyCode.ENTER) ? "Enter" : key.string());
            if (key.isChar('q')) {
                quitDialog.set(true);
            }
        });

        assertThat(delivered).containsExactly("q");
        assertThat(confirmations.open()).isEqualTo("the quit confirmation");
        assertThat(pending).hasSize(1).as("a key not due yet waits");

        // the user answered it: later keys are delivered again
        quitDialog.set(false);
        confirmations.deliverDue(1000, event -> delivered.add(((KeyEvent) event).string()));
        assertThat(delivered).containsExactly("q", "x");
    }

    @Test
    void theClicksOfATapeAreDeliveredInOrderWithItsKeysAndDroppedWhileADialogAsksToConfirm() {
        // CAMEL-26861: a tape clicks and scrolls through the same queue as its keys
        pending.add(due(KeyEvent.ofChar('a')));
        pending.add(new McpFacade.PendingKey(MouseEvent.press(MouseButton.LEFT, 3, 4), 0));
        pending.add(new McpFacade.PendingKey(MouseEvent.scrollDown(3, 4), 0));
        List<Event> delivered = new ArrayList<>();

        confirmations.deliverDue(0, event -> {
            delivered.add(event);
            if (event instanceof MouseEvent me && me.isPress()) {
                quitDialog.set(true);
            }
        });

        assertThat(delivered).hasSize(2);
        assertThat(delivered.get(0)).isInstanceOf(KeyEvent.class);
        assertThat(delivered.get(1)).isEqualTo(MouseEvent.press(MouseButton.LEFT, 3, 4));
        assertThat(pending).isEmpty();
    }

    @Test
    void sendKeysSaysThatOnlyTheUserAnswersADialogThatAsksToConfirm() {
        McpFacade facade = new McpFacade(
                null, new AtomicReference<>(List.of()), null, null, null, null, null, null, null, null, null, pending,
                null);
        facade.setConfirmations(confirmations);
        quitDialog.set(true);

        String answer = new TuiToolRegistry(facade).callSendKeys(Map.of("keys", List.of("Enter")));

        assertThat(answer).startsWith("Not sent: the quit confirmation is open, and only the user answers it");
        assertThat(pending).isEmpty();
    }

    @Test
    void aLoneEscCancelsADialogThatEscOnlyCancels() {
        // CAMEL-26699: the confirm of a chaos action, which an MCP client opened; Esc closes it, Enter or y never answer
        AtomicBoolean chaosConfirm = new AtomicBoolean(true);
        Confirmations chaos = new Confirmations(pending).add("a confirm dialog of the tab", chaosConfirm::get, () -> true);
        pending.add(due(KeyEvent.ofChar('y')));
        pending.add(due(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.CTRL)));
        pending.add(due(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE)));
        List<String> delivered = new ArrayList<>();

        chaos.deliverDue(0, event -> {
            KeyEvent key = (KeyEvent) event;
            delivered.add(key.isKey(KeyCode.ESCAPE) ? "Esc" : key.string());
            chaosConfirm.set(false);
        });

        assertThat(delivered).containsExactly("Esc");
        assertThat(chaos.open()).isNull();
        // the quit confirmation is not one that Esc only cancels
        assertThat(confirmations.isOpenCancelledByEsc()).isFalse();
    }

    @Test
    void sendKeysLetsALoneEscCancelADialogThatEscOnlyCancels() {
        AtomicBoolean chaosConfirm = new AtomicBoolean(true);
        Confirmations chaos = new Confirmations(pending).add("a confirm dialog of the tab", chaosConfirm::get, () -> true);
        McpFacade facade = new McpFacade(
                null, new AtomicReference<>(List.of()), new TabsState(0), new RecordingManager(null), null, null, null,
                null, null, null, null, pending, null);
        facade.setConfirmations(chaos);
        TuiToolRegistry tools = new TuiToolRegistry(facade);

        assertThat(tools.callSendKeys(Map.of("keys", List.of("y"))))
                .startsWith("Not sent: a confirm dialog of the tab is open").endsWith("(a lone Esc cancels it).");
        assertThat(tools.callSendKeys(Map.of("keys", List.of("Esc", "y")))).startsWith("Not sent");
        assertThat(pending).isEmpty();
        assertThat(tools.callSendKeys(Map.of("keys", List.of("Esc")))).startsWith("Queued 1 key")
                .endsWith("for the Overview tab");
        assertThat(pending).hasSize(1);
    }

    @Test
    void whileATapeDrivesTheMonitorNothingAsksToConfirm() {
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of()), new AtomicReference<>(List.of()));
        ctx.confirmActions = true;
        assertThat(ctx.confirmFirst()).isTrue();

        ctx.tapeDriven = () -> true;
        assertThat(ctx.confirmFirst()).isFalse();

        ctx.confirmActions = false;
        ctx.tapeDriven = () -> false;
        assertThat(ctx.confirmFirst()).isFalse();
    }

    @Test
    void deletingAFileAsksFirstUnlessATapeDrivesTheMonitor() {
        FileActionsPopup popup = new FileActionsPopup();
        popup.open("orders.camel.yaml", true);
        deleteFromTheMenu(popup);
        assertThat(popup.isConfirming()).isTrue();
        assertThat(popup.consumeResult()).isNull();

        popup.setUnattended(() -> true);
        popup.open("orders.camel.yaml", true);
        deleteFromTheMenu(popup);
        assertThat(popup.isConfirming()).isFalse();
        assertThat(popup.consumeResult()).isEqualTo(new FileActionsPopup.Request(FileActionsPopup.Action.DELETE, null));
    }

    @Test
    void stopAllStopsBothWithoutAskingWhileATapeDrivesTheMonitor(@TempDir Path home) {
        String original = CommandLineHelper.getHomeDir().toString();
        CommandLineHelper.useHomeDir(home.toString());
        try {
            // a pid no process has, so nothing is really stopped
            IntegrationInfo integration = new IntegrationInfo();
            integration.pid = String.valueOf(Long.MAX_VALUE);
            InfraInfo infra = new InfraInfo();
            infra.pid = String.valueOf(Long.MAX_VALUE);
            infra.alias = "kafka";
            StopAllPopup popup = new StopAllPopup(() -> List.of(integration), () -> List.of(infra), () -> {
            }, new HashSet<>());

            popup.open();
            assertThat(popup.isVisible()).as("both run: it asks what to stop").isTrue();
            popup.close();

            popup.setUnattended(true);
            popup.open();
            assertThat(popup.isVisible()).isFalse();
            assertThat(popup.consumeNotification()).isEqualTo("Stopping 1 integration and stopping 1 infra service");
        } finally {
            CommandLineHelper.useHomeDir(original);
        }
    }

    private static void deleteFromTheMenu(FileActionsPopup popup) {
        // New file, New folder, Rename, Duplicate, Delete
        for (int i = 0; i < 4; i++) {
            popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        }
        popup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
    }

    private static McpFacade.PendingKey due(KeyEvent key) {
        return new McpFacade.PendingKey(key, 0);
    }
}
