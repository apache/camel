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
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import org.apache.camel.util.IOHelper;
import org.apache.camel.util.OrderedProperties;
import org.apache.camel.util.StringHelper;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

public final class RuntimeUtil {

    /**
     * The system property with the directory of the log files, which the logging configurations of the CLI name their
     * files after: the Camel directory of the CLI ({@link CommandLineHelper#getCamelDir()}), where camel run, camel log
     * and the AI tools look for them. It can differ from <tt>~/.camel</tt>, such as for a CLI with a home of its own (a
     * test), or when the user has no home (a container), where <tt>user.home</tt> is <tt>?</tt>.
     */
    public static final String LOG_DIR = "camel.jbang.logDir";

    /**
     * The logging configuration of the CLI with color: the console, and the log file of the process.
     */
    static final String COLOR_CONFIG = "log4j2-color.properties";

    // the logging configuration log4j was configured with, or null when not yet
    private static String configured;
    // whether the command that runs next may configure the logging again (see newCommand)
    private static boolean newCommand;

    private RuntimeUtil() {
    }

    public static void configureLog(
            String level, boolean color, boolean json, boolean script, boolean export, String loggingConfigPath,
            List<String> loggingCategories)
            throws Exception {
        configureLog(level, color, json, script, export, loggingConfigPath, loggingCategories, false);
    }

    public static void configureLog(
            String level, boolean color, boolean json, boolean script, boolean export, String loggingConfigPath,
            List<String> loggingCategories, boolean mcpStdio)
            throws Exception {
        String location;
        if (loggingConfigPath != null) {
            // use custom logging configuration as-is
            location = Path.of(loggingConfigPath).toAbsolutePath().toUri().toString();
        } else if (mcpStdio) {
            location = "log4j2-mcp-stdio.properties";
        } else if (loggingCategories != null && !loggingCategories.isEmpty()) {
            // enrich logging file with custom logging categories
            String name = "log4j2-no-color.properties";
            if (export) {
                name = "log4j2-export.properties";
            } else if (script) {
                name = "log4j2-script.properties";
            } else if (json) {
                name = "log4j2-json.properties";
            } else if (color) {
                name = COLOR_CONFIG;
            }
            InputStream is = RuntimeUtil.class.getClassLoader().getResourceAsStream(name);
            String content = IOHelper.loadText(is);
            IOHelper.close(is);

            StringJoiner sj = new StringJoiner(System.lineSeparator());
            int i = 0;
            for (String lc : loggingCategories) {
                String prefix = "custom" + i++;
                String catName = StringHelper.before(lc, "=", "").trim();
                String catLevel = StringHelper.after(lc, "=", "").trim();
                if ("root".equalsIgnoreCase(catName)) {
                    // log4j allows one root logger only, which the template already defines: a root category is
                    // the root logging level
                    if (!catLevel.isEmpty()) {
                        level = catLevel;
                    }
                    continue;
                }
                if (!catName.isEmpty() && !catLevel.isEmpty()) {
                    sj.add("logger." + prefix + ".name=" + catName);
                    sj.add("logger." + prefix + ".level=" + catLevel);
                    // the category gets the same appenders as the root logger, so it must not also pass its
                    // events up to the root, or every line is printed twice (CAMEL-24701)
                    sj.add("logger." + prefix + ".additivity=false");
                    if (!export && !script) {
                        sj.add("logger." + prefix + ".appenderRef.$1.ref=out");
                    }
                    sj.add("logger." + prefix + ".appenderRef.$2.ref=file");
                }
            }
            content = content + System.lineSeparator() + sj;

            name = CommandLineHelper.CAMEL_JBANG_WORK_DIR + "/log4j2.properties";
            Files.writeString(Paths.get(name), content);
            // the categories are part of what the command asks for
            location = Path.of(name).toAbsolutePath().toUri() + "#" + content.hashCode();
        } else if (export) {
            // use out of the box logging configuration
            location = "log4j2-export.properties";
        } else if (script) {
            location = "log4j2-script.properties";
        } else if (json) {
            location = "log4j2-json.properties";
        } else if (color) {
            location = COLOR_CONFIG;
        } else {
            location = "log4j2-no-color.properties";
        }

        synchronized (RuntimeUtil.class) {
            // log4j resolves the name of a log file when it loads the configuration. Always the Camel directory of the
            // CLI, not an option: camel log and the AI tools read the log of a pid there
            System.setProperty(LOG_DIR, CommandLineHelper.getCamelDir().toString());
            if (configured == null) {
                long pid = ProcessHandle.current().pid();
                System.setProperty("pid", Long.toString(pid));
                // make it possible to manage log4j via JMX and camel-cli
                System.setProperty("log4j2.disable.jmx", "false");
                Configurator.initialize("CamelJBang", withoutHash(location));
                configured = location;
            } else if (newCommand && !configured.equals(location)) {
                // a CLI that runs several commands in this JVM (camel ask, camel shell, the monitor) runs one that
                // logs otherwise, such as export to its log file: log4j is configured once, so configure it again
                Configurator.reconfigure(toUri(withoutHash(location)));
                configured = location;
            }
            // as for a command run on its own, the first configuration of the command is the one it logs with
            newCommand = false;
        }

        setRootLoggingLevel(level);
    }

