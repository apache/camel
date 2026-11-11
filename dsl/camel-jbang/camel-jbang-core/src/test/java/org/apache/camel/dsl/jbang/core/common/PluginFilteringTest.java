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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.CliExportContext;
import org.apache.camel.dsl.jbang.run.common.StringPrinter;
import org.apache.camel.dsl.jbang.run.export.ExportContext;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests that plugins not relevant to the current command are skipped entirely — not downloaded, not customized.
 * <p>
 * Each test configures cached fake plugins (so no Maven download is needed). To detect whether a plugin was loaded or
 * skipped, some tests use a "broken" plugin entry whose cache points to a non-existent jar: if the resolution code
 * attempts to load the broken plugin, it will fail with a {@link CliException}. If the plugin is properly filtered out,
 * the resolution succeeds.
 */
class PluginFilteringTest {

    @TempDir
    Path tempDir;

    private Path previousHome;
    private String camelVersion;

    @BeforeEach
    void setup() {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(tempDir.toString());
        camelVersion = new org.apache.camel.catalog.DefaultCamelCatalog().getCatalogVersion();
    }

    @AfterEach
    void restoreHome() {
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    // ---- Target-based filtering in getActivePlugins ----

    @Test
    void pluginNotMatchingTargetIsNotLoaded() throws Exception {
        writeTwoPluginConfig("alpha", "beta");

        CamelJBangMain main = new CamelJBangMain();
        Map<String, Plugin> plugins = PluginHelper.getActivePlugins(main, null, "alpha");

        assertThat(plugins).containsKey("alpha")
                .doesNotContainKey("beta");
    }

    @Test
    void nullTargetLoadsAllPlugins() throws Exception {
        writeTwoPluginConfig("alpha", "beta");

        CamelJBangMain main = new CamelJBangMain();
        Map<String, Plugin> plugins = PluginHelper.getActivePlugins(main, null, null);

        assertThat(plugins).containsKey("alpha")
                .containsKey("beta");
    }

    @Test
    void shellTargetLoadsAllPlugins() throws Exception {
        writeTwoPluginConfig("alpha", "beta");

        CamelJBangMain main = new CamelJBangMain();
        Map<String, Plugin> plugins = PluginHelper.getActivePlugins(main, null, "shell");

        assertThat(plugins).containsKey("alpha")
                .containsKey("beta");
    }

    // ---- The "run" command should not load unrelated plugins ----

    @Test
    void runCommandDoesNotLoadUnrelatedPlugin() throws Exception {
        writeTwoPluginConfig("alpha", "beta");

        CamelJBangMain main = new CamelJBangMain();
        Map<String, Plugin> plugins = PluginHelper.getActivePlugins(main, null, "run");

        assertThat(plugins).doesNotContainKey("alpha")
                .doesNotContainKey("beta");
    }

    // ---- The "export" command should not load unrelated plugins ----

    @Test
    void exportCommandDoesNotLoadUnrelatedPlugin() throws Exception {
        writeTwoPluginConfig("alpha", "beta");

        CamelJBangMain main = new CamelJBangMain();
        Map<String, Plugin> plugins = PluginHelper.getActivePlugins(main, null, "export");

        assertThat(plugins).doesNotContainKey("alpha")
                .doesNotContainKey("beta");
    }

    // ---- A broken unrelated plugin should not break the "run" target ----

    @Test
    void brokenUnrelatedPluginDoesNotBreakTargetedResolution() throws Exception {
        Path goodJar = tempDir.resolve("fake-good.jar");
        FakePluginJar.write(goodJar, "good");

        writeConfig(
                buildEntry("good", camelVersion, goodJar, Files.getLastModifiedTime(goodJar).toMillis()),
                buildBrokenEntry("broken"));

        CamelJBangMain main = new CamelJBangMain();

        // targeting "good" should skip the broken plugin entirely
        assertThatCode(() -> PluginHelper.getActivePlugins(main, null, "good"))
                .doesNotThrowAnyException();

        Map<String, Plugin> plugins = PluginHelper.getActivePlugins(main, null, "good");
        assertThat(plugins).containsKey("good")
                .doesNotContainKey("broken");
    }

    @Test
    void brokenUnrelatedPluginBreaksNullTarget() throws Exception {
        Path goodJar = tempDir.resolve("fake-good.jar");
        FakePluginJar.write(goodJar, "good");

        writeConfig(
                buildEntry("good", camelVersion, goodJar, Files.getLastModifiedTime(goodJar).toMillis()),
                buildBrokenEntry("broken"));

        CamelJBangMain main = new CamelJBangMain();

        // null target loads ALL plugins, so the broken one causes a failure
        assertThatThrownBy(() -> PluginHelper.getActivePlugins(main, null, null))
                .isInstanceOf(CliException.class)
                .hasMessageContaining("camel-jbang-plugin-broken not found");
    }

    // ---- appliesTo filtering ----

    @Test
    void pluginWithAppliesToIsLoadedForMatchingTarget() throws Exception {
        Path jar = tempDir.resolve("fake-exporter.jar");
        FakePluginJar.write(jar, "exporter");

        JsonObject entry = buildEntry("exporter", camelVersion, jar, Files.getLastModifiedTime(jar).toMillis());
        entry.put("appliesTo", jsonArray("export"));
        writeConfig(entry);

        CamelJBangMain main = new CamelJBangMain();
        Map<String, Plugin> plugins = PluginHelper.getActivePlugins(main, null, "export");

        assertThat(plugins).containsKey("exporter");
    }

    @Test
    void pluginWithAppliesToIsNotLoadedForNonMatchingTarget() throws Exception {
        Path jar = tempDir.resolve("fake-exporter.jar");
        FakePluginJar.write(jar, "exporter");

        JsonObject entry = buildEntry("exporter", camelVersion, jar, Files.getLastModifiedTime(jar).toMillis());
        entry.put("appliesTo", jsonArray("export"));
        writeConfig(entry);

        CamelJBangMain main = new CamelJBangMain();
        Map<String, Plugin> plugins = PluginHelper.getActivePlugins(main, null, "run");

        assertThat(plugins).doesNotContainKey("exporter");
    }

    @Test
    void pluginWithAppliesToExportIsLoadedByCliExportContext() throws Exception {
        Path jar = tempDir.resolve("fake-exporter.jar");
        FakePluginJar.write(jar, "exporter");

        JsonObject entry = buildEntry("exporter", camelVersion, jar, Files.getLastModifiedTime(jar).toMillis());
        entry.put("appliesTo", jsonArray("export"));
        writeConfig(entry);

        StringPrinter printer = new StringPrinter();
        CamelJBangMain main = new CamelJBangMain().withPrinter(printer);

        ExportContext context = CliExportContext.create(main, printer, true);

        assertThatCode(() -> context.getPlugins(null))
                .doesNotThrowAnyException();
    }

    // ---- auto-migration of old configs for known plugins ----

    @Test
    void oldConfigForKnownPluginIsAutoMigrated() throws Exception {
        Path jar = tempDir.resolve("fake-test.jar");
        FakePluginJar.write(jar, "test");

        // simulate old config: known plugin "test" without appliesTo
        JsonObject entry = buildEntry("test", camelVersion, jar, Files.getLastModifiedTime(jar).toMillis());
        writeConfig(entry);

        StringPrinter printer = new StringPrinter();
        CamelJBangMain main = new CamelJBangMain().withPrinter(printer);

        // target "export" — without migration, "test" plugin would be skipped
        Map<String, Plugin> plugins = PluginHelper.getActivePlugins(main, null, "export");
        assertThat(plugins).containsKey("test");

        // verify the config was updated on disk
        JsonObject savedConfig = PluginHelper.getPluginConfig();
        JsonObject savedPlugins = savedConfig.getMap("plugins");
        JsonObject savedPlugin = savedPlugins.getMap("test");
        assertThat(savedPlugin.get("appliesTo"))
                .isInstanceOf(List.class)
                .asList().contains("export");
    }

    @Test
    void oldConfigForKnownPluginEmitsWarning() throws Exception {
        Path jar = tempDir.resolve("fake-test.jar");
        FakePluginJar.write(jar, "test");

        JsonObject entry = buildEntry("test", camelVersion, jar, Files.getLastModifiedTime(jar).toMillis());
        writeConfig(entry);

        StringPrinter printer = new StringPrinter();
        CamelJBangMain main = new CamelJBangMain().withPrinter(printer);

        PluginHelper.getActivePlugins(main, null, "export");

        assertThat(printer.getOutput())
                .contains("appliesTo");
    }

    // ---- CliExportContext should not resolve unrelated plugins ----

    @Test
    void cliExportContextDoesNotResolveUnrelatedPlugins() throws Exception {
        Path goodJar = tempDir.resolve("fake-good.jar");
        FakePluginJar.write(goodJar, "good");

        writeConfig(
                buildEntry("good", camelVersion, goodJar, Files.getLastModifiedTime(goodJar).toMillis()),
                buildBrokenEntry("broken"));

        StringPrinter printer = new StringPrinter();
        CamelJBangMain main = new CamelJBangMain().withPrinter(printer);

        ExportContext context = CliExportContext.create(main, printer, true);

        // CliExportContext.create() calls getActivePlugins(main, repos) WITHOUT a target filter.
        // This means it will attempt to resolve the broken plugin, causing a CliException.
        // If the filtering were correct, the broken plugin would be skipped and this would succeed.
        assertThatCode(() -> context.getPlugins(null))
                .as("CliExportContext should not resolve plugins unrelated to run/export, "
                    + "but it currently calls getActivePlugins without a target filter")
                .doesNotThrowAnyException();
    }

    // ---- addPlugins should not customize unrelated plugins for "run" ----

    @Test
    void addPluginsForRunDoesNotCustomizeUnrelatedPlugin() throws Exception {
        writeTwoPluginConfig("alpha", "beta");

        CamelJBangMain main = new CamelJBangMain().withPrinter(new StringPrinter());
        CommandLine commandLine = new CommandLine(main);
        commandLine.addSubcommand("run", new CommandLine(new NoOpCommand()));

        PluginHelper.addPlugins(commandLine, main, "run", "foo.yaml");

        // "run" is a plugin-consuming builtin, but neither plugin's command is "run",
        // so they should be filtered out
        assertThat(commandLine.getSubcommands())
                .doesNotContainKey("alpha")
                .doesNotContainKey("beta");
    }

    @Test
    void addPluginsForExportDoesNotCustomizeUnrelatedPlugin() throws Exception {
        writeTwoPluginConfig("alpha", "beta");

        CamelJBangMain main = new CamelJBangMain().withPrinter(new StringPrinter());
        CommandLine commandLine = new CommandLine(main);
        commandLine.addSubcommand("export", new CommandLine(new NoOpCommand()));

        PluginHelper.addPlugins(commandLine, main, "export");

        assertThat(commandLine.getSubcommands())
                .doesNotContainKey("alpha")
                .doesNotContainKey("beta");
    }

    // ---- A broken unrelated plugin should not break addPlugins for "run" ----

    @Test
    void brokenUnrelatedPluginDoesNotBreakAddPluginsForRun() throws Exception {
        Path goodJar = tempDir.resolve("fake-good.jar");
        FakePluginJar.write(goodJar, "good");

        writeConfig(
                buildEntry("good", camelVersion, goodJar, Files.getLastModifiedTime(goodJar).toMillis()),
                buildBrokenEntry("broken"));

        CamelJBangMain main = new CamelJBangMain().withPrinter(new StringPrinter());
        CommandLine commandLine = new CommandLine(main);
        commandLine.addSubcommand("run", new CommandLine(new NoOpCommand()));

        // addPlugins for "run" should skip the broken plugin since it doesn't match
        assertThatCode(() -> PluginHelper.addPlugins(commandLine, main, "run", "foo.yaml"))
                .doesNotThrowAnyException();
    }

    // ---- A built-in command like "version" or "ps" should not trigger plugin loading ----

    @Test
    void builtInCommandDoesNotTriggerPluginDiscovery() {
        CamelJBangMain main = new CamelJBangMain();
        CommandLine cl = new CommandLine(main);
        cl.addSubcommand("version", new CommandLine(new NoOpCommand()));
        cl.addSubcommand("ps", new CommandLine(new NoOpCommand()));
        cl.addSubcommand("get", new CommandLine(new NoOpCommand()));
        cl.addSubcommand("config", new CommandLine(new NoOpCommand()));
        cl.addSubcommand("catalog", new CommandLine(new NoOpCommand()));

        assertThat(PluginHelper.shouldDiscoverPlugins(cl, "version")).isFalse();
        assertThat(PluginHelper.shouldDiscoverPlugins(cl, "ps")).isFalse();
        assertThat(PluginHelper.shouldDiscoverPlugins(cl, "get", "bean")).isFalse();
        assertThat(PluginHelper.shouldDiscoverPlugins(cl, "config", "list")).isFalse();
        assertThat(PluginHelper.shouldDiscoverPlugins(cl, "catalog", "component")).isFalse();
    }

    // ---- Helpers ----

    private void writeTwoPluginConfig(String pluginA, String pluginB) throws Exception {
        Path jarA = tempDir.resolve("fake-" + pluginA + ".jar");
        FakePluginJar.write(jarA, pluginA);
        Path jarB = tempDir.resolve("fake-" + pluginB + ".jar");
        FakePluginJar.write(jarB, pluginB);

        writeConfig(
                buildEntry(pluginA, camelVersion, jarA, Files.getLastModifiedTime(jarA).toMillis()),
                buildEntry(pluginB, camelVersion, jarB, Files.getLastModifiedTime(jarB).toMillis()));
    }

    private void writeConfig(JsonObject... entries) throws Exception {
        JsonObject plugins = new JsonObject();
        for (JsonObject entry : entries) {
            plugins.put(entry.getString("name"), entry);
        }
        JsonObject config = new JsonObject();
        config.put("plugins", plugins);
        Path userConfig = CommandLineHelper.getHomeDir().resolve(PluginHelper.PLUGIN_CONFIG);
        Files.writeString(userConfig, config.toJson(), StandardOpenOption.CREATE);
    }

    private JsonObject buildEntry(String name, String camelVersion, Path jar, long jarMtime) throws Exception {
        JsonObject entry = new JsonObject();
        entry.put("name", name);
        entry.put("command", name);
        entry.put("description", "Fake plugin " + name);
        entry.put("firstVersion", camelVersion);

        JsonObject jarEntry = new JsonObject();
        jarEntry.put("path", jar.toAbsolutePath().toString());
        jarEntry.put("size", Files.size(jar));
        jarEntry.put("mtime", jarMtime);

        JsonObject resolved = new JsonObject();
        resolved.put("camelVersion", camelVersion);
        resolved.put("className", FakePluginJar.PLUGIN_CLASS);
        List<JsonObject> cp = new ArrayList<>();
        cp.add(jarEntry);
        resolved.put("classpath", cp);
        entry.put("resolved", resolved);
        return entry;
    }

    /**
     * Builds a plugin entry with no valid resolution path: no cache, no factory finder, no Maven GAV. If the resolution
     * code attempts to load this plugin, it will fail with "not found". Used to verify that filtering properly skips
     * unrelated plugins.
     */
    private JsonObject buildBrokenEntry(String name) {
        JsonObject entry = new JsonObject();
        entry.put("name", name);
        entry.put("command", name);
        entry.put("description", "Broken plugin that should not be loaded");
        entry.put("firstVersion", camelVersion);
        return entry;
    }

    private static JsonArray jsonArray(String... values) {
        JsonArray array = new JsonArray();
        array.addAll(Arrays.asList(values));
        return array;
    }

    @CommandLine.Command(name = "noop")
    private static class NoOpCommand implements Runnable {
        @Override
        public void run() {
        }
    }
}
