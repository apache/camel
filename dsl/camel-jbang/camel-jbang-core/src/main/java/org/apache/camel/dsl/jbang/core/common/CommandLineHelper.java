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
    public static final String USER_CONFIG = ".camel-jbang-user.properties";
    public static final String LOCAL_USER_CONFIG = "camel-jbang-user.properties";
    public static final String CAMEL_DIR = ".camel";
    public static final String CAMEL_JBANG_WORK_DIR = ".camel-jbang";

    private CommandLineHelper() {
        super();
    }

    public static void augmentWithUserConfiguration(CommandLine commandLine) {
        Path file = getUserConfigurationFile();
        if (Files.isRegularFile(file) && Files.exists(file)) {
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException e) {
                commandLine.setDefaultValueProvider(new CamelUserConfigDefaultValueProvider(file));
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
     * Gets the Camel JBang working directory.
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
    }

}
