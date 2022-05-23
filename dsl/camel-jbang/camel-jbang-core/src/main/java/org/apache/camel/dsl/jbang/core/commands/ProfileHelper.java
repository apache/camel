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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import picocli.CommandLine;

/**
 * Helper for CLI arguments with profile
 */
public final class ProfileHelper {

    private static final String PROPERTIES_FILE_EXTENSION = ".properties";
    private static final String DEFAULT_PROFILE = "application";
    private static final String PROFILE = "profile";
    private static final String PROPERTY_PREFIX = "camel.jbang";
    private static final String COMMAND_PREFIX = "camel";
    private static final String COMMON_PREFIX = COMMAND_PREFIX + ".project.";
    private static final List<String> COMMON_ARGUMENTS = List.of("namespace", "name", "version");
    private static String propertiesFilename = "application.properties";

    private ProfileHelper() {
    }

    public static String getProfile(String... args) {
        CommandLine.ParseResult results = new CommandLine(new Profile())
                .setStopAtUnmatched(false)
                .setStopAtPositional(false).parseArgs(args);
        if (results.hasMatchedOption(PROFILE)) {
            return results.matchedOption(PROFILE).getValue().toString();
        } else {
            return DEFAULT_PROFILE;
        }
    }

    public static void augmentWithProperties(CommandLine commandLine, String profile, String... args) {
        propertiesFilename = profile + PROPERTIES_FILE_EXTENSION;

        Properties fileProperties = readProperties();
        if (!fileProperties.isEmpty()) {
            if (!Objects.equals(profile, DEFAULT_PROFILE)) {
                // only show if not default
                System.out.println("Augmenting properties with profile " + profile);
            }
            Properties properties = replacePrefix(fileProperties);
            Properties augmentedProperties = augmentProperties(properties, commandLine);
            commandLine.setDefaultValueProvider(new CommandLine.PropertiesDefaultProvider(augmentedProperties));
        }
    }

    private static Properties augmentProperties(Properties properties, final CommandLine commandLine) {
        List<String> commonArgumentList
                = commonArgumentList(new ArrayList<>(), commandLine.getSubcommands(), commandLine.getCommandName());
        commonArgumentList.forEach(key -> {
            if (!properties.contains(key)) {
                String[] parts = key.split(Pattern.quote("."));
                String arg = parts[parts.length - 1];
                if (COMMON_ARGUMENTS.contains(arg) && properties.containsKey(COMMON_PREFIX + arg)) {
                    String value = properties.getProperty(COMMON_PREFIX + arg);
                    properties.put(key, value);
                }
            }
        });
        return properties;
    }

    private static List<String> commonArgumentList(
            List<String> list, Map<String, CommandLine> commandLines, String parentPrefix) {
        commandLines.forEach((name, subCommand) -> {
            subCommand.getCommandSpec().args().stream()
                    .filter(arg -> arg instanceof CommandLine.Model.OptionSpec)
                    .map(arg -> (CommandLine.Model.OptionSpec) arg)
                    .filter(arg -> COMMON_ARGUMENTS.contains(arg.longestName().replace("--", "")))
                    .map(arg -> arg.longestName().replace("--", ""))
                    .forEach(arg -> list.add(generateParameter(parentPrefix, subCommand.getCommandName(), arg)));

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

    private static Properties replacePrefix(Properties properties) {
        Properties result = new Properties();
        properties.forEach((key, value) -> {
            if (key.toString().startsWith(PROPERTY_PREFIX)) {
                result.put(key.toString().replace(PROPERTY_PREFIX, COMMAND_PREFIX), value);
            }
        });
        return result;
    }

    private static Properties readProperties() {
        File defaultsFile = new File(propertiesFilename);
        Properties properties = new Properties();
        if (defaultsFile.exists()) {
            try (FileInputStream fis = new FileInputStream(defaultsFile)) {
                properties.load(fis);
            } catch (IOException e) {
                // ignore
            }
        }
        return properties;
    }
}
