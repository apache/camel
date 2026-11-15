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

import org.aesh.command.Command;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.apache.camel.catalog.DefaultCamelCatalog;
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
        camelVersion = new DefaultCamelCatalog().getCatalogVersion();
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

    // ---- Legacy plugins without appliesTo: backward compat only for CliExportContext ----

    @Test
    void legacyPluginIsLoadedForRunTargetWithLegacyContributors() throws Exception {
        writeTwoPluginConfig("alpha", "beta");

        CamelJBangMain main = new CamelJBangMain();
        Map<String, Plugin> plugins = PluginHelper.getActivePlugins(main, null, "run", true);

        assertThat(plugins).containsKey("alpha")
                .containsKey("beta");
    }

    @Test
    void legacyPluginIsLoadedForExportTargetWithLegacyContributors() throws Exception {
        writeTwoPluginConfig("alpha", "beta");

        CamelJBangMain main = new CamelJBangMain();
        Map<String, Plugin> plugins = PluginHelper.getActivePlugins(main, null, "export", true);

        assertThat(plugins).containsKey("alpha")
                .containsKey("beta");
    }

    @Test
    void legacyPluginIsSkippedForRunTargetWithoutLegacyContributors() throws Exception {
        writeTwoPluginConfig("alpha", "beta");

        CamelJBangMain main = new CamelJBangMain();
        Map<String, Plugin> plugins = PluginHelper.getActivePlugins(main, null, "run");

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
    void pluginWithEmptyAppliesToIsNotLoadedForBuiltinTarget() throws Exception {
        Path jar = tempDir.resolve("fake-alpha.jar");
        FakePluginJar.write(jar, "alpha");

        JsonObject entry = buildEntry("alpha", camelVersion, jar, Files.getLastModifiedTime(jar).toMillis());
        entry.put("appliesTo", jsonArray());
        writeConfig(entry);

        CamelJBangMain main = new CamelJBangMain();
        Map<String, Plugin> plugins = PluginHelper.getActivePlugins(main, null, "export");

        assertThat(plugins).doesNotContainKey("alpha");
    }

    @Test
    void pluginWithAppliesToExportIsLoadedByCliExportContext() throws Exception {
        // A broken entry with appliesTo ["export"]: if CliExportContext includes it, resolution
        // fails — proving the plugin was included in the export target. A contributor that cannot
        // be loaded is left out with a warning (CAMEL-26931), which names it.
        JsonObject entry = buildBrokenEntry("exporter");
        entry.put("appliesTo", jsonArray("export"));
        writeConfig(entry);

        StringPrinter printer = new StringPrinter();
        CamelJBangMain main = new CamelJBangMain().withPrinter(printer);

        ExportContext context = CliExportContext.create(main, printer, true);

        assertThat(context.getPlugins(null).exporters()).isEmpty();
        assertThat(printer.getStderr())
                .contains("Plugin exporter cannot be loaded (camel-jbang-plugin-exporter not found), so camel export");
    }

    // ---- Known PluginType appliesTo is used at runtime without config entry ----

    @Test
    void knownPluginTypeAppliesToIsUsedAtRuntime() throws Exception {
        Path jar = tempDir.resolve("fake-test.jar");
        FakePluginJar.write(jar, "test");

        // config entry for "test" plugin without explicit appliesTo
        JsonObject entry = buildEntry("test", camelVersion, jar, Files.getLastModifiedTime(jar).toMillis());
        writeConfig(entry);

        CamelJBangMain main = new CamelJBangMain();

        // PluginType.TEST declares appliesTo = {"export"}, so it should be loaded for "export"
        Map<String, Plugin> plugins = PluginHelper.getActivePlugins(main, null, "export");
        assertThat(plugins).containsKey("test");

        // ... but not for "run"
        Map<String, Plugin> runPlugins = PluginHelper.getActivePlugins(main, null, "run");
        assertThat(runPlugins).doesNotContainKey("test");
    }

    // ---- resolved.appliesTo is only trusted when the Camel version matches ----

    @Test
    void resolvedAppliesToIsUsedWhenCamelVersionMatches() throws Exception {
        Path jar = tempDir.resolve("fake-exporter.jar");
        FakePluginJar.write(jar, "exporter");

        // entry with resolved block matching the current Camel version, appliesTo: ["export"]
        JsonObject entry = buildEntry("exporter", camelVersion, jar, Files.getLastModifiedTime(jar).toMillis());
        JsonObject resolved = (JsonObject) entry.getMap("resolved");
        resolved.put("appliesTo", jsonArray("export"));
        writeConfig(entry);

        CamelJBangMain main = new CamelJBangMain();

        // resolved.appliesTo says "export" and the version matches — plugin is skipped for "run"
        Map<String, Plugin> plugins = PluginHelper.getActivePlugins(main, null, "run", true);
        assertThat(plugins).doesNotContainKey("exporter");
    }

    @Test
    void staleResolvedAppliesToIsIgnored() throws Exception {
        // entry with resolved block from a DIFFERENT Camel version, appliesTo: ["export"]
        // No valid jar — resolution will fail, proving the plugin was NOT filtered out.
        JsonObject entry = buildBrokenEntry("exporter");
        JsonObject resolved = new JsonObject();
        resolved.put("camelVersion", "0.0.0-stale");
        resolved.put("appliesTo", jsonArray("export"));
        entry.put("resolved", resolved);
        writeConfig(entry);

        StringPrinter printer = new StringPrinter();
        CamelJBangMain main = new CamelJBangMain().withPrinter(printer);

        // The resolved.appliesTo says "export", but the camelVersion is stale so it is ignored.
        // With legacyContributors=true, the plugin passes the filter for "run" (treated as legacy).
        // Resolution fails — the warning proves the stale appliesTo did not filter it out.
        assertThat(PluginHelper.getActivePlugins(main, null, "run", true)).isEmpty();
        assertThat(printer.getStderr())
                .contains("Plugin exporter cannot be loaded (camel-jbang-plugin-exporter not found), so camel run");
    }

    // ---- CliExportContext should not resolve unrelated plugins (with explicit appliesTo) ----

    @Test
    void cliExportContextDoesNotResolveUnrelatedPlugins() throws Exception {
        Path goodJar = tempDir.resolve("fake-good.jar");
        FakePluginJar.write(goodJar, "good");

        JsonObject goodEntry = buildEntry("good", camelVersion, goodJar, Files.getLastModifiedTime(goodJar).toMillis());
        goodEntry.put("appliesTo", jsonArray("export"));

        // broken plugin with empty appliesTo — explicitly opted out of other commands
        JsonObject brokenEntry = buildBrokenEntry("broken");
        brokenEntry.put("appliesTo", jsonArray());

        writeConfig(goodEntry, brokenEntry);

        StringPrinter printer = new StringPrinter();
        CamelJBangMain main = new CamelJBangMain().withPrinter(printer);

        ExportContext context = CliExportContext.create(main, printer, true);

        // the broken plugin has appliesTo [] (explicitly empty) so it is filtered out
        assertThatCode(() -> context.getPlugins(null))
                .as("CliExportContext should not resolve plugins that opted out of export")
                .doesNotThrowAnyException();
    }

    // ---- addPlugins should not customize unrelated plugins ----

    @Test
    void addPluginsForRunDoesNotCustomizePluginWithNonMatchingAppliesTo() throws Exception {
        Path jarA = tempDir.resolve("fake-alpha.jar");
        FakePluginJar.write(jarA, "alpha");
        Path jarB = tempDir.resolve("fake-beta.jar");
        FakePluginJar.write(jarB, "beta");

        JsonObject entryA = buildEntry("alpha", camelVersion, jarA, Files.getLastModifiedTime(jarA).toMillis());
        entryA.put("appliesTo", jsonArray("export"));
        JsonObject entryB = buildEntry("beta", camelVersion, jarB, Files.getLastModifiedTime(jarB).toMillis());
        entryB.put("appliesTo", jsonArray("export"));
        writeConfig(entryA, entryB);

        CamelJBangMain main = new CamelJBangMain().withPrinter(new StringPrinter());
        CommandTree commandLine = new CommandTree(main);
        commandLine.addSubcommand("run", new CommandTree(new NoOpCommand()));

        PluginHelper.addPlugins(commandLine, main, "run", "foo.yaml");

        assertThat(commandLine.getSubcommands())
                .doesNotContainKey("alpha")
                .doesNotContainKey("beta");
    }

    @Test
    void addPluginsForExportDoesNotCustomizePluginWithNonMatchingAppliesTo() throws Exception {
        Path jarA = tempDir.resolve("fake-alpha.jar");
        FakePluginJar.write(jarA, "alpha");
        Path jarB = tempDir.resolve("fake-beta.jar");
        FakePluginJar.write(jarB, "beta");

        JsonObject entryA = buildEntry("alpha", camelVersion, jarA, Files.getLastModifiedTime(jarA).toMillis());
        entryA.put("appliesTo", jsonArray("run"));
        JsonObject entryB = buildEntry("beta", camelVersion, jarB, Files.getLastModifiedTime(jarB).toMillis());
        entryB.put("appliesTo", jsonArray("run"));
        writeConfig(entryA, entryB);

        CamelJBangMain main = new CamelJBangMain().withPrinter(new StringPrinter());
        CommandTree commandLine = new CommandTree(main);
        commandLine.addSubcommand("export", new CommandTree(new NoOpCommand()));

        PluginHelper.addPlugins(commandLine, main, "export");

        assertThat(commandLine.getSubcommands())
                .doesNotContainKey("alpha")
                .doesNotContainKey("beta");
    }

    // ---- A broken legacy plugin should not break addPlugins for "run" or "cmd" ----

    @Test
    void brokenLegacyPluginDoesNotBreakAddPluginsForRun() throws Exception {
        Path goodJar = tempDir.resolve("fake-good.jar");
        FakePluginJar.write(goodJar, "good");

        // broken plugin without appliesTo — legacy entry; addPlugins uses the default
        // non-legacy path, so this plugin is skipped for "run"
        writeConfig(
                buildEntry("good", camelVersion, goodJar, Files.getLastModifiedTime(goodJar).toMillis()),
                buildBrokenEntry("broken"));

        CamelJBangMain main = new CamelJBangMain().withPrinter(new StringPrinter());
        CommandTree commandLine = new CommandTree(main);
        commandLine.addSubcommand("run", new CommandTree(new NoOpCommand()));

        assertThatCode(() -> PluginHelper.addPlugins(commandLine, main, "run", "foo.yaml"))
                .doesNotThrowAnyException();
    }

    @Test
    void brokenLegacyPluginDoesNotBreakAddPluginsForCmd() throws Exception {
        Path goodJar = tempDir.resolve("fake-good.jar");
        FakePluginJar.write(goodJar, "good");

        writeConfig(
                buildEntry("good", camelVersion, goodJar, Files.getLastModifiedTime(goodJar).toMillis()),
                buildBrokenEntry("broken"));

        CamelJBangMain main = new CamelJBangMain().withPrinter(new StringPrinter());
        CommandTree commandLine = new CommandTree(main);
        commandLine.addSubcommand("cmd", new CommandTree(new NoOpCommand()));

        assertThatCode(() -> PluginHelper.addPlugins(commandLine, main, "cmd"))
                .doesNotThrowAnyException();
    }

    // ---- A built-in command like "version" or "ps" should not trigger plugin loading ----

    @Test
    void builtInCommandDoesNotTriggerPluginDiscovery() {
        CamelJBangMain main = new CamelJBangMain();
        CommandTree cl = new CommandTree(main);
        cl.addSubcommand("version", new CommandTree(new NoOpCommand()));
        cl.addSubcommand("ps", new CommandTree(new NoOpCommand()));
        cl.addSubcommand("get", new CommandTree(new NoOpCommand()));
        cl.addSubcommand("config", new CommandTree(new NoOpCommand()));
        cl.addSubcommand("catalog", new CommandTree(new NoOpCommand()));

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

    private static class NoOpCommand implements Command<CommandInvocation> {
        @Override
        public CommandResult execute(CommandInvocation invocation) {
            return CommandResult.SUCCESS;
        }
    }

    // --- CAMEL-26913: --version and -V must not trigger plugin discovery ---

    @Test
    void versionFlagDoesNotDiscoverPlugins() {
        CamelJBangMain main = new CamelJBangMain().withPrinter(new StringPrinter());
        CommandTree commandLine = new CommandTree(main);
        commandLine.addSubcommand("version", new CommandTree(new NoOpCommand()));
        commandLine.addSubcommand("get", new CommandTree(new NoOpCommand()));
        assertThat(PluginHelper.shouldDiscoverPlugins(commandLine, "--version")).isFalse();
        assertThat(PluginHelper.shouldDiscoverPlugins(commandLine, "-V")).isFalse();
    }

    @Test
    void helpFlagStillDiscoverPlugins() {
        CamelJBangMain main = new CamelJBangMain().withPrinter(new StringPrinter());
        CommandTree commandLine = new CommandTree(main);
        commandLine.addSubcommand("version", new CommandTree(new NoOpCommand()));
        // --help needs all plugins for the full listing
        assertThat(PluginHelper.shouldDiscoverPlugins(commandLine, "--help")).isTrue();
        assertThat(PluginHelper.shouldDiscoverPlugins(commandLine, "-h")).isTrue();
    }

    @Test
    void noArgDiscoverPlugins() {
        CamelJBangMain main = new CamelJBangMain().withPrinter(new StringPrinter());
        CommandTree commandLine = new CommandTree(main);
        assertThat(PluginHelper.shouldDiscoverPlugins(commandLine)).isTrue();
    }
}
