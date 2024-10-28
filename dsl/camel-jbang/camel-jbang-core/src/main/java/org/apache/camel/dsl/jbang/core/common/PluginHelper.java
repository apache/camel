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
import java.util.function.Supplier;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.impl.engine.DefaultFactoryFinder;
import org.apache.camel.main.download.DependencyDownloader;
import org.apache.camel.main.download.DependencyDownloaderClassLoader;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.tooling.maven.MavenGav;
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
    public static void addPlugins(CommandLine commandLine, CamelJBangMain main, String... args) {
        JsonObject config = getPluginConfig();

        // first arg is the command name (ie camel generate xxx)
        String target = args != null && args.length > 0 ? args[0] : null;

        if (config != null) {
            CamelCatalog catalog = new DefaultCamelCatalog();
            String version = catalog.getCatalogVersion();
            JsonObject plugins = config.getMap("plugins");

            for (String pluginKey : plugins.keySet()) {
                JsonObject properties = plugins.getMap(pluginKey);

                final String name = properties.getOrDefault("name", pluginKey).toString();
                final String command = properties.getOrDefault("command", name).toString();
                final String firstVersion = properties.getOrDefault("firstVersion", "").toString();

                // only load the plugin if the command-line is calling this plugin
                if (target != null && !target.equals(command)) {
                    continue;
                }

                // check if plugin version can be loaded (cannot if we use an older camel version than the plugin)
                if (!version.isBlank() && !firstVersion.isBlank()) {
                    versionCheck(main, version, firstVersion, command);
                }

                Optional<Plugin> plugin = FACTORY_FINDER.newInstance("camel-jbang-plugin-" + command, Plugin.class);
                if (plugin.isEmpty()) {
                    final MavenGav mavenGav = dependencyAsMavenGav(properties);
                    final String group = extractGroup(mavenGav, "org.apache.camel");
                    final String depVersion = extractVersion(mavenGav, version);

                    plugin = downloadPlugin(command, main, depVersion, group);
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

    private static MavenGav dependencyAsMavenGav(JsonObject properties) {
        final Object dependency = properties.get("dependency");
        if (dependency == null) {
            return null;
        }

        return MavenGav.parseGav(dependency.toString());
    }

    private static void versionCheck(CamelJBangMain main, String version, String firstVersion, String command) {
        // compare versions without SNAPSHOT
        String source = version;
        if (source.endsWith("-SNAPSHOT")) {
            source = source.replace("-SNAPSHOT", "");
        }
        boolean accept = VersionHelper.isGE(source, firstVersion);
        if (!accept) {
            main.getOut().println("Cannot load plugin camel-jbang-plugin-" + command + " with version: " + version
                                  + " because plugin has first version: " + firstVersion + ". Exit");
            main.quit(1);
        }
    }

    private static Optional<Plugin> downloadPlugin(String command, CamelJBangMain main, String version, String group) {
        DependencyDownloader downloader = new MavenDependencyDownloader();
        DependencyDownloaderClassLoader ddlcl = new DependencyDownloaderClassLoader(PluginHelper.class.getClassLoader());
        downloader.setClassLoader(ddlcl);
        downloader.start();
        // downloads and adds to the classpath
        downloader.downloadDependency(group, "camel-jbang-plugin-" + command, version);
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
                String gav = String.join(":", group, "camel-jbang-plugin-" + command, version);
                main.getOut().printf(String.format("ERROR: Failed to read file %s in dependency %s%n", path, gav));
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
        kubePlugin.put("firstVersion", pluginType.getFirstVersion());
        plugins.put(pluginType.getName(), kubePlugin);

        PluginHelper.savePluginConfig(pluginConfig);
    }

    /**
     * Extracts information from the GAV model
     *
     * @param  gav         An instance of a Maven GAV model
     * @param  defaultInfo the default if null or not available
     * @return             the information
     */
    private static String doExtractInfo(MavenGav gav, String defaultInfo, Supplier<String> supplier) {
        if (gav != null) {
            final String info = supplier.get();
            if (info != null) {
                return info;
            }
        }

        return defaultInfo;
    }

    /**
     * Extracts the group from g:a:v
     *
     * @param  gav          An instance of a Maven GAV model
     * @param  defaultGroup the default if null or not available
     * @return              The group in g:a:v. That is, "g".
     */
    private static String extractGroup(MavenGav gav, String defaultGroup) {
        return doExtractInfo(gav, defaultGroup, gav != null ? gav::getGroupId : () -> "");
    }

    /**
     * Extracts the version from g:a:v
     *
     * @param  gav            An instance of a Maven GAV model
     * @param  defaultVersion the default if null or not available
     * @return                The group in g:a:v. That is, "v".
     */
    private static String extractVersion(MavenGav gav, String defaultVersion) {
        return doExtractInfo(gav, defaultVersion, gav != null ? gav::getVersion : () -> "");
    }
}
