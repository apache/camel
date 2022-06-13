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
import java.util.Objects;
import java.util.Properties;

import picocli.CommandLine;

/**
 * Helper for CLI arguments with profile
 */
public final class ProfileHelper {

    private static final String DEFAULT_PROFILE = "application";
    private static final String PROFILE = "profile";
    private static final String PROPERTY_PREFIX = "camel.jbang";
    private static final String COMMAND_PREFIX = "camel";
    private static final String PROPERTIES_FILENAME = "application.properties";

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
        Properties fileProperties = readProperties();
        if (!fileProperties.isEmpty()) {
            if (!Objects.equals(profile, DEFAULT_PROFILE)) {
                // only show if not default
                System.out.println("Augmenting properties with profile " + profile);
            }
            Properties properties = replacePrefix(fileProperties, profile);
            commandLine.setDefaultValueProvider(new CommandLine.PropertiesDefaultProvider(properties));
        }
    }

    private static Properties replacePrefix(Properties properties, String profile) {
        Properties result = new Properties();
        String profilePrefix = "%" + profile + ".";
        properties.forEach((key, value) -> {
            if (key.toString().startsWith(PROPERTY_PREFIX) && !properties.containsKey(profilePrefix + PROPERTY_PREFIX)) {
                result.put(key.toString().replace(PROPERTY_PREFIX, COMMAND_PREFIX), value);
            } else if (key.toString().startsWith(profilePrefix + PROPERTY_PREFIX)) {
                result.put(key.toString().replace(profilePrefix + PROPERTY_PREFIX, COMMAND_PREFIX), value);
            }
        });
        return result;
    }

    private static Properties readProperties() {
        File defaultsFile = new File(PROPERTIES_FILENAME);
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