    /**
     * A CLI that runs several commands in this JVM (camel ask, camel shell, the monitor) runs another one: its first
     * logging configuration is applied, as for a command run on its own, also when log4j was configured otherwise by a
     * command before.
     */
    public static void newCommand() {
        synchronized (RuntimeUtil.class) {
            newCommand = true;
        }
    }

    private static String withoutHash(String location) {
        int pos = location.lastIndexOf('#');
        return pos > 0 ? location.substring(0, pos) : location;
    }

    // a configuration on the classpath, or a file
    private static URI toUri(String location) throws URISyntaxException {
        if (location.startsWith("file:")) {
            return new URI(location);
        }
        URL url = RuntimeUtil.class.getClassLoader().getResource(location);
        return url != null ? url.toURI() : new URI(location);
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

    public static void loadProperties(Properties properties, Path path) throws IOException {
        if (Files.exists(path)) {
            try (final var inputStream = Files.newInputStream(path)) {
                properties.load(inputStream);
            }
        }
    }

    public static void loadProperties(Properties properties, File file) throws IOException {
        loadProperties(properties, file.toPath());
    }

    public static List<String> loadPropertiesLines(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }

        List<String> lines = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
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

    public static List<String> loadPropertiesLines(File file) throws IOException {
        return loadPropertiesLines(file.toPath());
    }

    public static List<String> getCommaSeparatedPropertyAsList(Properties props, String key, List<String> defaultValue) {
        var value = props.getProperty(key);
        return Optional.ofNullable(value)
                .map(val -> Arrays.asList(val.split(",")))
                .filter(tok -> !tok.isEmpty())
                .orElse(defaultValue);
    }

    public static String getDependencies(Properties properties) {
        String deps = properties != null ? properties.getProperty(CamelJBangConstants.DEPENDENCIES) : null;
        if (deps != null) {
            deps = deps.trim();
            if (!deps.isEmpty() && deps.charAt(0) == ',') {
                deps = deps.substring(1);
            }
            if (!deps.isEmpty() && deps.charAt(deps.length() - 1) == ',') {
                deps = deps.substring(0, deps.lastIndexOf(","));
            }
        } else {
            deps = "";
        }
        return deps;
    }

    public static String[] getDependenciesAsArray(Properties properties) {
        String deps = getDependencies(properties);
        return deps.isEmpty() ? new String[0] : deps.split(",");
    }

    public static String getPid() {
        return String.valueOf(ProcessHandle.current().pid());
    }

}
