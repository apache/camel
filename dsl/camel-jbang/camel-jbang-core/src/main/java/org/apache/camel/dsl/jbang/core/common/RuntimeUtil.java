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
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.util.OrderedProperties;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

public final class RuntimeUtil {

    private static final AtomicBoolean INIT_DONE = new AtomicBoolean();

    private RuntimeUtil() {
    }

    public static void configureLog(
            String level, boolean color, boolean json, boolean script, boolean export, String loggingConfigPath) {
        if (INIT_DONE.compareAndSet(false, true)) {
            long pid = ProcessHandle.current().pid();
            System.setProperty("pid", Long.toString(pid));

            if (loggingConfigPath != null) {
                Configurator.initialize("CamelJBang", "file://" + Path.of(loggingConfigPath).toAbsolutePath());
            } else if (export) {
                Configurator.initialize("CamelJBang", "log4j2-export.properties");
            } else if (script) {
                Configurator.initialize("CamelJBang", "log4j2-script.properties");
            } else if (json) {
                Configurator.initialize("CamelJBang", "log4j2-json.properties");
            } else if (color) {
                Configurator.initialize("CamelJBang", "log4j2.properties");
            } else {
                Configurator.initialize("CamelJBang", "log4j2-no-color.properties");
            }
        }

        setRootLoggingLevel(level);
    }

    public static void setRootLoggingLevel(String level) {
        if (level != null) {
            level = level.toLowerCase();

            switch (level) {
                case "off":
                    Configurator.setRootLevel(Level.OFF);
                    break;
                case "trace":
                    Configurator.setRootLevel(Level.TRACE);
                    break;
                case "debug":
                    Configurator.setRootLevel(Level.DEBUG);
                    break;
                case "info":
                    Configurator.setRootLevel(Level.INFO);
                    break;
                case "warn":
                    Configurator.setRootLevel(Level.WARN);
                    break;
                case "error":
                    Configurator.setRootLevel(Level.ERROR);
                    break;
                case "fatal":
                    Configurator.setRootLevel(Level.FATAL);
                    break;
                default: {
                    Configurator.setRootLevel(Level.INFO);
                }
            }
        }
    }

    public static void loadProperties(Properties properties, File file) throws IOException {
        if (file.exists()) {
            try (final FileInputStream fileInputStream = new FileInputStream(file)) {
                properties.load(fileInputStream);
            }
        }
    }

    public static List<String> loadPropertiesLines(File file) throws IOException {
        if (!file.exists()) {
            return new ArrayList<>();
        }

        List<String> lines = new ArrayList<>();
        for (String line : Files.readAllLines(file.toPath())) {
            // need to use java.util.Properties to read raw value and un-escape
            Properties prop = new OrderedProperties();
            prop.load(new StringReader(line));

            for (String key : prop.stringPropertyNames()) {
                String value = prop.getProperty(key);
                if (value != null) {
                    lines.add(key + "=" + value);
                }
            }
        }
        return lines;
    }

    public static String getDependencies(Properties properties) {
        String deps = properties != null ? properties.getProperty("camel.jbang.dependencies") : null;
        if (deps != null) {
            deps = deps.trim();
            if (deps.length() > 0 && deps.charAt(0) == ',') {
                deps = deps.substring(1);
            }
            if (deps.length() > 0 && deps.charAt(deps.length() - 1) == ',') {
                deps = deps.substring(0, deps.lastIndexOf(","));
            }
        } else {
            deps = "";
        }
        return deps;
    }

    public static String getPid() {
        return String.valueOf(ProcessHandle.current().pid());
    }

}
