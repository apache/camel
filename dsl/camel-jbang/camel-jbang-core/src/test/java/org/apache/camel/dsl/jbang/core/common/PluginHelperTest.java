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

import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.run.common.StringPrinter;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PluginHelperTest {

    @TempDir
    Path tempDir;

    private Path previousHome;

    @BeforeEach
    public void setup() {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(tempDir.toString());
    }

    @AfterEach
    public void restoreHome() {
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    public void testEmbeddedPluginDetectionDoesNotThrowException() {
        assertDoesNotThrow(PluginHelper::hasEmbeddedPlugins);
    }

    @Test
    public void aPluginCommandIsOfACamelPluginOrOfTheConfiguration() throws Exception {
        Path userConfig = CommandLineHelper.getHomeDir().resolve(PluginHelper.PLUGIN_CONFIG);
        Files.writeString(userConfig, """
                {
                  "plugins": {
                    "forage": {
                      "name": "forage",
                      "command": "forage",
                      "description": "Forage plugin",
                      "firstVersion": "4.18.0"
                    }
                  }
                }
                """, StandardOpenOption.CREATE);

        // installed on first use
        assertTrue(PluginHelper.isPluginCommand("tui"));
        assertTrue(PluginHelper.isPluginCommand("kubernetes"));
        // in the plugin configuration
        assertTrue(PluginHelper.isPluginCommand("forage"));
        assertFalse(PluginHelper.isPluginCommand("version"));
        assertFalse(PluginHelper.isPluginCommand("confg"));
    }

    @Test
    public void testFallbackToJsonConfig() throws Exception {
        // Create a user plugin config file in home directory
        Path userConfig = CommandLineHelper.getHomeDir().resolve(PluginHelper.PLUGIN_CONFIG);
        String userConfigContent = """
                {
                  "plugins": {
                    "user-plugin": {
                      "name": "user-plugin",
                      "command": "user",
                      "description": "User plugin",
                      "firstVersion": "2.0.0"
                    }
                  }
                }
                """;
        Files.writeString(userConfig, userConfigContent, StandardOpenOption.CREATE);

        // Test that user config is loaded
        JsonObject config = PluginHelper.getPluginConfig();
        assertNotNull(config);

        JsonObject plugins = config.getMap("plugins");
        assertNotNull(plugins);

        // Should have user plugin
        JsonObject userPlugin = plugins.getMap("user-plugin");
        assertNotNull(userPlugin);
    }

    @Test
    public void testGetActivePluginsFiltersByTarget() throws Exception {
        Path userConfig = CommandLineHelper.getHomeDir().resolve(PluginHelper.PLUGIN_CONFIG);
        String configContent = """
                {
                  "plugins": {
                    "forage": {
                      "name": "forage",
                      "command": "forage",
                      "description": "Forage plugin",
                      "firstVersion": "4.18.0"
                    },
                    "test": {
                      "name": "test",
                      "command": "test",
                      "description": "Test plugin",
                      "firstVersion": "4.14.0"
                    }
                  }
                }
                """;
        Files.writeString(userConfig, configContent, StandardOpenOption.CREATE);

        CamelJBangMain main = new CamelJBangMain();

        // target "version" should not match any plugin — returns empty without downloading
        Map<String, Plugin> plugins = PluginHelper.getActivePlugins(main, null, "version");
        assertTrue(plugins.isEmpty());
    }

    @Test
    public void testGetActivePluginsNoFilterLoadsAll() throws Exception {
        Path userConfig = CommandLineHelper.getHomeDir().resolve(PluginHelper.PLUGIN_CONFIG);
        String configContent = """
                {
                  "plugins": {
                    "test": {
                      "name": "test",
                      "command": "test",
                      "description": "Test plugin",
                      "firstVersion": "4.14.0"
                    }
                  }
                }
                """;
        Files.writeString(userConfig, configContent, StandardOpenOption.CREATE);

        // null target should attempt to load all plugins (will fail to download in test,
        // but the important thing is the filter logic doesn't skip it)
        JsonObject config = PluginHelper.getPluginConfig();
        assertNotNull(config);
        JsonObject pluginsConfig = config.getMap("plugins");
        assertEquals(1, pluginsConfig.size());
    }

    @Test
    public void testShouldDiscoverPlugins() {
        CamelJBangMain main = new CamelJBangMain();
        CommandTree cl = new CommandTree(main);
        cl.addSubcommand("version", new CommandTree(new NoOpCommand()));
        cl.addSubcommand("get", new CommandTree(new NoOpCommand()));
        cl.addSubcommand("run", new CommandTree(new NoOpCommand()));
        cl.addSubcommand("export", new CommandTree(new NoOpCommand()));
        cl.addSubcommand("shell", new CommandTree(new NoOpCommand()));
        cl.addSubcommand("cmd", new CommandTree(new NoOpCommand()));

        // built-in non-plugin-consuming commands → short-circuit
        assertFalse(PluginHelper.shouldDiscoverPlugins(cl, "version"));
        assertFalse(PluginHelper.shouldDiscoverPlugins(cl, "get", "bean"));

        // plugin-consuming built-ins → must load
        assertTrue(PluginHelper.shouldDiscoverPlugins(cl, "run", "foo.yaml"));
        assertTrue(PluginHelper.shouldDiscoverPlugins(cl, "export"));
        assertTrue(PluginHelper.shouldDiscoverPlugins(cl, "shell"));
        assertTrue(PluginHelper.shouldDiscoverPlugins(cl, "cmd", "browse"));

        // unknown command (likely plugin-provided) → must load
        assertTrue(PluginHelper.shouldDiscoverPlugins(cl, "kubernetes", "run"));

        // no args / help → must load so plugin commands appear in help listing
        assertTrue(PluginHelper.shouldDiscoverPlugins(cl));
        assertTrue(PluginHelper.shouldDiscoverPlugins(cl, "--help"));
        assertTrue(PluginHelper.shouldDiscoverPlugins(cl, ""));
    }

    @Test
    public void testCacheHitSkipsDownload() throws Exception {
        Path jar = tempDir.resolve("fake-plugin.jar");
        FakePluginJar.write(jar, "fake");

        String camelVersion = new org.apache.camel.catalog.DefaultCamelCatalog().getCatalogVersion();
        writeConfig(buildEntry("fake", camelVersion, jar, Files.getLastModifiedTime(jar).toMillis()));

        CamelJBangMain main = new CamelJBangMain();
        Map<String, Plugin> plugins = PluginHelper.getActivePlugins(main, null, "fake");
        assertEquals(1, plugins.size());
        assertNotNull(plugins.get("fake"));
        // ensure the cache path returned an instance of the class loaded from the cached jar
        assertEquals(FakePluginJar.PLUGIN_CLASS, plugins.get("fake").getClass().getName());
    }

    @Test
    public void testCacheInvalidatedOnMtimeChange() throws Exception {
        Path jar = tempDir.resolve("fake-plugin.jar");
        FakePluginJar.write(jar, "fake");

        String camelVersion = new org.apache.camel.catalog.DefaultCamelCatalog().getCatalogVersion();
        long staleMtime = Files.getLastModifiedTime(jar).toMillis() - 1000;
        writeConfig(buildEntry("fake", camelVersion, jar, staleMtime));

        // Stale mtime invalidates the cache. There is no factory-finder entry and no Maven dependency to
        // download (gav is empty), so the resolver returns empty and the plugin is not found.
        QuitCapture main = new QuitCapture();
        CliException e = assertThrows(CliException.class, () -> PluginHelper.getActivePlugins(main, null, "fake"));
        assertEquals("camel-jbang-plugin-fake not found", e.getMessage(),
                "expected resolver to give up when cache is invalid and no download path is viable");
        assertFalse(main.quitCalled, "the error ends the command, not the JVM");
    }

    @Test
    public void aPluginThatCannotBeLoadedDoesNotFailCamelRun() throws Exception {
        Path jar = tempDir.resolve("fake-plugin.jar");
        FakePluginJar.write(jar, "fake");
        String camelVersion = new org.apache.camel.catalog.DefaultCamelCatalog().getCatalogVersion();
        long staleMtime = Files.getLastModifiedTime(jar).toMillis() - 1000;
        // without appliesTo the plugin contributes to camel run and camel export, and it cannot be resolved
        writeConfig(buildEntry("fake", camelVersion, jar, staleMtime));
        StringPrinter printer = new StringPrinter();
        QuitCapture main = new QuitCapture();
        main.withPrinter(printer);

        // before, camel run failed for it (CAMEL-26931): it is left out with a warning
        Map<String, Plugin> plugins = assertDoesNotThrow(() -> PluginHelper.getActivePlugins(main, null, "run", true));

        assertTrue(plugins.isEmpty());
        assertTrue(printer.getStderr().contains("Plugin fake cannot be loaded (camel-jbang-plugin-fake not found), so camel"
                                                + " run goes on without it: remove it with camel plugin delete fake"),
                printer.getStderr());
        // its own command still fails
        assertThrows(CliException.class, () -> PluginHelper.getActivePlugins(main, null, "fake"));
    }

    @Test
    public void testCachedPluginThatDoesNotLinkIsResolvedAgain() throws Exception {
        Path jar = tempDir.resolve("unlinked-plugin.jar");
        FakePluginJar.write(jar, "fake", UnlinkedFakePlugin.class.getName());

        String camelVersion = new org.apache.camel.catalog.DefaultCamelCatalog().getCatalogVersion();
        JsonObject entry = buildEntry("fake", camelVersion, jar, Files.getLastModifiedTime(jar).toMillis());
        ((JsonObject) entry.get("resolved")).put("className", UnlinkedFakePlugin.class.getName());
        writeConfig(entry);

        // the cache is not used, as when it is stale: there is no other way to resolve the plugin here
        QuitCapture main = new QuitCapture();
        CliException e = assertThrows(CliException.class, () -> PluginHelper.getActivePlugins(main, null, "fake"));
        assertEquals("camel-jbang-plugin-fake not found", e.getMessage());
    }

    @Test
    public void testJarFileOfAJarInAFolderWithASpace() throws Exception {
        // the CLI installed in such a folder, as C:\Program Files on Windows: the URL has the space as %20
        Path folder = Files.createDirectories(tempDir.resolve("my camel"));
        Path jar = folder.resolve("camel-launcher.jar");
        FakePluginJar.write(jar, "fake");
        URL url = URI.create("jar:" + jar.toUri() + "!/" + PluginHelper.PLUGIN_SERVICE_DIR).toURL();
        assertTrue(url.toString().contains("my%20camel"), url.toString());

        Path file = PluginHelper.jarFileOf(url);
        assertEquals(jar.toRealPath(), file.toRealPath());
        assertTrue(Files.isRegularFile(file));
    }

    @Test
    public void testWriteCachePersistsResolvedBlock() throws Exception {
        Path jar = tempDir.resolve("camel-jbang-plugin-fake-9.9.9.jar");
        FakePluginJar.write(jar, "fake");
        Path pom = tempDir.resolve("camel-jbang-plugin-fake-9.9.9.pom");
        Files.writeString(pom, "<project/>");

        JsonObject entry = new JsonObject();
        entry.put("name", "fake");
        entry.put("command", "fake");

        try (java.net.URLClassLoader cl = new java.net.URLClassLoader(new java.net.URL[] { jar.toUri().toURL() })) {
            boolean written = PluginHelper.writeCache(entry, "9.9.9", null, null, FakePluginJar.PLUGIN_CLASS, cl, "fake",
                    "9.9.9");
            assertTrue(written);
        }

        JsonObject resolved = entry.getMap("resolved");
        assertNotNull(resolved);
        assertEquals("9.9.9", resolved.getString("camelVersion"));
        assertEquals(FakePluginJar.PLUGIN_CLASS, resolved.getString("className"));
        assertNotNull(resolved.get("cachedAt"));

        Object cp = resolved.get("classpath");
        assertTrue(cp instanceof java.util.Collection);
        java.util.Collection<?> classpath = (java.util.Collection<?>) cp;
        assertEquals(1, classpath.size());
        Map<?, ?> jarEntry = (Map<?, ?>) classpath.iterator().next();
        assertEquals(jar.toAbsolutePath().toString(), jarEntry.get("path"));
        assertEquals(Files.size(jar), ((Number) jarEntry.get("size")).longValue());
        assertEquals(Files.getLastModifiedTime(jar).toMillis(), ((Number) jarEntry.get("mtime")).longValue());

        // POM sibling should be tracked since it lives next to the plugin jar
        Map<?, ?> pomEntry = (Map<?, ?>) resolved.get("pom");
        assertNotNull(pomEntry);
        assertEquals(pom.toAbsolutePath().toString(), pomEntry.get("path"));
        assertEquals(Files.size(pom), ((Number) pomEntry.get("size")).longValue());
    }

    @Test
    public void testCacheFastPathAvoidsResolver() throws Exception {
        // Before/after demonstration of CAMEL-23335 cache fast path. Same plugin name and on-disk jar in
        // both halves — only the presence of the `resolved` block in the config differs.
        Path jar = tempDir.resolve("fake-plugin.jar");
        FakePluginJar.write(jar, "fake");
        String camelVersion = new org.apache.camel.catalog.DefaultCamelCatalog().getCatalogVersion();

        // BEFORE CAMEL-23335: entry has no `resolved` block. resolvePlugin falls through loadFromCache,
        // misses FACTORY_FINDER (no service registered for "fake"), reaches downloadPlugin with no usable
        // gav, gets nothing back, and the plugin is not found.
        writeConfig(buildEntryWithoutResolvedBlock("fake", camelVersion));
        QuitCapture before = new QuitCapture();
        CliException e = assertThrows(CliException.class, () -> PluginHelper.getActivePlugins(before, null, "fake"));
        assertEquals("camel-jbang-plugin-fake not found", e.getMessage(),
                "without the resolved-block cache, resolver attempted download and gave up");

        // AFTER CAMEL-23335: entry has a valid `resolved` block. loadFromCache builds a URLClassLoader
        // from the cached jar and returns the plugin directly — FACTORY_FINDER and Maven are never touched.
        writeConfig(buildEntry("fake", camelVersion, jar, Files.getLastModifiedTime(jar).toMillis()));
        QuitCapture after = new QuitCapture();
        Map<String, Plugin> plugins
                = assertDoesNotThrow(() -> PluginHelper.getActivePlugins(after, null, "fake"));
        assertFalse(after.quitCalled, "cache fast path resolved the plugin without invoking the resolver");
        assertEquals(FakePluginJar.PLUGIN_CLASS, plugins.get("fake").getClass().getName(),
                "plugin loaded from the cached jar, not via FACTORY_FINDER");
    }

    @Test
    void testVersionCheckAcceptsSameSnapshotVersion() {
        // CAMEL-24469: a SNAPSHOT build must not be rejected against a plugin whose firstVersion
        // is the same SNAPSHOT release. This regressed on patch releases such as 4.22.1-SNAPSHOT.
        QuitCapture main = new QuitCapture();
        assertThatCode(() -> PluginHelper.versionCheck(main, "4.22.1-SNAPSHOT", "4.22.1-SNAPSHOT", "fake"))
                .doesNotThrowAnyException();
        assertFalse(main.quitCalled, "same SNAPSHOT version must be accepted, not quit");
    }

    @Test
    void testVersionCheckRejectsOlderVersion() {
        // A plugin requiring a newer firstVersion than the current build must be rejected: an error of the
        // command (exit code 1), not System.exit, which ends a JVM that embeds the CLI
        QuitCapture main = new QuitCapture();
        CliException e = assertThrows(CliException.class,
                () -> PluginHelper.versionCheck(main, "4.22.1-SNAPSHOT", "4.23.0", "fake"));
        assertEquals(CliExitCode.FAILURE, e.getExitCode(), "older build than plugin firstVersion must be rejected");
        assertFalse(main.quitCalled);
    }

    @Test
    void testCompatibilityWarningWhenRangeExcludesCli() throws Exception {
        // a plugin release selected for an older CLI; loading it uses the cache only, so there is no download
        Path jar = tempDir.resolve("fake-plugin.jar");
        FakePluginJar.write(jar, "compat");
        String camelVersion = new org.apache.camel.catalog.DefaultCamelCatalog().getCatalogVersion();
        JsonObject entry = buildEntry("compat", camelVersion, jar, Files.getLastModifiedTime(jar).toMillis());
        entry.put("camelVersions", "[4.0.0,4.1.0)");
        entry.put("camelVersionsSource", "inferred");
        writeConfig(entry);

        StringPrinter printer = new StringPrinter();
        CamelJBangMain main = new CamelJBangMain().withPrinter(printer);
        Map<String, Plugin> plugins = PluginHelper.getActivePlugins(main, null, "compat");
        assertEquals(1, plugins.size());
        assertEquals("WARN: compat is built for Camel [4.0.0,4.1.0) but this CLI is " + camelVersion
                     + ". Run 'camel plugin add compat' to select a matching release.",
                printer.getStderr());

        // once
        PluginHelper.getActivePlugins(main, null, "compat");
        assertEquals(1, printer.getStderr().lines().count());
    }

    @Test
    void testCompatibilityWarningNamesThePluginVersion() {
        StringPrinter printer = new StringPrinter();
        JsonObject entry = new JsonObject();
        entry.put("dependency", "org.example.plugins:camel-jbang-plugin-fixture:1.6.2");
        entry.put("camelVersions", "[4.22.0,4.23.0)");
        PluginHelper.compatibilityCheck(printer, "4.23.0", "fixture-named", entry);
        assertEquals("WARN: fixture-named 1.6.2 is built for Camel [4.22.0,4.23.0) but this CLI is 4.23.0."
                     + " Run 'camel plugin add fixture-named' to select a matching release.",
                printer.getStderr());
    }

    @Test
    void testNoCompatibilityWarningWhenRangeIncludesCli() {
        StringPrinter printer = new StringPrinter();
        JsonObject entry = new JsonObject();
        entry.put("dependency", "org.example.plugins:camel-jbang-plugin-fixture:1.6.2");
        entry.put("camelVersions", "[4.22.0,4.23.0)");
        PluginHelper.compatibilityCheck(printer, "4.22.3-SNAPSHOT", "fixture-ok", entry);
        assertEquals("", printer.getOutput());
    }

    @Test
    void testNoCompatibilityWarningForNearestOlderReleaseOnTheSameCamelVersion() {
        StringPrinter printer = new StringPrinter();
        JsonObject entry = new JsonObject();
        entry.put("dependency", "org.example.plugins:camel-jbang-plugin-fixture:1.4.2");
        entry.put("camelVersions", "[4.18.0,4.19.0)");
        entry.put("selectedForCamel", "4.21");
        PluginHelper.compatibilityCheck(printer, "4.21.3", "fixture-older", entry);
        assertEquals("", printer.getOutput());
        // the CLI moved to another Camel version, where a matching release may exist
        PluginHelper.compatibilityCheck(printer, "4.22.0", "fixture-older", entry);
        assertEquals("WARN: fixture-older 1.4.2 is built for Camel [4.18.0,4.19.0) but this CLI is 4.22.0."
                     + " Run 'camel plugin add fixture-older' to select a matching release.",
                printer.getStderr());
    }

    @Test
    void testNoCompatibilityWarningForLegacyEntry() {
        // registered before the Camel versions were stored
        StringPrinter printer = new StringPrinter();
        JsonObject entry = new JsonObject();
        entry.put("dependency", "org.example.plugins:camel-jbang-plugin-fixture:LATEST");
        PluginHelper.compatibilityCheck(printer, "4.23.0", "fixture-legacy", entry);
        assertEquals("", printer.getOutput());
    }

    @Test
    public void testDefaultPluginConfigPath() {
        Path expected = CommandLineHelper.getHomeDir().resolve(PluginHelper.PLUGIN_CONFIG);
        assertEquals(expected, PluginHelper.resolvePluginConfigPath(null, null));
    }

    @Test
    public void testCustomPluginConfigPath() {
        Path custom = tempDir.resolve("custom-plugins.json");
        assertEquals(custom, PluginHelper.getCustomPluginConfigPath(custom));
    }

    @Test
    public void testCustomPluginConfigPathAsDirectory() throws Exception {
        Path customDir = tempDir.resolve("custom-config-dir");
        Files.createDirectories(customDir);
        assertEquals(customDir.resolve(PluginHelper.PLUGIN_CONFIG), PluginHelper.getCustomPluginConfigPath(customDir));
    }

    @Test
    public void testCustomPluginConfigPathUsedByGetPluginConfig() throws Exception {
        Path customConfig = tempDir.resolve("custom-plugins.json");
        String configContent = """
                {
                  "plugins": {
                    "custom-plugin": {
                      "name": "custom-plugin",
                      "command": "custom",
                      "description": "Custom plugin"
                    }
                  }
                }
                """;
        Files.writeString(customConfig, configContent, StandardOpenOption.CREATE);

        JsonObject config = PluginHelper.getPluginConfig(customConfig);
        assertNotNull(config);
        JsonObject plugins = config.getMap("plugins");
        assertNotNull(plugins);
        assertNotNull(plugins.getMap("custom-plugin"));
    }

    @Test
    public void testCustomPluginConfigPathUsedByCreatePluginConfig() {
        Path customConfig = tempDir.resolve("sub").resolve("custom-plugins.json");

        JsonObject config = PluginHelper.createPluginConfig(customConfig);
        assertNotNull(config);
        assertTrue(Files.exists(customConfig));
    }

    @Test
    public void testCustomPluginConfigPathUsedBySavePluginConfig() throws Exception {
        Path customConfig = tempDir.resolve("save-plugins.json");

        PluginHelper.createPluginConfig(customConfig);

        JsonObject config = PluginHelper.getPluginConfig(customConfig);
        assertNotNull(config);
        JsonObject plugins = config.getMap("plugins");
        plugins.put("saved-plugin", new JsonObject());

        PluginHelper.savePluginConfig(config, customConfig);

        String saved = Files.readString(customConfig);
        assertTrue(saved.contains("saved-plugin"));
    }

    @Test
    public void testCustomPathIsolatesFromDefaultConfig() throws Exception {
        Path defaultConfig = CommandLineHelper.getHomeDir().resolve(PluginHelper.PLUGIN_CONFIG);
        String defaultContent = """
                {
                  "plugins": {
                    "default-plugin": {
                      "name": "default-plugin",
                      "command": "default",
                      "description": "Default plugin"
                    }
                  }
                }
                """;
        Files.writeString(defaultConfig, defaultContent, StandardOpenOption.CREATE);

        Path customConfig = tempDir.resolve("isolated-plugins.json");
        String customContent = """
                {
                  "plugins": {
                    "isolated-plugin": {
                      "name": "isolated-plugin",
                      "command": "isolated",
                      "description": "Isolated plugin"
                    }
                  }
                }
                """;
        Files.writeString(customConfig, customContent, StandardOpenOption.CREATE);

        JsonObject config = PluginHelper.getPluginConfig(customConfig);
        assertNotNull(config);
        JsonObject plugins = config.getMap("plugins");
        assertNotNull(plugins.getMap("isolated-plugin"));
        assertNull(plugins.getMap("default-plugin"));
    }

    @Test
    public void testEnvVarPluginConfigPath() {
        Path result = PluginHelper.resolvePluginConfigPath(null, "/tmp/camel-test-plugins.json");
        assertEquals(Path.of("/tmp/camel-test-plugins.json"), result);
    }

    @Test
    public void testEnvVarPluginConfigPathAsDirectory() throws Exception {
        Path customDir = tempDir.resolve("env-config-dir");
        Files.createDirectories(customDir);
        Path result = PluginHelper.resolvePluginConfigPath(null, customDir.toString());
        assertEquals(customDir.resolve(PluginHelper.PLUGIN_CONFIG), result);
    }

    @Test
    public void testExplicitPathTakesPrecedenceOverEnvVar() {
        Path explicit = tempDir.resolve("explicit-plugins.json");
        Path result = PluginHelper.resolvePluginConfigPath(explicit, "/tmp/env-plugins.json");
        assertEquals(explicit, result);
    }

    @Test
    public void testInvalidEnvVarPathThrowsIllegalState() {
        assertThrows(IllegalStateException.class,
                () -> PluginHelper.resolvePluginConfigPath(null, "\u0000invalid"));
    }

    @Test
    public void testExistingNonJsonFileUsedAsConfig() throws Exception {
        Path configFile = tempDir.resolve("plugins.conf");
        Files.writeString(configFile, "{}", StandardOpenOption.CREATE);
        Path result = PluginHelper.resolvePluginConfigPath(configFile, null);
        assertEquals(configFile, result);
    }

    @Test
    public void testUpperCaseJsonSuffixTreatedAsFile() {
        Path configFile = tempDir.resolve("PLUGINS.JSON");
        Path result = PluginHelper.resolvePluginConfigPath(configFile, null);
        assertEquals(configFile, result);
    }

    @Test
    public void testNonExistingNonJsonPathTreatedAsDirectory() {
        Path dirPath = tempDir.resolve("custom-config-dir");
        Path result = PluginHelper.resolvePluginConfigPath(dirPath, null);
        assertEquals(dirPath.resolve(PluginHelper.PLUGIN_CONFIG), result);
    }

    private void writeConfig(JsonObject pluginEntry) throws Exception {
        JsonObject plugins = new JsonObject();
        plugins.put(pluginEntry.getString("name"), pluginEntry);
        JsonObject config = new JsonObject();
        config.put("plugins", plugins);
        Path userConfig = CommandLineHelper.getHomeDir().resolve(PluginHelper.PLUGIN_CONFIG);
        Files.writeString(userConfig, config.toJson(), StandardOpenOption.CREATE);
    }

    private static JsonObject buildEntryWithoutResolvedBlock(String name, String camelVersion) {
        JsonObject entry = new JsonObject();
        entry.put("name", name);
        entry.put("command", name);
        entry.put("description", "Fake plugin");
        entry.put("firstVersion", camelVersion);
        return entry;
    }

    private static JsonObject buildEntry(String name, String camelVersion, Path jar, long jarMtime) throws Exception {
        JsonObject entry = new JsonObject();
        entry.put("name", name);
        entry.put("command", name);
        entry.put("description", "Fake plugin");
        entry.put("firstVersion", camelVersion);

        JsonObject jarEntry = new JsonObject();
        jarEntry.put("path", jar.toAbsolutePath().toString());
        jarEntry.put("size", Files.size(jar));
        jarEntry.put("mtime", jarMtime);

        JsonObject resolved = new JsonObject();
        resolved.put("camelVersion", camelVersion);
        resolved.put("className", FakePluginJar.PLUGIN_CLASS);
        java.util.List<JsonObject> cp = new java.util.ArrayList<>();
        cp.add(jarEntry);
        resolved.put("classpath", cp);
        entry.put("resolved", resolved);
        return entry;
    }

    private static class QuitCapture extends CamelJBangMain {
        boolean quitCalled;

        @Override
        public void quit(int exitCode) {
            quitCalled = true;
            throw new RuntimeException("quit");
        }
    }

    @CommandDefinition(name = "noop", description = "", generateHelp = true, sortOptions = true)
    private static class NoOpCommand implements Command<CommandInvocation> {
        @Override
        public CommandResult execute(CommandInvocation invocation) {
            return CommandResult.SUCCESS;
        }
    }

    // --- CAMEL-26913: atomic write for savePluginConfig ---

    @Test
    void savePluginConfigWritesAtomically() throws Exception {
        // create a valid plugin config
        Path configPath = tempDir.resolve(PluginHelper.PLUGIN_CONFIG);
        JsonObject config = PluginHelper.createPluginConfig(tempDir);

        // add a fake plugin entry
        JsonObject plugins = config.getMap("plugins");
        JsonObject fakePlugin = new JsonObject();
        fakePlugin.put("name", "fake");
        fakePlugin.put("command", "fake");
        plugins.put("fake", fakePlugin);

        // save via the atomic path
        PluginHelper.savePluginConfig(config, tempDir);

        // the file must exist and have the expected content
        assertTrue(Files.exists(configPath));
        String content = Files.readString(configPath);
        assertTrue(content.contains("\"fake\""));

        // no temp files left behind
        try (var stream = Files.list(tempDir)) {
            long tmpCount = stream.filter(p -> p.getFileName().toString().endsWith(".tmp")).count();
            assertEquals(0, tmpCount, "Stray .tmp files found in " + tempDir);
        }
    }

    @Test
    void savePluginConfigPreservesExistingFileOnWrite() throws Exception {
        // start with a valid config
        PluginHelper.createPluginConfig(tempDir);
        Path configPath = tempDir.resolve(PluginHelper.PLUGIN_CONFIG);

        // reading back produces the same object
        JsonObject read = PluginHelper.getPluginConfig(tempDir);
        assertNotNull(read);
        PluginHelper.savePluginConfig(read, tempDir);

        // content should be equivalent — round-trip must preserve the plugins map
        String afterSave = Files.readString(configPath);
        assertNotNull(afterSave);
        assertTrue(afterSave.contains("\"plugins\""), "saved config must still contain the plugins key");
        JsonObject reRead = PluginHelper.getPluginConfig(tempDir);
        assertNotNull(reRead);
        assertNotNull(reRead.getMap("plugins"), "plugins map must be preserved after save");
    }

    @Test
    void emptyJsonObjectDoesNotCauseNpe() throws Exception {
        // {} without a "plugins" key must not NPE — getActivePlugins should return empty
        Path userConfig = CommandLineHelper.getHomeDir().resolve(PluginHelper.PLUGIN_CONFIG);
        Files.writeString(userConfig, "{}", StandardOpenOption.CREATE);

        CamelJBangMain main = new CamelJBangMain();
        Map<String, Plugin> plugins = PluginHelper.getActivePlugins(main, null, "version");
        assertTrue(plugins.isEmpty());
    }

    @Test
    void isPluginCommandHandlesEmptyJsonObject() throws Exception {
        // {} without a "plugins" key must not NPE in isPluginCommand
        Path userConfig = CommandLineHelper.getHomeDir().resolve(PluginHelper.PLUGIN_CONFIG);
        Files.writeString(userConfig, "{}", StandardOpenOption.CREATE);

        assertFalse(PluginHelper.isPluginCommand("fake"));
    }

    @Test
    void truncatedJsonThrowsOnGetPluginConfig() throws Exception {
        Path userConfig = CommandLineHelper.getHomeDir().resolve(PluginHelper.PLUGIN_CONFIG);
        Files.writeString(userConfig, "{ \"plugins\": { \"foo\":", StandardOpenOption.CREATE);

        assertThrows(RuntimeException.class, () -> PluginHelper.getPluginConfig());
    }

    @Test
    void corruptPluginJsonDoesNotCrashGetActivePlugins() throws Exception {
        // A truncated/corrupt plugin JSON must not crash camel run, camel --help, etc.
        // getActivePlugins must warn and return empty map instead of throwing.
        Path userConfig = CommandLineHelper.getHomeDir().resolve(PluginHelper.PLUGIN_CONFIG);
        Files.writeString(userConfig, "{ \"plugins\": { \"foo\":", StandardOpenOption.CREATE);

        StringPrinter out = new StringPrinter();
        CamelJBangMain main = new CamelJBangMain().withPrinter(out);
        Map<String, Plugin> plugins = PluginHelper.getActivePlugins(main, null, "run");

        assertThat(plugins).isEmpty();
        assertThat(out.getStderr()).contains("corrupt");
    }

    @Test
    void getOrCreatePluginConfigRefusesToOverwriteCorruptFile() throws Exception {
        Path userConfig = CommandLineHelper.getHomeDir().resolve(PluginHelper.PLUGIN_CONFIG);
        Files.writeString(userConfig, "TRUNCATED GARBAGE", StandardOpenOption.CREATE);

        assertThrows(RuntimeException.class, () -> PluginHelper.getOrCreatePluginConfig());
        // the corrupt file must still exist (not overwritten)
        assertEquals("TRUNCATED GARBAGE", Files.readString(userConfig));
    }
}
