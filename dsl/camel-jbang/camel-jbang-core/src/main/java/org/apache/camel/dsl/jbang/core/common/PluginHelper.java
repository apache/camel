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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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
    public static final String PLUGIN_SERVICE_DIR = "META-INF/services/org/apache/camel/camel-jbang-plugin/";

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
        // first arg is the command name (ie camel generate xxx)
        String target = args != null && args.length > 0 ? args[0] : null;

        // First, try to load embedded plugins from classpath (fat-jar scenario)
        boolean foundEmbeddedPlugins = false;
        try {
            foundEmbeddedPlugins = addEmbeddedPlugins(commandLine, main, target);
        } catch (Exception e) {
            // Ignore errors in embedded plugin loading
        }

        // If we found embedded plugins and we're looking for a specific target,
        // check if it was satisfied by embedded plugins
        if (foundEmbeddedPlugins && target != null && !"shell".equals(target)) {
            // Check if the target command was added by embedded plugins
            if (commandLine.getSubcommands().containsKey(target)) {
                return; // Target satisfied by embedded plugins, no need for JSON config
            }
        }

        // Fall back to JSON configuration for additional or missing plugins
        JsonObject config = getPluginConfig();
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
                if (target != null && !"shell".equals(target) && !target.equals(command)) {
                    continue;
                }

                // Skip if this plugin was already loaded from embedded plugins
                if (foundEmbeddedPlugins && commandLine.getSubcommands().containsKey(command)) {
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

    static void versionCheck(CamelJBangMain main, String version, String firstVersion, String command) {
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
        downloader.downloadDependencyWithParent("org.apache.camel:camel-jbang-parent:" + version, group,
                "camel-jbang-plugin-" + command, version);
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

    static JsonObject getPluginConfig() {
        try {
            Path f = CommandLineHelper.getHomeDir().resolve(PLUGIN_CONFIG);
            if (Files.exists(f)) {
                String text = Files.readString(f);
                return (JsonObject) Jsoner.deserialize(text);
            }
        } catch (Exception e) {
            // ignore
        }

        return null;
    }

    public static JsonObject createPluginConfig() {
        Path f = CommandLineHelper.getHomeDir().resolve(PLUGIN_CONFIG);
        JsonObject config = Jsoner.deserialize("{ \"plugins\": {} }", new JsonObject());
        try {
            Files.writeString(f, config.toJson(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeCamelException("Failed to create plugin configuration", e);
        }

        return config;
    }

    public static void savePluginConfig(JsonObject plugins) {
        Path f = CommandLineHelper.getHomeDir().resolve(PLUGIN_CONFIG);
        try {
            Files.writeString(f, plugins.toJson(),
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

    /**
     * Scans the classpath for embedded plugins and loads them directly. This bypasses the JSON configuration and
     * download phase for fat-jar scenarios.
     */
    public static boolean addEmbeddedPlugins(CommandLine commandLine, CamelJBangMain main, String target) {
        boolean foundAny = false;
        try {
            ClassLoader classLoader = PluginHelper.class.getClassLoader();
            String serviceDir = PLUGIN_SERVICE_DIR;

            // If we didn't find individual services, try scanning jar
            if (!foundAny) {
                Enumeration<URL> resources = classLoader.getResources(serviceDir);
                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    if (url.getProtocol().equals("jar")) {
                        foundAny = scanJarForPlugins(commandLine, main, target, classLoader, url);
                        break; // Found jar, no need to continue
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors in classpath scanning
        }
        return foundAny;
    }

    private static boolean scanJarForPlugins(
            CommandLine commandLine, CamelJBangMain main, String target,
            ClassLoader classLoader, URL jarUrl) {
        boolean foundAny = false;
        try {
            String jarPath = jarUrl.getPath();
            if (jarPath.startsWith("file:")) {
                jarPath = jarPath.substring(5);
            }
            if (jarPath.contains("!")) {
                jarPath = jarPath.substring(0, jarPath.indexOf("!"));
            }

            try (JarFile jarFile = new JarFile(jarPath)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    if (entryName.startsWith(PLUGIN_SERVICE_DIR)
                            && !entryName.endsWith("/")) {
                        String pluginName = entryName.substring(entryName.lastIndexOf("/") + 1);
                        URL serviceUrl = classLoader.getResource(entryName);
                        if (serviceUrl != null) {
                            if (loadPluginFromService(commandLine, main, target, classLoader, serviceUrl, pluginName)) {
                                foundAny = true;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors
        }
        return foundAny;
    }

    private static boolean loadPluginFromService(
            CommandLine commandLine, CamelJBangMain main, String target,
            ClassLoader classLoader, URL serviceUrl, String pluginName) {
        try (InputStream is = serviceUrl.openStream()) {
            Properties prop = new Properties();
            prop.load(is);
            String pluginClassName = prop.getProperty("class");
            if (pluginClassName != null) {
                Class<?> pluginClass = classLoader.loadClass(pluginClassName);
                Plugin plugin = (Plugin) pluginClass.getDeclaredConstructor().newInstance();

                // Extract command name from plugin name
                String command = extractCommandFromPlugin(pluginClass, pluginName);

                // Only load the plugin if the command-line is calling this plugin or if target is null (shell mode)
                if (target != null && !"shell".equals(target) && !target.equals(command)) {
                    return false;
                }

                // Check version compatibility if needed
                CamelJBangPlugin annotation = pluginClass.getAnnotation(CamelJBangPlugin.class);
                if (annotation != null) {
                    CamelCatalog catalog = new DefaultCamelCatalog();
                    String version = catalog.getCatalogVersion();
                    String firstVersion = annotation.firstVersion();
                    if (!version.isBlank() && !firstVersion.isBlank()) {
                        PluginHelper.versionCheck(main, version, firstVersion, command);
                    }
                }

                plugin.customize(commandLine, main);
                return true;
            }
        } catch (Exception e) {
            // Ignore individual plugin loading errors
        }
        return false;
    }

    private static String extractCommandFromPlugin(Class<?> pluginClass, String pluginName) {
        // Try to extract command from plugin name
        if (pluginName.startsWith("camel-jbang-plugin-")) {
            return pluginName.substring("camel-jbang-plugin-".length());
        }

        // Fallback to class name analysis
        String className = pluginClass.getSimpleName();
        if (className.endsWith("Plugin")) {
            String command = className.substring(0, className.length() - 6).toLowerCase();
            return command;
        }

        return pluginName;
    }

    /**
     * Checks if embedded plugins are available in the classpath.
     */
    public static boolean hasEmbeddedPlugins() {
        try {
            ClassLoader classLoader = PluginHelper.class.getClassLoader();
            String serviceDir = PLUGIN_SERVICE_DIR;

            // Check if we're in a jar with plugin services
            Enumeration<URL> resources = classLoader.getResources(serviceDir);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                if (url.getProtocol().equals("jar")) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Ignore errors
        }
        return false;
    }
}
