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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.io.Closeable;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

import org.apache.camel.util.IOHelper;
import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Attributes;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.Display;
import org.jline.utils.InfoCmp;
import org.jline.utils.Status;

/**
 * Interactive terminal that runs in full screen and allows a more immersive user experience with Camel JBang CLI.
 */
public class InteractiveTerminal implements Closeable {

    private final KeyMap<String> keys = new KeyMap<>();

    private Terminal terminal;
    private Display display;
    private Size size;
    private BindingReader bindingReader;
    private Runnable sigint;
    private Status status;
    private Runnable statusTask;

    public InteractiveTerminal() throws Exception {
        terminal = TerminalBuilder.builder().build();
    }

    public void start() {
        Attributes attributes = terminal.getAttributes();
        int vsusp = attributes.getControlChar(Attributes.ControlChar.VSUSP);
        if (vsusp > 0) {
            attributes.setControlChar(Attributes.ControlChar.VSUSP, vsusp);
        }
        Attributes newAttr = new Attributes(attributes);
        newAttr.setLocalFlags(EnumSet.of(Attributes.LocalFlag.ICANON, Attributes.LocalFlag.ECHO, Attributes.LocalFlag.IEXTEN,
                Attributes.LocalFlag.ISIG), false);
        newAttr.setInputFlags(EnumSet.of(Attributes.InputFlag.IXON, Attributes.InputFlag.ICRNL, Attributes.InputFlag.INLCR),
                false);
        newAttr.setControlChar(Attributes.ControlChar.VMIN, 1);
        newAttr.setControlChar(Attributes.ControlChar.VTIME, 0);
        newAttr.setControlChar(Attributes.ControlChar.VINTR, 0);
        terminal.setAttributes(newAttr);
        terminal.puts(InfoCmp.Capability.enter_ca_mode);
        terminal.puts(InfoCmp.Capability.keypad_xmit);

        // Create a display for managing the screen
        display = new Display(terminal, true);
        // Get initial terminal size
        size = terminal.getSize();
        // resize display
        display.resize(size.getRows(), size.getColumns());

        status = Status.getStatus(terminal);
        bindingReader = new BindingReader(terminal.reader());

        if (sigint != null) {
            terminal.handle(Terminal.Signal.INT, signal -> {
                sigint.run();
            });
        }
    }

    public void sigint(Runnable task) {
        this.sigint = task;
    }

    public void status(Runnable task) {
        this.statusTask = task;
    }

    public void addKeyBinding(String operation, String... key) {
        keys.bind(operation, key);
    }

    public void addKeyBinding(String operation, InfoCmp.Capability keySeq) {
        keys.bind(operation, KeyMap.key(terminal, keySeq));
    }

    public String readNextKeyBinding() {
        return bindingReader.readBinding(keys);
    }

    public void clearDisplay() {
        display.clear();
    }

    public void updateDisplay(List<AttributedString> newLines) {
        display.update(newLines, size.cursorPos(0, 0));
    }

    public void updateStatus(List<AttributedString> newLines) {
        status.update(newLines);
    }

    public void flush() {
        terminal.flush();
    }

    public void stop() throws Exception {
        close();
    }

    @Override
    public void close() throws IOException {
        IOHelper.close(terminal);
    }
}
