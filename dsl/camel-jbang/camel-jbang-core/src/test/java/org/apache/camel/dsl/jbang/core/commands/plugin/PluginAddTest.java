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
package org.apache.camel.dsl.jbang.core.commands.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CachedFakePlugin;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.FakePluginDownloader;
import org.apache.camel.dsl.jbang.core.common.Plugin;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.core.common.PluginType;
import org.apache.camel.main.download.DependencyDownloader;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PluginAddTest extends CamelCommandBaseTestSupport {

    private FakePluginDownloader downloader;

    @BeforeEach
    public void setup() throws Exception {
        super.setup();

        CommandLineHelper.useHomeDir("target");
        PluginHelper.createPluginConfig();
        downloader = new FakePluginDownloader();
    }

    @Test
    public void shouldAddDefaultPlugin() throws Exception {
        TestPluginAdd command = newCommand();
        command.name = "kubernetes";
        command.doCall();

        Assertions.assertEquals("Plugin kubernetes added", printer.getOutput());

        Assertions.assertEquals(
                "{\"plugins\":{\"kubernetes\":{\"name\":\"kubernetes\",\"command\":\"kubernetes\",\"firstVersion\":\"4.8.0\",\"description\":\"%s\"}}}"
                        .formatted(PluginType.KUBERNETES.getDescription()),
                PluginHelper.getOrCreatePluginConfig().toJson());
    }

    @Test
    public void shouldResolveKitDependencyFromKnownPlugins() throws Exception {
        TestPluginAdd command = newCommand();
        command.name = "kit";
        command.version = "0.4.0";
        command.doCall();

        Assertions.assertEquals("Plugin kit added", printer.getOutput());

        JsonObject plugins = PluginHelper.getOrCreatePluginConfig().getMap("plugins");
        JsonObject plugin = plugins.getMap("kit");
        Assertions.assertEquals("io.github.luigidemasi:camel-jbang-plugin-kit:0.4.0", plugin.getString("dependency"));
    }

    @Test
    public void shouldAddPlugin() throws Exception {
        TestPluginAdd command = newCommand();
        command.name = "foo-plugin";
        command.command = "foo";
        command.description = "Some plugin";
        command.firstVersion = "1.2.3";
        command.doCall();

        Assertions.assertEquals("Plugin foo-plugin added", printer.getOutput());

        Assertions.assertEquals("{\"plugins\":{\"foo-plugin\":{\"name\":\"foo-plugin\",\"command\":\"foo\"," +
                                "\"firstVersion\":\"1.2.3\",\"description\":\"Some plugin\"}}}",
                PluginHelper.getOrCreatePluginConfig().toJson());
    }

    @Test
    public void shouldGenerateProperties() throws Exception {
        TestPluginAdd command = newCommand();
        command.name = "foo";
        command.doCall();

        Assertions.assertEquals("Plugin foo added", printer.getOutput());

        Assertions.assertEquals("{\"plugins\":{\"foo\":{\"name\":\"foo\",\"command\":\"foo\"," +
                                "\"description\":\"Plugin foo called with command foo\"}}}",
                PluginHelper.getOrCreatePluginConfig().toJson());
    }

    @Test
    public void shouldUseMavenGAV() throws Exception {
        TestPluginAdd command = newCommand();
        command.name = "foo-plugin";
        command.command = "foo";
        command.gav = "org.apache.camel:foo-plugin:1.0.0";
        command.doCall();

        Assertions.assertEquals("Plugin foo-plugin added", printer.getOutput());

        Assertions.assertEquals("{\"plugins\":{\"foo-plugin\":{\"name\":\"foo-plugin\",\"command\":\"foo\"," +
                                "\"description\":\"Plugin foo-plugin called with command foo\",\"dependency\":\"org.apache.camel:foo-plugin:1.0.0\"}}}",
                PluginHelper.getOrCreatePluginConfig().toJson());
    }

    @Test
    public void shouldUseArtifactIdAndVersion() throws Exception {
        TestPluginAdd command = newCommand();
        command.name = "foo-plugin";
        command.command = "foo";
        command.groupId = "org.foo";
        command.artifactId = "foo-bar";
        command.version = "1.0.0";
        command.doCall();

        Assertions.assertEquals("Plugin foo-plugin added", printer.getOutput());

        Assertions.assertEquals("{\"plugins\":{\"foo-plugin\":{\"name\":\"foo-plugin\",\"command\":\"foo\"," +
                                "\"firstVersion\":\"1.0.0\",\"description\":\"Plugin foo-plugin called with command foo\"" +
                                ",\"dependency\":\"org.foo:foo-bar:1.0.0\"}}}",
                PluginHelper.getOrCreatePluginConfig().toJson());
    }

    @Test
    public void shouldNotResolveVersionOfDefaultPlugin() throws Exception {
        TestPluginAdd command = newCommand();
        command.name = "kubernetes";
        command.doCall();

        Assertions.assertTrue(downloader.descriptorsRead.isEmpty());
        Assertions.assertTrue(command.downloaded.isEmpty());
    }

    @Test
    public void shouldSelectReleaseForCliVersion() throws Exception {
        downloader.release("2.0.0", "4.23.0", "[4.23.0,4.24.0)")
                .release("1.6.2", "4.22.2", null)
                .release("1.6.1", "4.22.1", null);

        TestPluginAdd command = newFixtureCommand();
        Assertions.assertEquals(0, command.doCall());

        Assertions.assertEquals("Selected fixture 1.6.2 for Camel CLI 4.22.0 (built for Camel 4.22.2)"
                                + System.lineSeparator() + "Plugin fixture added",
                printer.getOutput());
        Assertions.assertEquals(List.of("org.example.plugins:camel-jbang-plugin-fixture:1.6.2"), command.downloaded);

        JsonObject plugin = fixtureEntry();
        Assertions.assertEquals("org.example.plugins:camel-jbang-plugin-fixture:1.6.2", plugin.getString("dependency"));
        Assertions.assertEquals("[4.22.0,4.23.0)", plugin.getString("camelVersions"));
        Assertions.assertEquals("inferred", plugin.getString("camelVersionsSource"));
    }

    @Test
    public void shouldSelectDeclaredRange() throws Exception {
        downloader.release("2.0.0", "4.23.0", "[4.23.0,4.24.0)")
                .release("1.6.2", "4.22.2", null);

        TestPluginAdd command = newFixtureCommand();
        command.cli = "4.23.0-SNAPSHOT";
        Assertions.assertEquals(0, command.doCall());

        JsonObject plugin = fixtureEntry();
        Assertions.assertEquals("org.example.plugins:camel-jbang-plugin-fixture:2.0.0", plugin.getString("dependency"));
        Assertions.assertEquals("[4.23.0,4.24.0)", plugin.getString("camelVersions"));
        Assertions.assertEquals("declared", plugin.getString("camelVersionsSource"));
    }

    @Test
    public void shouldNotChangeConfigWhenSelectedReleaseCannotBeDownloaded() throws Exception {
        downloader.release("1.6.2", "4.22.2", null);
        String before = PluginHelper.getOrCreatePluginConfig().toJson();

        TestPluginAdd command = newFixtureCommand();
        command.downloadFails = true;
        Assertions.assertEquals(1, command.doCall());

        Assertions.assertTrue(
                printer.getOutput().contains("Cannot download org.example.plugins:camel-jbang-plugin-fixture:1.6.2"),
                printer.getOutput());
        Assertions.assertEquals(before, PluginHelper.getOrCreatePluginConfig().toJson());
    }

    @Test
    public void shouldUseLatestWhenNoReleaseMatches() throws Exception {
        downloader.release("2.0.0", "4.23.0", "[4.23.0,4.24.0)")
                .release("1.6.2", "4.22.2", null);

        TestPluginAdd command = newFixtureCommand();
        command.cli = "4.24.0";
        Assertions.assertEquals(0, command.doCall());

        String warning = printer.getOutput();
        Assertions.assertTrue(warning.startsWith("WARN: No release of fixture is built for Camel CLI 4.24.0 yet."
                                                 + " The newest, fixture 2.0.0, is built for Camel 4.23.0. Using LATEST."),
                warning);
        Assertions.assertTrue(warning.contains("Camel 4.23: fixture 2.0.0"), warning);
        Assertions.assertTrue(warning.contains("Camel 4.22: fixture 1.6.2"), warning);
        Assertions.assertTrue(warning.contains("camel plugin add fixture --version <version>"), warning);
        Assertions.assertTrue(command.downloaded.isEmpty());

        JsonObject plugin = fixtureEntry();
        Assertions.assertEquals("org.example.plugins:camel-jbang-plugin-fixture:LATEST", plugin.getString("dependency"));
        Assertions.assertNull(plugin.get("camelVersions"));
    }

    @Test
    public void shouldSelectNearestOlderReleaseWhenBetweenCamelVersions() throws Exception {
        downloader.release("2.0.0", "4.23.0", "[4.23.0,4.24.0)")
                .release("1.4.2", "4.18.5", null);

        TestPluginAdd command = newFixtureCommand();
        command.cli = "4.21.0";
        Assertions.assertEquals(0, command.doCall());

        Assertions.assertEquals("WARN: No release of fixture is built for Camel CLI 4.21.0. Selected fixture 1.4.2,"
                                + " the newest release for an older Camel version (built for Camel 4.18.5)."
                                + System.lineSeparator() + "Plugin fixture added",
                printer.getOutput());
        Assertions.assertEquals(List.of("org.example.plugins:camel-jbang-plugin-fixture:1.4.2"), command.downloaded);
        JsonObject plugin = fixtureEntry();
        Assertions.assertEquals("org.example.plugins:camel-jbang-plugin-fixture:1.4.2", plugin.getString("dependency"));
        Assertions.assertEquals("[4.18.0,4.19.0)", plugin.getString("camelVersions"));
        Assertions.assertEquals("inferred", plugin.getString("camelVersionsSource"));
    }

    @Test
    public void shouldUseLatestWhenCliIsOlderThanAllReleases() throws Exception {
        downloader.release("2.0.0", "4.23.0", "[4.23.0,4.24.0)")
                .release("1.4.2", "4.18.5", null);

        TestPluginAdd command = newFixtureCommand();
        command.cli = "4.14.0";
        Assertions.assertEquals(0, command.doCall());

        String warning = printer.getOutput();
        Assertions.assertTrue(warning.startsWith(
                "WARN: No release of fixture is built for Camel CLI 4.14.0 or an older Camel version. Using LATEST."),
                warning);
        Assertions.assertTrue(warning.contains("Camel 4.18: fixture 1.4.2"), warning);
        Assertions.assertTrue(command.downloaded.isEmpty());
        JsonObject plugin = fixtureEntry();
        Assertions.assertEquals("org.example.plugins:camel-jbang-plugin-fixture:LATEST", plugin.getString("dependency"));
    }

    @Test
    public void shouldUseLatestWhenNoReleaseTellsItsCamelVersions() throws Exception {
        downloader.release("2.0.0", null, null);

        TestPluginAdd command = newFixtureCommand();
        Assertions.assertEquals(0, command.doCall());

        Assertions.assertEquals("WARN: No release of fixture tells which Camel versions it supports. Using LATEST."
                                + System.lineSeparator() + "Plugin fixture added",
                printer.getOutput());
        JsonObject plugin = fixtureEntry();
        Assertions.assertEquals("org.example.plugins:camel-jbang-plugin-fixture:LATEST", plugin.getString("dependency"));
    }

    @Test
    public void shouldUseLatestWhenCompatibilityCannotBeChecked() throws Exception {
        downloader.listingDown = true;

        TestPluginAdd command = newFixtureCommand();
        Assertions.assertEquals(0, command.doCall());

        Assertions.assertEquals("WARN: Could not check plugin compatibility (Cannot resolve available versions of"
                                + " org.example.plugins:camel-jbang-plugin-fixture). Using LATEST."
                                + System.lineSeparator() + "Plugin fixture added",
                printer.getOutput());
        JsonObject plugin = fixtureEntry();
        Assertions.assertEquals("org.example.plugins:camel-jbang-plugin-fixture:LATEST", plugin.getString("dependency"));
    }

    @Test
    public void shouldWarnWhenExplicitVersionIsBuiltForAnotherCamel() throws Exception {
        downloader.release("1.6.2", "4.22.2", null);

        TestPluginAdd command = newFixtureCommand();
        command.version = "1.6.2";
        command.cli = "4.23.0";
        Assertions.assertEquals(0, command.doCall());

        Assertions.assertEquals("WARN: fixture 1.6.2 is built for Camel [4.22.0,4.23.0); this CLI is 4.23.0"
                                + System.lineSeparator() + "Plugin fixture added",
                printer.getOutput());
        Assertions.assertTrue(command.downloaded.isEmpty());
        JsonObject plugin = fixtureEntry();
        Assertions.assertEquals("org.example.plugins:camel-jbang-plugin-fixture:1.6.2", plugin.getString("dependency"));
        Assertions.assertNull(plugin.get("camelVersions"));
    }

    @Test
    public void shouldWarnWhenGavIsBuiltForAnotherCamel() throws Exception {
        downloader.release("1.6.2", "4.22.2", null);

        TestPluginAdd command = newCommand();
        command.name = "fixture";
        command.gav = "org.example.plugins:camel-jbang-plugin-fixture:1.6.2";
        command.cli = "4.23.0";
        Assertions.assertEquals(0, command.doCall());

        Assertions.assertEquals("WARN: fixture 1.6.2 is built for Camel [4.22.0,4.23.0); this CLI is 4.23.0"
                                + System.lineSeparator() + "Plugin fixture added",
                printer.getOutput());
        JsonObject plugin = fixtureEntry();
        Assertions.assertEquals("org.example.plugins:camel-jbang-plugin-fixture:1.6.2", plugin.getString("dependency"));
    }

    @Test
    public void shouldNotWarnWhenExplicitVersionMatches() throws Exception {
        downloader.release("1.6.2", "4.22.2", null);

        TestPluginAdd command = newFixtureCommand();
        command.version = "1.6.2";
        Assertions.assertEquals(0, command.doCall());

        Assertions.assertEquals("Plugin fixture added", printer.getOutput());
    }

    private static JsonObject fixtureEntry() {
        JsonObject plugins = PluginHelper.getOrCreatePluginConfig().getMap("plugins");
        return plugins.getMap("fixture");
    }

    private TestPluginAdd newCommand() {
        return new TestPluginAdd(new CamelJBangMain().withPrinter(printer), downloader);
    }

    // a community plugin under its own groupId
    private TestPluginAdd newFixtureCommand() {
        TestPluginAdd command = newCommand();
        command.name = "fixture";
        command.groupId = "org.example.plugins";
        command.artifactId = "camel-jbang-plugin-fixture";
        return command;
    }

    /**
     * Reads the releases from a fake downloader, and does not download the plugin.
     */
    private static class TestPluginAdd extends PluginAdd {

        private final DependencyDownloader downloader;
        private final List<String> downloaded = new ArrayList<>();
        private String cli = "4.22.0";
        private boolean downloadFails;

        TestPluginAdd(CamelJBangMain main, DependencyDownloader downloader) {
            super(main);
            this.downloader = downloader;
        }

        @Override
        String cliVersion() {
            return cli;
        }

        @Override
        DependencyDownloader newDownloader() {
            return downloader;
        }

        @Override
        Optional<Plugin> downloadPlugin(String dependency) {
            downloaded.add(dependency);
            return downloadFails ? Optional.empty() : Optional.of(new CachedFakePlugin());
        }
    }
}
