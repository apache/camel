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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.aesh.command.DefaultValueProvider;
import org.aesh.command.impl.internal.ProcessedOption;
import org.apache.camel.dsl.jbang.run.common.CamelHome;
import org.apache.camel.dsl.jbang.run.common.Printer;
import org.apache.camel.util.OrderedProperties;

/**
 * Helper for CLI command line.
 */
public final class CommandLineHelper {

    /** Paths whose malformed-config warning has already been printed this JVM run, to avoid repeating it. */
    private static final Set<String> WARNED_PATHS = ConcurrentHashMap.newKeySet();

    public static final String USER_CONFIG = ".camel-cli.properties";
    public static final String LOCAL_USER_CONFIG = "camel-cli.properties";
    public static final String LEGACY_USER_CONFIG = ".camel-jbang-user.properties";
    public static final String LEGACY_LOCAL_USER_CONFIG = "camel-jbang-user.properties";
    public static final String CAMEL_DIR = CamelHome.CAMEL_DIR;
    public static final String CAMEL_JBANG_WORK_DIR = CamelHome.CAMEL_JBANG_WORK_DIR;

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
        migrateLegacyUserConfig(getHomeDir().resolve(LEGACY_USER_CONFIG), getHomeDir().resolve(USER_CONFIG), printer);
        migrateLegacyUserConfig(Paths.get(LEGACY_LOCAL_USER_CONFIG), Paths.get(LOCAL_USER_CONFIG), printer);
    }

    // the notices go to the diagnostics channel, as this runs before any command and stdout may be JSON
    private static void migrateLegacyUserConfig(Path legacy, Path current, Printer printer) {
        try {
            if (!Files.isRegularFile(legacy)) {
                return;
            }
            if (!Files.exists(current)) {
                Files.move(legacy, current);
                printer.printDiagnostic("Migrated legacy settings: " + legacy.getFileName() + " -> " + current.getFileName());
                return;
            }

            long legacyModified = Files.getLastModifiedTime(legacy).toMillis();
            long currentModified = Files.getLastModifiedTime(current).toMillis();
            if (currentModified < legacyModified) {
                printer.printDiagnostic("Note: legacy settings file " + legacy.getFileName()
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
                printer.printDiagnostic(
                        "Merged legacy settings from " + legacy.getFileName() + " into " + current.getFileName());
            }
            Files.delete(legacy);
            printer.printDiagnostic("Removed legacy settings file: " + legacy.getFileName());
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

    public static void augmentWithUserConfiguration(CommandTree commandLine) {
        augmentWithUserConfiguration(commandLine, new Printer.SystemOutPrinter());
    }

    public static void augmentWithUserConfiguration(CommandTree commandLine, Printer printer) {
        Properties properties = loadUserConfiguration(printer);
        if (!properties.isEmpty()) {
            commandLine.setDefaultValueProvider(new CamelUserConfigDefaultValueProvider(properties));
        }
    }

    /**
     * The user configuration as the commands use it: the global file in the user home ({@value #USER_CONFIG}), with the
     * local file of the current directory ({@value #LOCAL_USER_CONFIG}) laid over it, so a key of the local file wins.
     * Before, a local file hid the whole global one, such as its repos or camel-version (CAMEL-26931). A file that
     * cannot be parsed is left out, with a warning (once per file).
     *
     * @param  printer where a file that cannot be parsed is reported (once per file)
     * @return         the configuration, empty when neither file exists
     */
    public static Properties loadUserConfiguration(Printer printer) {
        Properties answer = new OrderedProperties();
        readInto(getUserPropertyFile(false), answer, printer);
        readInto(Paths.get(LOCAL_USER_CONFIG), answer, printer);
        return answer;
    }

    private static void readInto(Path file, Properties answer, Printer printer) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        // in the order of the file, as the configuration keeps it
        Properties properties = new OrderedProperties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (Exception e) {
            // best-effort: a malformed config file (e.g. malformed unicode escape) must never break the CLI;
            // warn the user so they know to fix it, but let the command continue without its defaults.
            // Warn once per path: loadProperties may also warn for the same file, so we deduplicate here.
            String key = file.toAbsolutePath().toString();
            if (WARNED_PATHS.add(key)) {
                printer.printErr(
                        "Warning: could not parse '" + file.toAbsolutePath() + "' (" + e.getMessage()
                                 + "). Edit the file by hand, escaping '\\' as '\\\\' or using '/'.");
            }
            return;
        }
        answer.putAll(properties);
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
        boolean ok = loadProperties(consumer, local, new Printer.SystemOutPrinter());
        if (!ok) {
            // Read-only callers get empty props so the command continues with defaults.
            // Write callers (config set/unset) use the 3-arg overload and check the return value.
            consumer.accept(new OrderedProperties());
        }
    }

    /**
     * Loads user properties from the configuration file. When the file exists but is malformed (e.g. a Windows-style
     * path stored without escaping produces a malformed \\uXXXX sequence), the method warns via the given printer and
     * returns {@code false} <b>without</b> calling the consumer. This lets callers that write back to the file (config
     * set, config unset) detect the failure and refuse to overwrite a malformed file.
     *
     * @return {@code true} if the file was loaded successfully (or did not exist), {@code false} if the file exists but
     *         could not be parsed.
     */
    public static boolean loadProperties(Consumer<Properties> consumer, boolean local, Printer printer) {
        Path file = getUserPropertyFile(local);
        if (Files.isRegularFile(file) && Files.exists(file)) {
            Properties prop = new OrderedProperties();
            try (InputStream is = Files.newInputStream(file)) {
                prop.load(is);
            } catch (Exception e) {
                // Warn once per path per JVM run: camel run calls loadProperties from multiple code paths
                // (resolveRepos, augmentWithUserConfiguration, printConfigurationValues) and each would
                // print the same message. Using the absolute path as the key deduplicates the warning.
                String key = file.toAbsolutePath().toString();
                if (WARNED_PATHS.add(key)) {
                    printer.printErr("Warning: could not parse '" + file.toAbsolutePath() + "' (" + e.getMessage()
                                     + "). Edit the file by hand, escaping '\\' as '\\\\' or using '/'.");
                }
                return false;
            }
            consumer.accept(prop);
        }
        return true;
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
            printer.printErr(file.getFileName() + " does not exist");
        }
    }

    /**
     * Get the config file in current directory (local = true) or the default one
     */
    private static Path getUserPropertyFile(boolean local) {
        if (local) {
            return Paths.get(LOCAL_USER_CONFIG);
        } else {
            return getHomeDir().resolve(USER_CONFIG);
        }
    }

    /**
     * Gets the user home directory.
     *
     * @return the user home directory.
     */
    public static Path getHomeDir() {
        return CamelHome.getHomeDir();
    }

    /**
     * Adjust basic home directory where user properties and other settings will be stored. Unit tests may set this in
     * order to create stable and independent tests.
     *
     * @param homeDir the home directory.
     */
    public static void useHomeDir(String homeDir) {
        CamelHome.useHomeDir(homeDir);
    }

    /**
     * The basic Camel directory located in the user home directory.
     *
     * @return file pointing to the camel directory.
     */
    public static Path getCamelDir() {
        return CamelHome.getCamelDir();
    }

    /**
     * Gets the Camel CLI working directory.
     *
     * @return file pointing to the working directory.
     */
    public static Path getWorkDir() {
        return CamelHome.getWorkDir();
    }

    /**
     * The defaults of the options from the user configuration: {@code camel.<command path>.<option>} for one command,
     * else {@code <option>} (as the properties default provider of picocli looked them up). The commands of camel
     * plugin take no defaults from it.
     */
    private static class CamelUserConfigDefaultValueProvider implements DefaultValueProvider {

        private final Properties properties;

        CamelUserConfigDefaultValueProvider(Properties properties) {
            this.properties = properties;
        }

        @Override
        public String defaultValue(ProcessedOption option) {
            CommandTree tree = CommandTree.of(option.parent() != null ? option.parent().getCommand() : null);
            if (tree != null && tree.getParent() != null && "plugin".equals(tree.getParent().getName())) {
                return null;
            }
            List<String> names = new ArrayList<>();
            names.add(option.name());
            names.addAll(option.getAliases());
            if (tree != null) {
                for (String name : names) {
                    String value = properties.getProperty(tree.getQualifiedName().replace(' ', '.') + "." + name);
                    if (value != null) {
                        return value;
                    }
                }
            }
            for (String name : names) {
                String value = properties.getProperty(name);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }
    }

}
