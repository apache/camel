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
import java.util.concurrent.TimeUnit;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shell of the shell panel swaps the printer of the Camel CLI to write into the panel. It must swap the printer of
 * the command line it started with, and put it back when the panel is destroyed: the shell thread starts later than the
 * panel, and other code (AiCliCommandExecutorTest) points the static command line at another main in the meantime,
 * whose printer was taken and never given back, which made that test flaky.
 */
class ShellPanelLifecycleTest {

    private Field commandLineField;
    private Object previous;

    @BeforeEach
    void setUp() throws Exception {
        Theme.resetForTesting();
        commandLineField = CamelJBangMain.class.getDeclaredField("commandLine");
        commandLineField.setAccessible(true);
        previous = commandLineField.get(null);
    }

    @AfterEach
    void tearDown() throws Exception {
        commandLineField.set(null, previous);
    }

    private static Buffer render(ShellPanel panel) {
        Rect area = new Rect(0, 0, 80, 10);
        Buffer buffer = Buffer.empty(area);
        panel.render(Frame.forTesting(buffer), area);
        return buffer;
    }

    @Test
    void theShellSwapsThePrinterOfItsOwnCommandLineAndPutsItBack() throws Exception {
        CamelJBangMain mainA = new CamelJBangMain();
        Printer printerA = mainA.getOut();
        CamelJBangMain mainB = new CamelJBangMain();
        Printer printerB = mainB.getOut();

        commandLineField.set(null, new CommandLine(mainA));
        ShellPanel panel = new ShellPanel();
        panel.open();
        render(panel);
        // as another test does while the shell thread is still starting
        commandLineField.set(null, new CommandLine(mainB));

        await().atMost(10, TimeUnit.SECONDS).until(() -> mainA.getOut() != printerA);
        assertNotSame(printerA, mainA.getOut(), "the shell writes into the panel");
        assertSame(printerB, mainB.getOut(), "the shell must not take the printer of another command line");

        panel.destroy();
        assertSame(printerA, mainA.getOut(), "the printer is put back before destroy returns");
    }

    @Test
    void noShellWithoutTheCamelCli() throws Exception {
        commandLineField.set(null, null);
        ShellPanel panel = new ShellPanel();
        panel.open();
        String rendered = HealthTabRenderTest.bufferToString(render(panel));
        panel.destroy();

        assertTrue(rendered.contains("The shell needs the Camel CLI"), rendered);
    }
}
