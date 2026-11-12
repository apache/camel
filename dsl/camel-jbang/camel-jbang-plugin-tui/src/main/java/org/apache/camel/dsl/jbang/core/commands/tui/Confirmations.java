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
import java.util.Queue;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolConfirms;

/**
 * The dialogs that ask the user to confirm an action: quitting, stopping an integration, a file write or another call
 * of an AI tool ({@link ToolConfirms}), an AI edit waiting to be saved, a tool of its own an ACP agent wants to run,
 * deleting a file or discarding changes. Only the user answers them (CAMEL-25444): a key an MCP client sends
 * (tui_send_keys) or a tape plays never reaches one, and is dropped while one is open. While a tape drives the monitor
 * nothing asks at all ({@link MonitorContext#confirmFirst}), as the model or the tape does everything itself. A lone
 * Esc still reaches a dialog that Esc only cancels ({@link MonitorTab#isConfirmCancelledByEsc()}), so an MCP client can
 * close a dialog it opened (CAMEL-26699).
 */
final class Confirmations {

    /** A dialog that asks to confirm, whether it is open now, and whether Esc only cancels it. */
    private record Dialog(String name, BooleanSupplier open, BooleanSupplier cancelledByEsc) {
    }

    private final List<Dialog> dialogs = new ArrayList<>();
    private final Queue<McpFacade.PendingKey> pendingKeys;
    private boolean delivering;
    private boolean deliveringFromMcp;

    Confirmations(Queue<McpFacade.PendingKey> pendingKeys) {
        this.pendingKeys = pendingKeys;
    }

    /** The dialogs of the monitor that ask to confirm. */
    static Confirmations of(
            Queue<McpFacade.PendingKey> pendingKeys, PopupManager popups, ToolConfirmPopup toolConfirm,
            ActionsPopup actions, LiveEdit liveEdit, AiPanel aiPanel, FilesBrowser filesBrowser, TabRegistry tabs) {
        return new Confirmations(pendingKeys)
                .add("a confirm dialog", popups::isConfirmVisible)
                .add("the kill confirmation", popups::isKillConfirmVisible)
                .add("the confirmation of an AI tool call", toolConfirm::isVisible)
                .add("the Stop All dialog", actions::isStopAllVisible)
                .add("an AI edit that waits to be saved or discarded", liveEdit::isShown)
                .add("a permission request of an ACP agent", aiPanel::isPermissionPopupVisible)
                .add("a confirm dialog of the file browser", filesBrowser::isConfirming)
                .add("a confirm dialog of the tab", () -> {
                    MonitorTab tab = tabs.activeTab();
                    return tab != null && tab.isConfirming();
                }, () -> {
                    MonitorTab tab = tabs.activeTab();
                    return tab != null && tab.isConfirmCancelledByEsc();
                });
    }

    /** Adds a dialog that asks to confirm, named as the user sees it. */
    Confirmations add(String name, BooleanSupplier open) {
        return add(name, open, () -> false);
    }

    /** Adds a dialog that asks to confirm, and whether Esc only cancels it. */
    Confirmations add(String name, BooleanSupplier open, BooleanSupplier cancelledByEsc) {
        dialogs.add(new Dialog(name, open, cancelledByEsc));
        return this;
    }

    /** Whether the dialog that is open is one that Esc only cancels (false when none is open). */
    boolean isOpenCancelledByEsc() {
        for (Dialog dialog : dialogs) {
            if (dialog.open().getAsBoolean()) {
                return dialog.cancelledByEsc().getAsBoolean();
            }
        }
        return false;
    }

    /** The dialog that asks to confirm and is open, or null when none is. */
    String open() {
        for (Dialog dialog : dialogs) {
            if (dialog.open().getAsBoolean()) {
                return dialog.name();
            }
        }
        return null;
    }

    /**
     * Delivers the keys an MCP client or a tape sent that are due now, one by one, as a key can open a dialog. A key
     * that comes while a dialog asks to confirm is dropped: only the user answers it. An Esc that only cancels the
     * dialog gets through.
     *
     * @return whether a key was taken from the queue
     */
    boolean deliverDue(long now, Consumer<KeyEvent> handler) {
        boolean taken = false;
        McpFacade.PendingKey key;
        while ((key = pendingKeys.peek()) != null && key.isDue(now)) {
            pendingKeys.poll();
            taken = true;
            if (open() != null && !(isEsc(key.event()) && isOpenCancelledByEsc())) {
                continue;
            }
            delivering = true;
            deliveringFromMcp = key.fromMcp();
            try {
                handler.accept(key.event());
            } finally {
                delivering = false;
                deliveringFromMcp = false;
            }
        }
        return taken;
    }

    private static boolean isEsc(KeyEvent ke) {
        return ke.isKey(KeyCode.ESCAPE) && !ke.hasCtrl() && !ke.hasAlt() && !ke.hasShift();
    }

    /** Whether the key being handled was sent by an MCP client or a tape, not typed by the user. */
    boolean isDelivering() {
        return delivering;
    }

    /** Whether the key being handled was sent by an MCP client: it is for the active tab, not the AI panel's input. */
    boolean isDeliveringMcpKey() {
        return deliveringFromMcp;
    }
}
