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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.Properties;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.catalog.VersionHelper;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.impl.engine.DefaultFactoryFinder;
import org.apache.camel.main.download.DependencyDownloader;
import org.apache.camel.main.download.DependencyDownloaderClassLoader;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;

/**
 * Helper for command line plugins that add sub-commands and functionality when enabled by the user. Loads and saves
 * plugin configuration from/to the user home directory.
 */
public final class PluginHelper {

    public static final String PLUGIN_CONFIG = ".camel-jbang-plugins.json";

    private static final FactoryFinder FACTORY_FINDER
            = new DefaultFactoryFinder(new DefaultClassResolver(), FactoryFinder.DEFAULT_PATH + "camel-jbang-plugin/");

    private PluginHelper() {
        // prevent instantiation of utility class
    }

    /**
     * Loads the plugin Json configuration from the user home and goes through all configured plugins adding the plugin
     * commands to the current command line. Tries to resolve each plugin from the classpath with the factory finder
     * pattern. If present the plugin is called to customize the command line to add all sub-commands of the plugin.
     *
     * @param commandLine the command line to add commands to
     * @param main        the current Camel JBang main
     */
    public static void addPlugins(CommandLine commandLine, CamelJBangMain main) {
        JsonObject config = getPluginConfig();

        if (config != null) {
            JsonObject plugins = config.getMap("plugins");
            for (String pluginKey : plugins.keySet()) {
                JsonObject properties = plugins.getMap(pluginKey);

                String name = properties.getOrDefault("name", pluginKey).toString();
                String command = properties.getOrDefault("command", name).toString();

                Optional<Plugin> plugin = FACTORY_FINDER.newInstance("camel-jbang-plugin-" + command, Plugin.class);
                if (plugin.isEmpty()) {
                    plugin = downloadPlugin(command, main);
                }
                if (plugin.isPresent()) {
                    plugin.get().customize(commandLine, main);
                } else {
                    main.getOut().println("camel-jbang-plugin-" + command + " not found. Exit");
                    main.quit(1);
                }
            }
        }
    }

    private static Optional<Plugin> downloadPlugin(String command, CamelJBangMain main) {
        DependencyDownloader downloader = new MavenDependencyDownloader();
        DependencyDownloaderClassLoader ddlcl = new DependencyDownloaderClassLoader(PluginHelper.class.getClassLoader());
        downloader.setClassLoader(ddlcl);
        downloader.start();
        String version = new VersionHelper().getVersion();
        // downloads and adds to the classpath
        downloader.downloadDependency("org.apache.camel", "camel-jbang-plugin-" + command, version);
        Optional<Plugin> instance = Optional.empty();
        InputStream in = null;
        String path = FactoryFinder.DEFAULT_PATH + "camel-jbang-plugin/camel-jbang-plugin-" + command;
        try {
            // reads the class name from the plugin dependency
            in = ddlcl.getResourceAsStream(path);
            if (in != null) {
                Properties prop = new Properties();
                prop.load(in);
                String pluginClassName = prop.getProperty("class");
                DefaultClassResolver resolver = new DefaultClassResolver();
                Class<?> pluginClass = resolver.resolveClass(pluginClassName, ddlcl);
                instance = Optional.of(Plugin.class.cast(ObjectHelper.newInstance(pluginClass)));
            } else {
                String gav = String.join(":", "org.apache.camel", "camel-jbang-plugin-" + command, version);
                main.getOut().printf(String.format("ERROR: Failed to read file %s in dependency %s.\n", path, gav));
            }
        } catch (IOException e) {
            throw new RuntimeCamelException(String.format("Failed to read the file %s.", path), e);
        } finally {
            downloader.stop();
            IOHelper.close(in);
        }
        return instance;
    }

    public static JsonObject getOrCreatePluginConfig() {
        return Optional.ofNullable(getPluginConfig()).orElseGet(PluginHelper::createPluginConfig);
    }

    private static JsonObject getPluginConfig() {
        try {
            File f = new File(CommandLineHelper.getHomeDir(), PLUGIN_CONFIG);
            if (f.exists()) {
                try (FileInputStream fis = new FileInputStream(f)) {
                    String text = IOHelper.loadText(fis);
                    return (JsonObject) Jsoner.deserialize(text);
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return null;
    }

    public static JsonObject createPluginConfig() {
        File f = new File(CommandLineHelper.getHomeDir(), PLUGIN_CONFIG);
        JsonObject config = Jsoner.deserialize("{ \"plugins\": {} }", new JsonObject());
        try {
            Files.writeString(f.toPath(), config.toJson(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeCamelException("Failed to create plugin configuration", e);
        }

        return config;
    }

    public static void savePluginConfig(JsonObject plugins) {
        File f = new File(CommandLineHelper.getHomeDir(), PLUGIN_CONFIG);
        try {
            Files.writeString(f.toPath(), plugins.toJson(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeCamelException("Failed to save plugin configuration", e);
        }
    }

    public static void enable(PluginType pluginType) {
        JsonObject pluginConfig = PluginHelper.getOrCreatePluginConfig();
        JsonObject plugins = pluginConfig.getMap("plugins");

        JsonObject kubePlugin = new JsonObject();
        kubePlugin.put("name", pluginType.getName());
        kubePlugin.put("command", pluginType.getCommand());
        kubePlugin.put("description", pluginType.getDescription());
        plugins.put(pluginType.getName(), kubePlugin);

        PluginHelper.savePluginConfig(pluginConfig);
    }
}
