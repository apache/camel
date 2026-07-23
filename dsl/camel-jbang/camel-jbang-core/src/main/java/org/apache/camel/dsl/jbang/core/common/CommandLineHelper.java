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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.function.Consumer;

import org.apache.camel.util.HomeHelper;
import org.apache.camel.util.OrderedProperties;
import picocli.CommandLine;

/**
 * Helper for CLI command line.
 */
public final class CommandLineHelper {

    private static volatile Path homeDir = Paths.get(HomeHelper.resolveHomeDir());
    public static final String USER_CONFIG = ".camel-cli.properties";
    public static final String LOCAL_USER_CONFIG = "camel-cli.properties";
    public static final String LEGACY_USER_CONFIG = ".camel-jbang-user.properties";
    public static final String LEGACY_LOCAL_USER_CONFIG = "camel-jbang-user.properties";
    public static final String CAMEL_DIR = ".camel";
    public static final String CAMEL_JBANG_WORK_DIR = ".camel-jbang";

    private CommandLineHelper() {
        super();
    }

    /**
     * Migrates any pre-existing legacy user configuration file ({@value #LEGACY_USER_CONFIG}) to the new name
     * ({@value #USER_CONFIG}), for both the user home directory and the current working directory.
     * <p>
     * The migration is idempotent and best-effort: it renames when only the legacy file exists, merges non-conflicting
     * legacy keys into the current file when the current file is newer or same-age, and leaves both files untouched
     * when the legacy file is newer.
     */
    public static void migrateLegacyUserConfig() {
        migrateLegacyUserConfig(new Printer.SystemOutPrinter());
    }

    public static void migrateLegacyUserConfig(Printer printer) {
        migrateLegacyUserConfig(homeDir.resolve(LEGACY_USER_CONFIG), homeDir.resolve(USER_CONFIG), printer);
        migrateLegacyUserConfig(Paths.get(LEGACY_LOCAL_USER_CONFIG), Paths.get(LOCAL_USER_CONFIG), printer);
    }

    private static void migrateLegacyUserConfig(Path legacy, Path current, Printer printer) {
        try {
            if (!Files.isRegularFile(legacy)) {
                return;
            }
            if (!Files.exists(current)) {
                Files.move(legacy, current);
                printer.println("Migrated legacy settings: " + legacy.getFileName() + " -> " + current.getFileName());
                return;
            }

            long legacyModified = Files.getLastModifiedTime(legacy).toMillis();
            long currentModified = Files.getLastModifiedTime(current).toMillis();
            if (currentModified < legacyModified) {
                printer.println("Note: legacy settings file " + legacy.getFileName()
                                + " is newer than " + current.getFileName()
                                + ". Both files left unchanged. Remove the legacy file manually if no longer needed.");
                return;
            }

            Properties legacyProps = new OrderedProperties();
            try (InputStream is = Files.newInputStream(legacy)) {
                legacyProps.load(is);
            }
            Properties currentProps = new OrderedProperties();
            try (InputStream is = Files.newInputStream(current)) {
                currentProps.load(is);
            }
            boolean merged = false;
            for (String key : legacyProps.stringPropertyNames()) {
                if (currentProps.getProperty(key) == null) {
                    currentProps.setProperty(key, legacyProps.getProperty(key));
                    merged = true;
                }
            }
            if (merged) {
                try (OutputStream os = Files.newOutputStream(current)) {
                    currentProps.store(os, null);
                }
                printer.println("Merged legacy settings from " + legacy.getFileName() + " into " + current.getFileName());
            }
            Files.delete(legacy);
            printer.println("Removed legacy settings file: " + legacy.getFileName());
        } catch (IOException e) {
            // best-effort: a migration hiccup must never block the CLI
        }
    }

    /**
     * Whether a local user configuration file ({@value #LOCAL_USER_CONFIG}) exists in the current working directory.
     * Callers use this to decide whether to read/write the local file instead of the user home file.
     */
    public static boolean hasLocalUserConfig() {
        return Files.exists(Paths.get(LOCAL_USER_CONFIG));
    }

    public static void augmentWithUserConfiguration(CommandLine commandLine) {
        Path file = getUserConfigurationFile();
        if (Files.isRegularFile(file) && Files.exists(file)) {
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException e) {
                commandLine.setDefaultValueProvider(new CamelUserConfigDefaultValueProvider(file));
                return;
            }

            commandLine.setDefaultValueProvider(new CamelUserConfigDefaultValueProvider(properties));
        }
    }

    private static Path getUserConfigurationFile() {
        Path file;
        if (Files.exists(Paths.get(LOCAL_USER_CONFIG))) {
            file = Paths.get(LOCAL_USER_CONFIG);
        } else {
            file = getUserPropertyFile(false);
        }
        return file;
    }

    public static void createPropertyFile(boolean local) throws IOException {
        Path file = getUserPropertyFile(local);
        if (!Files.exists(file)) {
            Files.createFile(file);
        }
    }

    public static void loadProperties(Consumer<Properties> consumer) {
        loadProperties(consumer, false);
    }

    public static void loadProperties(Consumer<Properties> consumer, boolean local) {
        Path file = getUserPropertyFile(local);
        if (Files.isRegularFile(file) && Files.exists(file)) {
            try (InputStream is = Files.newInputStream(file)) {
                Properties prop = new OrderedProperties();
                prop.load(is);
                consumer.accept(prop);
            } catch (Exception e) {
                throw new RuntimeException("Cannot load user configuration: " + file);
            }
        }
    }

    public static void storeProperties(Properties properties, Printer printer, boolean local) {
        Path file = getUserPropertyFile(local);
        if (Files.isRegularFile(file) && Files.exists(file)) {
            try (OutputStream os = Files.newOutputStream(file)) {
                properties.store(os, null);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            printer.println(file.getFileName() + " does not exist");
        }
    }

    /**
     * Get the config file in current directory (local = true) or the default one
     */
    private static Path getUserPropertyFile(boolean local) {
        if (local) {
            return Paths.get(LOCAL_USER_CONFIG);
        } else {
            return homeDir.resolve(USER_CONFIG);
        }
    }

    /**
     * Gets the user home directory.
     *
     * @return the user home directory.
     */
    public static Path getHomeDir() {
        return homeDir;
    }

    /**
     * Adjust basic home directory where user properties and other settings will be stored. Unit tests may set this in
     * order to create stable and independent tests.
     *
     * @param homeDir the home directory.
     */
    public static void useHomeDir(String homeDir) {
        CommandLineHelper.homeDir = Paths.get(homeDir);
    }

    /**
     * The basic Camel directory located in the user home directory.
     *
     * @return file pointing to the camel directory.
     */
    public static Path getCamelDir() {
        return homeDir.resolve(CAMEL_DIR);
    }

    /**
     * Gets the Camel CLI working directory.
     *
     * @return file pointing to the working directory.
     */
    public static Path getWorkDir() {
        return Paths.get(CAMEL_JBANG_WORK_DIR);
    }

    private static class CamelUserConfigDefaultValueProvider extends CommandLine.PropertiesDefaultProvider {

        public CamelUserConfigDefaultValueProvider(Path file) {
            super(file.toFile());
        }

        public CamelUserConfigDefaultValueProvider(Properties properties) {
            super(properties);
        }

        @Override
        public String defaultValue(CommandLine.Model.ArgSpec argSpec) throws Exception {
            // all plugin commands should not support default values from the user configuration file
            CommandLine.Model.CommandSpec parent = argSpec.command().parent();
            if (parent != null && parent.name().equals("plugin")) {
                return null;
            }
            return super.defaultValue(argSpec);
        }
    }

}
