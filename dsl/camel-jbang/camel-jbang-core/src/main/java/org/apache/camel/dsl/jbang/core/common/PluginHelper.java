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
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
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

    /**
     * Built-in top-level commands that consume plugins — either by accepting plugin-contributed sub-options (run,
     * export) or by dispatching to plugin-provided commands (shell, cmd). Plugin discovery must still run for these
     * even if the target name is registered as a built-in subcommand.
     */
    private static final Set<String> PLUGIN_CONSUMING_BUILTINS = Set.of("shell", "run", "export", "cmd");

    private static final FactoryFinder FACTORY_FINDER
            = new DefaultFactoryFinder(new DefaultClassResolver(), FactoryFinder.DEFAULT_PATH + "camel-jbang-plugin/");

    private PluginHelper() {
        // prevent instantiation of utility class
    }

    /**
     * Decides whether plugin discovery (classpath scan + JSON config + Maven resolution) is needed for the current
     * invocation. Returns false when the target command is a built-in that does not consume plugins, skipping all
     * plugin-related IO. Returns true for plugin-consuming built-ins (run/export/cmd/shell), for unknown commands
     * (likely plugin-provided), and when no target is given (e.g. --help listing).
     *
     * @param  commandLine the command line with all built-in subcommands already registered
     * @param  args        the raw CLI args; only args[0] is inspected
     * @return             true if plugin discovery should run, false to short-circuit
     */
    public static boolean shouldDiscoverPlugins(CommandLine commandLine, String... args) {
        if (args == null || args.length == 0) {
            return true;
        }
        // Only args[0] is inspected. If the user puts global options before the subcommand
        // (e.g. `camel --verbose version`), we conservatively load plugins. Picocli option grammar
        // is non-trivial enough that a heuristic skip would risk false negatives; the missed
        // optimization is acceptable since this prefix-options pattern is uncommon.
        String target = args[0];
        if (target == null || target.isBlank() || target.startsWith("-")) {
            return true;
        }
        if (PLUGIN_CONSUMING_BUILTINS.contains(target)) {
            return true;
        }
        // target is a built-in (and not a plugin-consuming one) → no plugin needed
        return !commandLine.getSubcommands().containsKey(target);
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

        // allow to download plugins from 3rd party maven repositories by --repos argument
        String repos = null;
        try {
            for (String a : args) {
                if (a.startsWith("--repos=")) {
                    repos = a.substring(8).trim();
                } else if (a.startsWith("--repo=")) {
                    repos = a.substring(7).trim();
                }
            }
        } catch (Exception e) {
            // ignore
        }

        if (repos == null) {
            // fallback to load user configuration
            Properties configProperties = new Properties();
            CommandLineHelper.loadProperties(configProperties::putAll);
            repos = configProperties.getProperty("repos");
            if (repos == null) {
                repos = configProperties.getProperty("repo");
            }
        }

        // If we found embedded plugins, and we're looking for a specific target,
        // check if it was satisfied by embedded plugins
        if (foundEmbeddedPlugins && target != null && !"shell".equals(target)) {
            // Check if the target command was added by embedded plugins
            if (commandLine.getSubcommands().containsKey(target)) {
                return; // Target satisfied by embedded plugins, no need for JSON config
            }
        }

        // Fall back to JSON configuration for additional or missing plugins.
        // Pass target so only matching plugins are downloaded — avoids expensive
        // Maven resolution for plugins unrelated to the current command.
        Map<String, Plugin> plugins = getActivePlugins(main, repos, target);
        for (Map.Entry<String, Plugin> plugin : plugins.entrySet()) {
            // Skip if this plugin was already loaded from embedded plugins
            if (foundEmbeddedPlugins && commandLine.getSubcommands().containsKey(plugin.getKey())) {
                continue;
            }

            plugin.getValue().customize(commandLine, main);
        }
    }

    /**
     * Gets the active plugins according to the local plugin configuration file. Performs version check to make sure
     * that the current Camel JBang version is able to execute the plugin.
     *
     * @param  main  to exit the CLI process in case of error
     * @param  repos custom maven repositories
     * @return       map of plugins where key represents the plugin command and value the plugin instance.
     */
    public static Map<String, Plugin> getActivePlugins(CamelJBangMain main, String repos) {
        return getActivePlugins(main, repos, null);
    }

    /**
     * Gets the active plugins according to the local plugin configuration file, optionally filtered by target command.
     * When a target command is specified (and is not "shell"), only the plugin matching that command is downloaded and
     * instantiated — other plugins are skipped entirely, avoiding expensive Maven resolution.
     *
     * @param  main   to exit the CLI process in case of error
     * @param  repos  custom maven repositories
     * @param  target the target command name to filter by, or null to load all plugins
     * @return        map of plugins where key represents the plugin command and value the plugin instance.
     */
    public static Map<String, Plugin> getActivePlugins(CamelJBangMain main, String repos, String target) {
        Map<String, Plugin> activePlugins = new HashMap<>();
        JsonObject config = getPluginConfig();
        if (config != null) {
            CamelCatalog catalog = new DefaultCamelCatalog();
            String version = catalog.getCatalogVersion();
            JsonObject plugins = config.getMap("plugins");

            boolean configDirty = false;
            for (String pluginKey : plugins.keySet()) {
                JsonObject properties = plugins.getMap(pluginKey);

                final String name = properties.getOrDefault("name", pluginKey).toString();
                final String command = properties.getOrDefault("command", name).toString();
                final String firstVersion = properties.getOrDefault("firstVersion", "").toString();
                final String gav = properties.getOrDefault("dependency", "").toString();

                // skip plugins that don't match the target command to avoid unnecessary downloads
                if (target != null && !"shell".equals(target) && !target.equals(command)) {
                    continue;
                }

                // check if plugin version can be loaded (cannot if we use an older camel version than the plugin)
                if (!version.isBlank() && !firstVersion.isBlank()) {
                    versionCheck(main, version, firstVersion, command);
                }

                ResolveResult res = resolvePlugin(properties, command, version, gav, repos, main.getOut());
                if (res.plugin().isPresent()) {
                    activePlugins.put(command, res.plugin().get());
                    if (res.cacheWritten()) {
                        configDirty = true;
                    }
                } else {
                    main.getOut().println("camel-jbang-plugin-" + command + " not found. Exit");
                    main.quit(1);
                }
            }
            if (configDirty) {
                savePluginConfig(config);
            }
        }

        return activePlugins;
    }

    public static Optional<Plugin> getPlugin(String name, String defaultVersion, String gav, String repos, Printer printer) {
        return resolvePlugin(null, name, defaultVersion, gav, repos, printer).plugin();
    }

    /**
     * Resolves a plugin by trying, in order: the cached metadata in the plugin entry (fast path with no IO beyond
     * size+mtime checks), the factory finder (embedded plugin on the JVM classpath), and finally the Maven downloader.
     * When the downloader runs, the resolved classpath is captured into the plugin entry's {@code resolved} block so
     * subsequent invocations take the fast path.
     */
    private static ResolveResult resolvePlugin(
            JsonObject entry, String name, String defaultVersion, String gav, String repos, Printer printer) {
        Optional<Plugin> cached = loadFromCache(entry, defaultVersion, gav, repos);
        if (cached.isPresent()) {
            return new ResolveResult(cached, false);
        }

        Optional<Plugin> plugin = FACTORY_FINDER.newInstance("camel-jbang-plugin-" + name, Plugin.class);
        if (plugin.isPresent()) {
            return new ResolveResult(plugin, false);
        }

        final MavenGav mavenGav = dependencyAsMavenGav(gav);
        final String group = extractGroup(mavenGav, "org.apache.camel");
        final String depVersion = extractVersion(mavenGav, defaultVersion);

        DownloadResult dr = downloadPlugin(name, defaultVersion, depVersion, group, repos, printer);
        boolean cacheWritten = false;
        if (dr.plugin().isPresent() && entry != null && dr.classLoader() != null && dr.className() != null) {
            cacheWritten = writeCache(entry, defaultVersion, gav, repos, dr.className(), dr.classLoader(), name, depVersion);
        }
        return new ResolveResult(dr.plugin(), cacheWritten);
    }

    private static Optional<Plugin> loadFromCache(JsonObject entry, String camelVersion, String gav, String repos) {
        if (entry == null) {
            return Optional.empty();
        }
        JsonObject resolved = entry.getMap("resolved");
        if (resolved == null) {
            return Optional.empty();
        }
        if (!sameCamelVersion(asString(resolved.get("camelVersion")), camelVersion)) {
            return Optional.empty();
        }
        if (!Objects.equals(normalize(asString(resolved.get("gav"))), normalize(gav))) {
            return Optional.empty();
        }
        if (!Objects.equals(normalize(asString(resolved.get("repos"))), normalize(repos))) {
            return Optional.empty();
        }
        String className = asString(resolved.get("className"));
        if (className == null || className.isBlank()) {
            return Optional.empty();
        }
        Object cpObj = resolved.get("classpath");
        if (!(cpObj instanceof Collection)) {
            return Optional.empty();
        }
        Collection<?> classpath = (Collection<?>) cpObj;
        if (classpath.isEmpty()) {
            return Optional.empty();
        }

        List<URL> urls = new ArrayList<>(classpath.size());
        for (Object o : classpath) {
            if (!(o instanceof Map)) {
                return Optional.empty();
            }
            Map<?, ?> jar = (Map<?, ?>) o;
            Path p = validateFileEntry(jar);
            if (p == null) {
                return Optional.empty();
            }
            try {
                urls.add(p.toUri().toURL());
            } catch (IOException e) {
                return Optional.empty();
            }
        }

        // If the cache tracks the plugin POM, validate it too. Detects POM-only changes (e.g. a SNAPSHOT
        // plugin's transitive deps changed without a jar rebuild).
        Object pomObj = resolved.get("pom");
        if (pomObj instanceof Map<?, ?> pom) {
            if (validateFileEntry(pom) == null) {
                return Optional.empty();
            }
        }

        try {
            URLClassLoader cl = new URLClassLoader(urls.toArray(new URL[0]), PluginHelper.class.getClassLoader());
            Class<?> pluginClass = cl.loadClass(className);
            Plugin instance = (Plugin) ObjectHelper.newInstance(pluginClass);
            instance.setClassLoader(cl);
            return Optional.of(instance);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Persists the resolved plugin classpath into the entry's {@code resolved} block. Package-private so unit tests can
     * drive the happy path without invoking the Maven downloader. Also tracks the plugin's own POM file (size+mtime) so
     * a POM-only change (e.g. a SNAPSHOT plugin gaining a new transitive dependency without a jar rebuild) invalidates
     * the cache on the next invocation.
     */
    static boolean writeCache(
            JsonObject entry, String camelVersion, String gav, String repos, String className, ClassLoader cl,
            String pluginCommand, String pluginVersion) {
        URL[] urls;
        if (cl instanceof URLClassLoader ucl) {
            urls = ucl.getURLs();
        } else {
            return false;
        }
        if (urls == null || urls.length == 0) {
            return false;
        }
        Collection<JsonObject> classpath = new ArrayList<>(urls.length);
        JsonObject pomEntry = null;
        String pluginJarName = "camel-jbang-plugin-" + pluginCommand + "-" + pluginVersion + ".jar";
        for (URL u : urls) {
            try {
                Path p = Path.of(u.toURI());
                if (!Files.exists(p)) {
                    return false;
                }
                JsonObject jar = new JsonObject();
                jar.put("path", p.toAbsolutePath().toString());
                jar.put("size", Files.size(p));
                jar.put("mtime", Files.getLastModifiedTime(p).toMillis());
                classpath.add(jar);

                // Identify the plugin's own jar by filename and track the sibling POM, so a Maven re-install
                // of the plugin (which always rewrites the POM) is detected even when the jar bytes happen
                // to be unchanged.
                if (pomEntry == null && pluginJarName.equals(p.getFileName().toString())) {
                    Path pom = p.resolveSibling("camel-jbang-plugin-" + pluginCommand + "-" + pluginVersion + ".pom");
                    if (Files.exists(pom)) {
                        pomEntry = new JsonObject();
                        pomEntry.put("path", pom.toAbsolutePath().toString());
                        pomEntry.put("size", Files.size(pom));
                        pomEntry.put("mtime", Files.getLastModifiedTime(pom).toMillis());
                    }
                }
            } catch (Exception e) {
                return false;
            }
        }
        JsonObject resolved = new JsonObject();
        resolved.put("camelVersion", camelVersion);
        if (normalize(gav) != null) {
            resolved.put("gav", normalize(gav));
        }
        if (normalize(repos) != null) {
            resolved.put("repos", normalize(repos));
        }
        resolved.put("className", className);
        resolved.put("cachedAt", System.currentTimeMillis());
        resolved.put("classpath", classpath);
        if (pomEntry != null) {
            resolved.put("pom", pomEntry);
        }
        entry.put("resolved", resolved);
        return true;
    }

    /**
     * Validates a {path, size, mtime} entry from the cache against the actual file on disk. Returns the resolved Path
     * on match, or null if the file is missing, was modified, or the entry is malformed.
     */
    private static Path validateFileEntry(Map<?, ?> entry) {
        String path = asString(entry.get("path"));
        Object sizeObj = entry.get("size");
        Object mtimeObj = entry.get("mtime");
        if (path == null || !(sizeObj instanceof Number) || !(mtimeObj instanceof Number)) {
            return null;
        }
        long size = ((Number) sizeObj).longValue();
        long mtime = ((Number) mtimeObj).longValue();
        Path p = Path.of(path);
        try {
            if (!Files.exists(p) || Files.size(p) != size || Files.getLastModifiedTime(p).toMillis() != mtime) {
                return null;
            }
            return p;
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean sameCamelVersion(String a, String b) {
        return stripSnapshot(a).equals(stripSnapshot(b));
    }

    private static String stripSnapshot(String v) {
        if (v == null) {
            return "";
        }
        return v.endsWith("-SNAPSHOT") ? v.substring(0, v.length() - "-SNAPSHOT".length()) : v;
    }

    private static String normalize(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private record ResolveResult(Optional<Plugin> plugin, boolean cacheWritten) {
    }

    private record DownloadResult(Optional<Plugin> plugin, ClassLoader classLoader, String className) {
    }

    private static MavenGav dependencyAsMavenGav(String gav) {
        if (gav == null || gav.isEmpty()) {
            return null;
        }

        return MavenGav.parseGav(gav);
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

    private static DownloadResult downloadPlugin(
            String command, String camelVersion, String version, String group, String repos, Printer printer) {
        DependencyDownloader downloader = new MavenDependencyDownloader();
        DependencyDownloaderClassLoader ddlcl = new DependencyDownloaderClassLoader(PluginHelper.class.getClassLoader());
        downloader.setClassLoader(ddlcl);
        if (repos != null && !repos.isBlank()) {
            downloader.setRepositories(repos);
        }
        // prefer resolving from local Maven repository to avoid expensive remote SNAPSHOT metadata checks
        downloader.setPreferLocal(true);
        downloader.start();
        // downloads and adds to the classpath
        downloader.downloadDependencyWithParent("org.apache.camel:camel-jbang-parent:pom:" + camelVersion, group,
                "camel-jbang-plugin-" + command, version);
        Optional<Plugin> instance = Optional.empty();
        String pluginClassName = null;
        InputStream in = null;
        String path = FactoryFinder.DEFAULT_PATH + "camel-jbang-plugin/camel-jbang-plugin-" + command;
        try {
            // reads the class name from the plugin dependency
            in = ddlcl.getResourceAsStream(path);
            if (in != null) {
                Properties prop = new Properties();
                prop.load(in);
                pluginClassName = prop.getProperty("class");
                DefaultClassResolver resolver = new DefaultClassResolver();
                Class<?> pluginClass = resolver.resolveClass(pluginClassName, ddlcl);
                instance = Optional.of(Plugin.class.cast(ObjectHelper.newInstance(pluginClass)));
                instance.ifPresent(plugin -> plugin.setClassLoader(ddlcl));
            } else {
                String gav = String.join(":", group, "camel-jbang-plugin-" + command, version);
                printer.printf(String.format("ERROR: Failed to read file %s in dependency %s%n", path, gav));
            }
        } catch (IOException e) {
            throw new RuntimeCamelException(String.format("Failed to read the file %s.", path), e);
        } finally {
            downloader.stop();
            try {
                downloader.close();
            } catch (Exception e) {
                printer.printErr(e);
            }
            IOHelper.close(in);
        }
        return new DownloadResult(instance, ddlcl, pluginClassName);
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

            // Try scanning jar
            Enumeration<URL> resources = classLoader.getResources(PLUGIN_SERVICE_DIR);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                if (url.getProtocol().equals("jar")) {
                    foundAny = scanJarForPlugins(commandLine, main, target, classLoader, url);
                    break; // Found jar, no need to continue
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
