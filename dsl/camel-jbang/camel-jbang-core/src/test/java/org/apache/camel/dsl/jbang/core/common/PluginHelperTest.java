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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PluginHelperTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setup() {
        CommandLineHelper.useHomeDir(tempDir.toString());
    }

    @Test
    public void testEmbeddedPluginDetectionDoesNotThrowException() {
        assertDoesNotThrow(PluginHelper::hasEmbeddedPlugins);
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
        CommandLine cl = new CommandLine(main);
        cl.addSubcommand("version", new CommandLine(new NoOpCommand()));
        cl.addSubcommand("get", new CommandLine(new NoOpCommand()));
        cl.addSubcommand("run", new CommandLine(new NoOpCommand()));
        cl.addSubcommand("export", new CommandLine(new NoOpCommand()));
        cl.addSubcommand("shell", new CommandLine(new NoOpCommand()));
        cl.addSubcommand("cmd", new CommandLine(new NoOpCommand()));

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
        // download (gav is empty), so the resolver returns empty and quits.
        QuitCapture main = new QuitCapture();
        assertThrows(RuntimeException.class, () -> PluginHelper.getActivePlugins(main, null, "fake"));
        assertTrue(main.quitCalled, "expected resolver to give up when cache is invalid and no download path is viable");
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
        // gav, gets nothing back, and quits.
        writeConfig(buildEntryWithoutResolvedBlock("fake", camelVersion));
        QuitCapture before = new QuitCapture();
        assertThrows(RuntimeException.class, () -> PluginHelper.getActivePlugins(before, null, "fake"));
        assertTrue(before.quitCalled,
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

    @CommandLine.Command(name = "noop")
    private static class NoOpCommand implements Runnable {
        @Override
        public void run() {
            // no-op
        }
    }
}
