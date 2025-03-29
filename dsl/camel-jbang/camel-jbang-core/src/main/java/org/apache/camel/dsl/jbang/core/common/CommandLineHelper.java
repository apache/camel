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
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Consumer;

import org.apache.camel.util.IOHelper;
import org.apache.camel.util.OrderedProperties;
import picocli.CommandLine;

/**
 * Helper for CLI command line.
 */
public final class CommandLineHelper {

    private static volatile String homeDir = System.getProperty("user.home");
    public static final String USER_CONFIG = ".camel-jbang-user.properties";
    public static final String CAMEL_DIR = ".camel";
    public static final String CAMEL_JBANG_WORK_DIR = ".camel-jbang";

    private CommandLineHelper() {
        super();
    }

    public static void augmentWithUserConfiguration(CommandLine commandLine, boolean mergeConfigurations) {
        File file = getUserConfigurationFile();
        if (file.isFile() && file.exists()) {
            Properties properties = new Properties();
            try {
                if (mergeConfigurations) {
                    properties.load(new FileReader(getUserPropertyFile(false)));
                }

                properties.load(new FileReader(file));
            } catch (IOException e) {
                commandLine.setDefaultValueProvider(new CamelUserConfigDefaultValueProvider(file));
            }

            commandLine.setDefaultValueProvider(new CamelUserConfigDefaultValueProvider(properties));
        }
    }

    private static File getUserConfigurationFile() {
        File file;
        if (Files.exists(Path.of(USER_CONFIG))) {
            file = new File(USER_CONFIG);
        } else {
            file = getUserPropertyFile(false);
        }
        return file;
    }

    public static void createPropertyFile(boolean local) throws IOException {
        File file = getUserPropertyFile(local);
        if (!file.exists()) {
            file.createNewFile();
        }
    }

    public static void loadProperties(Consumer<Properties> consumer) {
        loadProperties(consumer, false);
    }

    public static void loadProperties(Consumer<Properties> consumer, boolean local) {
        File file = getUserPropertyFile(local);
        if (file.isFile() && file.exists()) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(file);
                Properties prop = new OrderedProperties();
                prop.load(fis);
                consumer.accept(prop);
            } catch (Exception e) {
                throw new RuntimeException("Cannot load user configuration: " + file);
            } finally {
                IOHelper.close(fis);
            }
        }
    }

    public static void storeProperties(Properties properties, Printer printer, boolean local) {
        File file = getUserPropertyFile(local);
        if (file.isFile() && file.exists()) {
            try (FileOutputStream fos = new FileOutputStream(file)) {
                properties.store(fos, null);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            printer.println(USER_CONFIG + " does not exist");
        }
    }

    /**
     * Get the config file in current directory (local = true) or the default one
     *
     * @param  local
     * @return
     */
    private static File getUserPropertyFile(boolean local) {
        if (local) {
            return new File(USER_CONFIG);
        } else {
            return new File(homeDir, USER_CONFIG);
        }
    }

    /**
     * Gets the user home directory.
     *
     * @return the user home directory.
     */
    public static String getHomeDir() {
        return homeDir;
    }

    /**
     * Adjust basic home directory where user properties and other settings will be stored. Unit tests may set this in
     * order to create stable and independent tests.
     *
     * @param homeDir the home directory.
     */
    public static void useHomeDir(String homeDir) {
        CommandLineHelper.homeDir = homeDir;
    }

    /**
     * The basic Camel directory located in the user home directory.
     *
     * @return file pointing to the camel directory.
     */
    public static File getCamelDir() {
        return new File(homeDir, CAMEL_DIR);
    }

    /**
     * Gets the Camel JBang working directory.
     *
     * @return file pointing to the working directory.
     */
    public static File getWorkDir() {
        return new File(CAMEL_JBANG_WORK_DIR);
    }

    private static class CamelUserConfigDefaultValueProvider extends CommandLine.PropertiesDefaultProvider {

        public CamelUserConfigDefaultValueProvider(File file) {
            super(file);
        }

        public CamelUserConfigDefaultValueProvider(Properties properties) {
            super(properties);
        }
    }

}
