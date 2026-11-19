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
package org.apache.camel.dsl.jbang.core.commands.test;

import java.lang.reflect.Field;
import java.util.Optional;

import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.metadata.MetadataProviderRegistry;
import org.apache.camel.catalog.VersionHelper;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CamelJBangPlugin;
import org.apache.camel.dsl.jbang.core.common.CommandTree;
import org.apache.camel.dsl.jbang.core.common.Plugin;
import org.apache.camel.dsl.jbang.run.common.PluginExporter;
import org.apache.camel.dsl.jbang.run.common.Printer;
import org.citrusframework.CitrusVersion;
import org.citrusframework.jbang.cli.CitrusJBangMain;
import org.citrusframework.jbang.cli._AeshMetadataRegistry;

@CamelJBangPlugin(name = "camel-jbang-plugin-test", firstVersion = "4.14.0", appliesTo = { "export" })
public class TestPlugin implements Plugin {

    public static final String TEST_DIR = "test";

    @Override
    public void customize(CommandTree commandLine, CamelJBangMain main) {
        // the CLI tells aesh to use only its own metadata registry (no class path scan), so register the generated
        // aesh metadata of the Citrus commands, which the commands of this plugin extend (see TestInit)
        MetadataProviderRegistry.register(new _AeshMetadataRegistry());

        CitrusJBangMain citrus = new CitrusJBangMain();
        citrus.withPrinter(new PipedPrinter(main.getOut()));

        commandLine.addSubcommand("test", new CommandTree(new TestCommand(main))
                .addSubcommand("init", new CommandTree(new TestInit(citrus)))
                .addSubcommand("inspect", new CommandTree(new TestInspect(citrus)))
                .addSubcommand("run", new CommandTree(new TestRun(citrus)))
                .addSubcommand("ps", new CommandTree(new TestListTests(citrus)), "ls")
                .addSubcommand("agent", new CommandTree(new TestAgent(citrus))
                        .addSubcommand("start", new CommandTree(new TestAgentStart(citrus)))
                        .addSubcommand("run", new CommandTree(new TestAgentRun(citrus)))
                        .addSubcommand("stop", new CommandTree(new TestAgentStop(citrus)))));
    }

    @Override
    public Optional<PluginExporter> getExporter() {
        return Optional.of(new TestPluginExporter());
    }

    static boolean isCamelLauncherRuntime() {
        return Boolean.parseBoolean(System.getProperty("camel.launcher", "false"));
    }

    /**
     * Executes a Citrus command with the plugin class loader set as the thread context class loader, restoring the
     * original class loader afterward. Also sets the Citrus and Camel version system properties that Citrus commands
     * use for Maven artifact resolution. In the Camel Launcher runtime the class loader is already correct.
     */
    static CommandResult withClassLoader(
            CitrusCommandAction action, CommandInvocation invocation)
            throws CommandException, InterruptedException {
        System.setProperty("citrus.jbang.version", CitrusVersion.version());
        System.setProperty("citrus.camel.jbang.version", new VersionHelper().getVersion());

        if (!isCamelLauncherRuntime()) {
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(TestPlugin.class.getClassLoader());
                return action.execute(invocation);
            } finally {
                Thread.currentThread().setContextClassLoader(tccl);
            }
        }
        return action.execute(invocation);
    }

    @SuppressWarnings("unchecked")
    static <T> T readField(Class<?> clazz, String name, Object instance) throws ReflectiveOperationException {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(instance);
    }

    @FunctionalInterface
    interface CitrusCommandAction {
        CommandResult execute(CommandInvocation invocation)
                throws CommandException, InterruptedException;
    }

    /**
     * Delegates Citrus printer API methods to Camel printer implementation.
     *
     * @param delegate the Camel printer.
     */
    private record PipedPrinter(Printer delegate) implements org.citrusframework.jbang.cli.Printer {
        @Override
        public void println() {
            delegate.println();
        }

        @Override
        public void println(String line) {
            delegate.println(line);
        }

        @Override
        public void print(String output) {
            delegate.print(output);
        }

        @Override
        public void printf(String format, Object... args) {
            delegate.printf(format, args);
        }

        @Override
        public void printErr(String message) {
            // errors go to the diagnostics channel (stderr) of the Camel printer
            delegate.printErr(message);
        }
    }
}
