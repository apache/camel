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
package org.apache.camel.dsl.jbang.core.commands.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.aesh.command.CommandDefinition;
import org.aesh.command.impl.internal.ProcessedOption;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.CommandTree;
import org.apache.camel.util.StringHelper;

@CommandDefinition(name = "set", description = "Set user configuration value", generateHelp = true,
                   footer = {
                           "Examples:",
                           "  camel config set camel-version=4.10.0",
                           "  camel config set runtime=spring-boot" })
public class ConfigSet extends CamelCommand {

    @Argument(required = true, description = "Configuration parameter (ex. key=value)")
    String configuration;

    @Option(name = "global", hasValue = false, description = "Use global or local configuration")
    boolean global = true;

    public ConfigSet(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        return setConfiguration(!global);
    }

    private int setConfiguration(boolean local) throws IOException {
        String name = StringHelper.before(configuration, "=");
        if (name == null || name.isBlank() || configuration.split("=").length == 1) {
            throw CliException.usage("Configuration parameter not in key=value format: " + configuration);
        }
        checkValue(name.trim(), StringHelper.after(configuration, "=").trim());
        CommandLineHelper.createPropertyFile(local);

        if (!CommandLineHelper.loadProperties(properties -> {
            String key = StringHelper.before(configuration, "=").trim();
            String value = StringHelper.after(configuration, "=").trim();
            properties.put(key, value);
            CommandLineHelper.storeProperties(properties, printer(), local);
        }, local, printer())) {
            printer().printErr("Cannot set configuration: the configuration file is malformed. Fix it by hand first.");
            return CliExitCode.FAILURE;
        }

        return CliExitCode.OK;
    }

    /**
     * Checks the value against the options the key is the default of: {@code <option>} for every command with that
     * option, {@code camel.<command path>.<option>} for one command. A value an option does not take, such as
     * runtime=springboot, was stored, and then every command with that option failed (CAMEL-26931). A key that no
     * command has is stored with a warning, as nothing uses it.
     */
    private void checkValue(String key, String value) {
        if (CommandTree.of(this) == null) {
            // not run from a command line, such as in a test of this command alone
            return;
        }
        // all the commands: the command line of a run has only the command it runs, for a fast start
        CommandTree root = getMain().newCommandLine();
        boolean[] known = new boolean[1];
        String problem = checkValue(root, key, value, known, new HashSet<>());
        if (problem != null) {
            throw CliException.usage("Invalid value " + key + "=" + value + ": " + problem);
        }
        if (!known[0]) {
            printer().printDiagnostic("Warning: no command of the CLI has the option " + key
                                      + "; the value is stored, but only a command of a plugin can use it");
        }
    }

    private static String checkValue(CommandTree tree, String key, String value, boolean[] known, Set<CommandTree> seen) {
        if (!seen.add(tree)) {
            return null;
        }
        try {
            String prefix = tree.getQualifiedName().replace(' ', '.') + ".";
            for (ProcessedOption option : tree.processedCommand().getOptions()) {
                List<String> names = new ArrayList<>();
                names.add(option.name());
                names.addAll(option.getAliases());
                for (String name : names) {
                    if (name.equals(key) || (prefix + name).equals(key)) {
                        known[0] = true;
                        String problem = CommandLineHelper.invalidValue(option, value);
                        if (problem != null) {
                            return "--" + option.name() + " of " + tree.getQualifiedName() + " does not take it ("
                                   + problem + ")";
                        }
                    }
                }
            }
        } catch (Exception e) {
            // a command whose model cannot be built is left out of the check
        }
        for (CommandTree sub : tree.getSubcommands().values()) {
            String problem = checkValue(sub, key, value, known, seen);
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }
}
