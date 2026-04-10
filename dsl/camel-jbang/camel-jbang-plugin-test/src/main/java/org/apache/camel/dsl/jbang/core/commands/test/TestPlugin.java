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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.ExportHelper;
import org.apache.camel.dsl.jbang.core.common.CamelJBangPlugin;
import org.apache.camel.dsl.jbang.core.common.Plugin;
import org.apache.camel.dsl.jbang.core.common.PluginExporter;
import org.apache.camel.util.IOHelper;
import org.citrusframework.CitrusVersion;
import org.citrusframework.jbang.JBangSettings;
import org.citrusframework.jbang.JBangSupport;
import org.citrusframework.jbang.ProcessAndOutput;
import picocli.CommandLine;

@CamelJBangPlugin(name = "camel-jbang-plugin-test", firstVersion = "4.14.0")
public class TestPlugin implements Plugin {

    @Override
    public void customize(CommandLine commandLine, CamelJBangMain main) {
        commandLine.setExecutionStrategy(new CitrusExecutionStrategy(main))
                .addSubcommand("test", new CommandLine(new TestCommand(main))
                        .setUnmatchedArgumentsAllowed(true)
                        .setUnmatchedOptionsAllowedAsOptionParameters(true));
    }

    @Override
    public Optional<PluginExporter> getExporter() {
        return Optional.of(new TestPluginExporter());
    }

    /**
     * Command execution strategy delegates to Citrus JBang for subcommands like init or run. Performs special command
     * preparations and makes sure to run the proper Citrus version for this Camel release.
     *
     * @param main Camel JBang main that provides the output printer.
     */
    private record CitrusExecutionStrategy(CamelJBangMain main) implements CommandLine.IExecutionStrategy {

        public static final String TEST_DIR = "test";

        @Override
        public int execute(CommandLine.ParseResult parseResult)
                throws CommandLine.ExecutionException, CommandLine.ParameterException {

            String command;
            List<String> args = Collections.emptyList();

            if (parseResult.originalArgs().size() > 2) {
                command = parseResult.originalArgs().get(1);
                args = parseResult.originalArgs().subList(2, parseResult.originalArgs().size());
            } else if (parseResult.originalArgs().size() == 2) {
                command = parseResult.originalArgs().get(1);
            } else {
                // run help command by default
                command = "--help";
            }

            JBangSupport citrus = JBangSupport.jbang().app(JBangSettings.getApp())
                    .withSystemProperty("citrus.jbang.version", CitrusVersion.version());

            // Prepare commands
            if ("init".equals(command)) {
                return executeInitCommand(citrus, args);
            } else if ("run".equals(command)) {
                return executeRunCommand(citrus, args);
            }

            return execute(citrus, command, args);
        }

        /**
         * Prepare and execute init command. Automatically uses test subfolder as a working directory for creating new
         * tests. Automatically adds a jbang.properties configuration to add required Camel Citrus dependencies.
         */
        private int executeInitCommand(JBangSupport citrus, List<String> args) {
            Path currentDir = Paths.get(".");
            Path workingDir;
            // Automatically set test subfolder as a working directory
            if (TEST_DIR.equals(currentDir.getFileName().toString())) {
                // current directory is already the test subfolder
                workingDir = currentDir;
            } else if (currentDir.resolve(TEST_DIR).toFile().exists()) {
                // navigate to existing test subfolder
                workingDir = currentDir.resolve(TEST_DIR);
                citrus.workingDir(workingDir);
            } else if (currentDir.resolve(TEST_DIR).toFile().mkdirs()) {
                // create test subfolder and navigate to it
                workingDir = currentDir.resolve(TEST_DIR);
                citrus.workingDir(workingDir);
            } else {
                throw new RuntimeCamelException("Cannot create test working directory in: " + currentDir);
            }

            // Create jbang properties with default dependencies if not present
            if (!workingDir.resolve("jbang.properties").toFile().exists()) {
                Path jbangProperties = workingDir.resolve("jbang.properties");
                try (InputStream is
                        = TestPlugin.class.getClassLoader().getResourceAsStream("templates/jbang-properties.tmpl")) {
                    String context = IOHelper.loadText(is);

                    context = context.replaceAll("\\{\\{ \\.CitrusVersion }}", CitrusVersion.version());

                    ExportHelper.safeCopy(new ByteArrayInputStream(context.getBytes(StandardCharsets.UTF_8)), jbangProperties);
                } catch (Exception e) {
                    main.getOut().println("Failed to create jbang.properties for tests in:" + jbangProperties);
                }
            }

            return execute(citrus, "init", args);
        }

        /**
         * Prepare and execute Citrus run command. Automatically navigates to test subfolder if it is present and uses
         * this as a working directory. Runs command asynchronous streaming logs to the main output of this Camel JBang
         * process.
         */
        private int executeRunCommand(JBangSupport citrus, List<String> args) {
            Path currentDir = Paths.get(".");
            List<String> runArgs = new ArrayList<>(args);
            // automatically navigate to test subfolder for test execution
            if (currentDir.resolve(TEST_DIR).toFile().exists()) {
                // set test subfolder as working directory
                citrus.workingDir(currentDir.resolve(TEST_DIR));

                // remove test folder prefix in test file path if present
                if (!args.isEmpty() && args.get(0).startsWith(TEST_DIR + "/")) {
                    runArgs = new ArrayList<>(args.subList(1, args.size()));
                    runArgs.add(0, args.get(0).substring((TEST_DIR + "/").length()));
                }
            }

            citrus.withOutputListener(output -> main.getOut().print(output));
            ProcessAndOutput pao = citrus.runAsync("run", runArgs);
            try {
                pao.waitFor();
            } catch (InterruptedException e) {
                main.getOut().printErr("Interrupted while running Citrus command", e);
            }

            return pao.getProcess().exitValue();
        }

        /**
         * Uses given Citrus JBang instance to run the given command using the given arguments.
         *
         * @return exit code of the command process.
         */
        private int execute(JBangSupport citrus, String command, List<String> args) {
            ProcessAndOutput pao = citrus.run(command, args);
            main.getOut().print(pao.getOutput());
            return pao.getProcess().exitValue();
        }
    }
}
