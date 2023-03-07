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
package org.apache.camel.dsl.jbang.core.common;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.camel.util.IOHelper;
import picocli.CommandLine;

/**
 * Helper for CLI command line.
 */
public class CommandLineHelper {

    public static final String USER_CONFIG = ".camel-jbang-user.properties";

    public static void augmentWithUserConfiguration(CommandLine commandLine, String... args) {
        File file = new File(System.getProperty("user.home"), USER_CONFIG);
        if (file.isFile() && file.exists()) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(file);
                Properties prop = new Properties();
                prop.load(fis);
                IOHelper.close(fis);
                if (!prop.isEmpty()) {
                    commandLine.setDefaultValueProvider(new CommandLine.PropertiesDefaultProvider(prop));
                }
            } catch (Exception e) {
                throw new RuntimeException("Cannot load user configuration: " + file);
            } finally {
                IOHelper.close(fis);
            }
        }

        // TODO: if we need to do this (must be slower due all the command options parsing)
        // this filters the list of options to only what the commands support
        // Properties options = augmentConfiguration(configuration, commandLine);
        // commandLine.setDefaultValueProvider(new CommandLine.PropertiesDefaultProvider(options));
    }

    private static Properties augmentConfiguration(Properties configuration, CommandLine commandLine) {
        Properties answer = new Properties();

        // gather for all commands every option they have
        List<String> allOptions
                = commonArgumentList(new ArrayList<>(), commandLine.getSubcommands(), commandLine.getCommandName());
        allOptions.forEach(key -> {
            String[] parts = key.split(Pattern.quote("."));
            String arg = parts[parts.length - 1];
            // for every option see if we have a configuration for that
            String value = configuration.getProperty(arg);
            if (value != null) {
                answer.setProperty(arg, value);
            }
        });
        return configuration;
    }

    private static List<String> commonArgumentList(
            List<String> list, Map<String, CommandLine> commandLines, String parentPrefix) {
        commandLines.forEach((name, subCommand) -> {
            subCommand.getCommandSpec().args().stream()
                    .filter(arg -> arg instanceof CommandLine.Model.OptionSpec)
                    .map(arg -> (CommandLine.Model.OptionSpec) arg)
                    .map(arg -> arg.longestName().replace("--", ""))
                    .forEach(arg -> list.add(generateParameter(parentPrefix, subCommand.getCommandName(), arg)));
            // add arguments for sub commands as well
            list.addAll(
                    commonArgumentList(new ArrayList<>(), subCommand.getSubcommands(), generateParameter(parentPrefix, name)));
        });
        return list;
    }

    private static String generateParameter(String... prefix) {
        return Arrays.stream(prefix)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("."));
    }
}
