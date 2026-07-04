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
package org.apache.camel.dsl.jbang.launcher;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.dsl.jbang.core.common.Printer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for the CamelLauncher.
 */
public class CamelLauncherTest {

    /**
     * A simple PrintStream-based Printer implementation for testing.
     */
    static class PrintStreamPrinter implements Printer {
        private final PrintStream printStream;

        public PrintStreamPrinter(PrintStream printStream) {
            this.printStream = printStream;
        }

        @Override
        public void println() {
            printStream.println();
        }

        @Override
        public void println(String line) {
            printStream.println(line);
        }

        @Override
        public void print(String output) {
            printStream.print(output);
        }

        @Override
        public void printf(String format, Object... args) {
            printStream.printf(format, args);
        }
    }

    @Test
    public void testLauncherVersion() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);

        // Create a custom main that doesn't exit
        CamelLauncherMain main = new CamelLauncherMain() {
            @Override
            public void quit(int exitCode) {
                // Do nothing to prevent System.exit
            }
        };

        main.setDiscoverPlugins(false);

        // Set a custom printer to capture output
        main.setOut(new PrintStreamPrinter(ps));

        // Run the version command
        main.execute("version");

        String output = baos.toString();
        assertTrue(output.contains("Camel CLI version:"), "Output should contain version information");
    }

    @Test
    public void testEmbeddedTuiPluginReceivesClassLoader() throws Exception {
        // Regression for the launcher TUI failure "No BackendProvider found on classpath".
        // The launcher installs embedded plugins directly in postAddCommands. Each plugin must be
        // given the fat-jar classloader so that the TUI can install it as the thread-context
        // classloader for tamboui's ServiceLoader-based terminal backend discovery. Without it the
        // discovery falls back to the system classloader, which cannot see the nested BOOT-INF/lib
        // jars of the Spring Boot fat-jar.
        CamelLauncherMain main = new CamelLauncherMain();

        CommandLine commandLine = new CommandLine(main);
        main.postAddCommands(commandLine, new String[] { "tui" });

        CommandLine tui = commandLine.getSubcommands().get("tui");
        assertNotNull(tui, "tui command should be registered by the launcher");
        CommandLine monitor = tui.getSubcommands().get("monitor");
        assertNotNull(monitor, "tui monitor subcommand should be registered");

        ClassLoader pluginClassLoader = readClassLoaderField(monitor.getCommand());
        assertNotNull(pluginClassLoader,
                "embedded TUI plugin must receive a non-null classloader (else tamboui ServiceLoader breaks in the fat-jar)");
        // The classloader handed to the plugin must be able to resolve tamboui's backend SPI.
        assertDoesNotThrow(() -> pluginClassLoader.loadClass("dev.tamboui.terminal.BackendProvider"),
                "the plugin classloader must be able to load tamboui's BackendProvider");
    }

    private static ClassLoader readClassLoaderField(Object command) throws Exception {
        Field field = command.getClass().getDeclaredField("classLoader");
        field.setAccessible(true);
        return (ClassLoader) field.get(command);
    }

    @Test
    public void testLauncherValidatePlugin() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);

        // Create a custom main that doesn't exit
        final AtomicInteger code = new AtomicInteger();
        CamelLauncherMain main = new CamelLauncherMain() {
            @Override
            public void quit(int exitCode) {
                // Do nothing to prevent System.exit
                code.set(exitCode);
            }
        };

        main.setDiscoverPlugins(false);

        // Set a custom printer to capture output
        main.setOut(new PrintStreamPrinter(ps));

        // Run the validate command
        main.execute("validate", "yaml", "src/test/resources/cheese.yaml");

        Assertions.assertEquals(1, code.get());

        String output = baos.toString();
        Assertions.assertTrue(output.startsWith("Validation error detected (errors:1)"));
    }

}
