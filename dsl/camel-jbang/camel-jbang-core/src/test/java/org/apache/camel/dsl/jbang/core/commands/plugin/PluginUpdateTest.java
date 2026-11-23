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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CachedFakePlugin;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.FakePluginDownloader;
import org.apache.camel.dsl.jbang.core.common.Plugin;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.main.download.DependencyDownloader;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * The decisions of camel plugin update on the entries of the plugin configuration. How a release is selected for a
 * Camel CLI is tested in PluginVersionResolverTest and PluginAddTest.
 */
class PluginUpdateTest extends CamelCommandBaseTestSupport {

    private static final String GAV = "org.example.plugins:camel-jbang-plugin-fixture:";

    private FakePluginDownloader downloader;
    private Path config;

    @BeforeEach
    public void setup() throws Exception {
        super.setup();
        config = useTestHomeDir().resolve("plugins.json");
        downloader = new FakePluginDownloader();
    }

    static Stream<Arguments> rewritesAutoSelectedEntry() {
        return Stream.of(
                arguments("1.6.2", "[4.22.0,4.23.0)", "4.23.0",
                        (Consumer<FakePluginDownloader>) d -> d.release("2.0.0", "4.23.0", "[4.23.0,4.24.0)")
                                .release("1.6.2", "4.22.2", null),
                        "fixture 1.6.2 -> 2.0.0 (built for Camel 4.23.0)", "2.0.0", "[4.23.0,4.24.0)", "declared", null),
                arguments("1.6.1", "[4.22.0,4.23.0)", "4.22.0",
                        (Consumer<FakePluginDownloader>) d -> d.release("1.6.2", "4.22.2", null)
                                .release("1.6.1", "4.22.1", null),
                        "fixture 1.6.1 -> 1.6.2 (built for Camel 4.22.2)", "1.6.2", "[4.22.0,4.23.0)", "inferred", null),
                // no release for this CLI, which is between Camel versions: the newest release for an older one
                arguments("1.4.1", "[4.18.0,4.19.0)", "4.21.0",
                        (Consumer<FakePluginDownloader>) d -> d.release("2.0.0", "4.23.0", "[4.23.0,4.24.0)")
                                .release("1.4.2", "4.18.5", null).release("1.4.1", "4.18.1", null),
                        "fixture 1.4.1 -> 1.4.2 (built for Camel 4.18.5)" + System.lineSeparator()
                                                                                                    + "WARN: No release of fixture is built for Camel CLI 4.21.0: 1.4.2 is the newest release for an older Camel version.",
                        "1.4.2", "[4.18.0,4.19.0)", "inferred", "4.21"),
                // the same release, for another Camel version: so the load-time check stays quiet
                arguments("1.4.2", "[4.18.0,4.19.0)", "4.21.0",
                        (Consumer<FakePluginDownloader>) d -> d.release("2.0.0", "4.23.0", "[4.23.0,4.24.0)")
                                .release("1.4.2", "4.18.5", null),
                        "WARN: No release of fixture is built for Camel CLI 4.21.0. Keeping fixture 1.4.2, the newest"
                                                                   + " release for an older Camel version (built for Camel 4.18.5).",
                        "1.4.2", "[4.18.0,4.19.0)", "inferred", "4.21"));
    }

    @ParameterizedTest
    @MethodSource
    void rewritesAutoSelectedEntry(
            String current, String camelVersions, String cli, Consumer<FakePluginDownloader> releases, String output,
            String version, String range, String source, String selectedForCamel)
            throws Exception {
        releases.accept(downloader);
        writeConfig(fixture(current, camelVersions));

        TestPluginUpdate command = newCommand(cli);
        assertThat(command.doCall()).isZero();

        assertThat(printer.getOutput()).isEqualTo(output);
        JsonObject entry = entry("fixture");
        assertThat(entry.getString("dependency")).isEqualTo(GAV + version);
        assertThat(entry.getString("camelVersions")).isEqualTo(range);
        assertThat(entry.getString("camelVersionsSource")).isEqualTo(source);
        assertThat(entry.getString("selectedForCamel")).isEqualTo(selectedForCamel);
        // the class path of the release before is not used for the new one
        assertThat(entry.containsKey("resolved")).isEqualTo(version.equals(current));
        assertThat(entry.getString("description")).isEqualTo("A fixture plugin");
        assertThat(command.downloaded).isEqualTo(version.equals(current) ? List.of() : List.of(GAV + version));
    }

    static Stream<Arguments> keepsAutoSelectedEntry() {
        return Stream.of(
                arguments("4.22.0",
                        (Consumer<FakePluginDownloader>) d -> d.release("1.6.2", "4.22.2", null)
                                .release("1.6.1", "4.22.1", null),
                        "fixture 1.6.2 is up to date (built for Camel 4.22.2)"),
                arguments("4.25.0",
                        (Consumer<FakePluginDownloader>) d -> d.release("2.0.0", "4.23.0", "[4.23.0,4.24.0)")
                                .release("1.6.2", "4.22.2", null),
                        // its release for a new Camel version may come some time after the Camel release
                        "WARN: No release of fixture is built for Camel CLI 4.25.0 yet (the newest, 2.0.0, is built for"
                                                                   + " Camel 4.23.0). Keeping fixture 1.6.2: run 'camel plugin update fixture' again once a"
                                                                   + " release built for this Camel version is published."),
                // offline, or the repositories cannot be reached
                arguments("4.23.0",
                        (Consumer<FakePluginDownloader>) d -> d.listingDown = true,
                        "WARN: Could not check the releases of fixture (Cannot resolve available versions of"
                                                                                    + " org.example.plugins:camel-jbang-plugin-fixture). Keeping fixture 1.6.2."),
                // the stored release is built for this CLI, but is not among the releases read
                arguments("4.22.0",
                        (Consumer<FakePluginDownloader>) d -> d.release("2.0.0", "4.23.0", "[4.23.0,4.24.0)")
                                .release("1.4.2", "4.18.5", null),
                        "fixture 1.6.2 is up to date (built for Camel [4.22.0,4.23.0))"),
                // the POM of the stored release cannot be read, so an older patch is selected: no downgrade
                arguments("4.22.0",
                        (Consumer<FakePluginDownloader>) d -> {
                            d.versions.add("1.6.2");
                            d.release("1.6.1", "4.22.1", null);
                        },
                        "fixture 1.6.2 is up to date (built for Camel [4.22.0,4.23.0))"));
    }

    @ParameterizedTest
    @MethodSource
    void keepsAutoSelectedEntry(String cli, Consumer<FakePluginDownloader> releases, String output) throws Exception {
        releases.accept(downloader);
        String before = writeConfig(fixture("1.6.2", "[4.22.0,4.23.0)"));

        TestPluginUpdate command = newCommand(cli);
        assertThat(command.doCall()).isZero();

        assertThat(printer.getOutput()).startsWith(output);
        assertThat(config).hasContent(before);
        assertThat(command.downloaded).isEmpty();
    }

    @Test
    void reportsEntriesWithoutSelectedRelease() throws Exception {
        String before = writeConfig(
                """
                        "kubernetes": {
                          "name": "kubernetes",
                          "command": "kubernetes"
                        }""",
                """
                        "kit": {
                          "name": "kit",
                          "command": "kit",
                          "dependency": "io.github.luigidemasi:camel-jbang-plugin-kit:0.4.0"
                        }""",
                """
                        "forage": {
                          "name": "forage",
                          "command": "forage",
                          "dependency": "io.kaoto.forage:camel-jbang-plugin-forage:LATEST"
                        }""",
                """
                        "mine": {
                          "name": "mine",
                          "command": "my",
                          "dependency": "com.example:camel-jbang-plugin-my:LATEST"
                        }""");

        TestPluginUpdate command = newCommand("4.23.0");
        assertThat(command.doCall()).isZero();

        assertThat(printer.getOutput().lines()).containsExactly(
                "kubernetes is released with the Camel CLI, and uses its version",
                "kit 0.4.0 is kept: camel plugin add did not select this release (such as one given with --version or --gav)",
                "forage is kept as LATEST: it was added without the Camel versions it is built for. Run 'camel plugin"
                                                                                                                               + " add forage' to select the release built for this Camel CLI",
                "mine is kept as LATEST: it was added without the Camel versions it is built for. Run 'camel plugin add"
                                                                                                                                                                                                + " mine --groupId com.example --artifactId camel-jbang-plugin-my --command my' to select the"
                                                                                                                                                                                                + " release built for this Camel CLI");
        assertThat(config).hasContent(before);
        assertThat(downloader.listedFresh).isNull();
        assertThat(command.downloaded).isEmpty();
    }

    @Test
    void keepsEntryWhenSelectedReleaseCannotBeDownloaded() throws Exception {
        downloader.release("1.6.2", "4.22.2", null).release("1.6.1", "4.22.1", null);
        String before = writeConfig(fixture("1.6.1", "[4.22.0,4.23.0)"));

        TestPluginUpdate command = newCommand("4.22.0");
        command.downloadFails = true;
        assertThat(command.doCall()).isEqualTo(1);

        assertThat(printer.getOutput()).isEqualTo(
                "ERROR: Cannot download " + GAV + "1.6.2" + System.lineSeparator() + "WARN: Keeping fixture 1.6.1.");
        assertThat(config).hasContent(before);
    }

    @Test
    void dryRunReportsForAnotherCliWithoutDownloadingOrWriting() throws Exception {
        downloader.release("2.0.0", "4.23.0", "[4.23.0,4.24.0)").release("1.6.2", "4.22.2", null);
        String before = writeConfig(fixture("1.6.2", "[4.22.0,4.23.0)"));

        TestPluginUpdate command = newCommand("4.22.0");
        command.dryRunFor("4.23.0", null);
        assertThat(command.doCall()).isZero();

        assertThat(printer.getOutput()).isEqualTo("Dry run for Camel CLI 4.23.0, nothing is changed:"
                                                  + System.lineSeparator()
                                                  + "fixture 1.6.2 -> 2.0.0 (built for Camel 4.23.0)");
        assertThat(config).hasContent(before);
        assertThat(command.downloaded).isEmpty();
    }

    @Test
    void updatesOnlyTheNamedPlugin() throws Exception {
        downloader.release("1.6.2", "4.22.2", null);
        writeConfig(fixture("1.6.1", "[4.22.0,4.23.0)"),
                """
                        "kit": {
                          "name": "kit",
                          "dependency": "io.github.luigidemasi:camel-jbang-plugin-kit:0.4.0"
                        }""");
        // as the CLI writes it
        PluginHelper.savePluginConfig(PluginHelper.getPluginConfig(config), config);
        String kit = "\"kit\":{\"name\":\"kit\",\"dependency\":\"io.github.luigidemasi:camel-jbang-plugin-kit:0.4.0\"}";
        assertThat(config).content().contains(kit);

        TestPluginUpdate command = newCommand("4.22.0");
        command.name = "fixture";
        assertThat(command.doCall()).isZero();

        assertThat(printer.getOutput()).isEqualTo("fixture 1.6.1 -> 1.6.2 (built for Camel 4.22.2)");
        assertThat(entry("fixture").getString("dependency")).isEqualTo(GAV + "1.6.2");
        // the other plugin is left as it is, byte for byte
        assertThat(config).content().contains(kit);
    }

    @Test
    void unknownPluginIsNotFound() throws Exception {
        String before = writeConfig(fixture("1.6.1", "[4.22.0,4.23.0)"));

        TestPluginUpdate command = newCommand("4.22.0");
        command.name = "nope";
        assertThat(command.doCall()).isEqualTo(CliExitCode.NOT_FOUND);

        assertThat(printer.getOutput()).isEqualTo("ERROR: Plugin nope not found in configuration");
        assertThat(config).hasContent(before);
    }

    // an entry that camel plugin add stored for a release it selected, with the class path of that release cached
    private static String fixture(String version, String camelVersions) {
        return """
                "fixture": {
                  "name": "fixture",
                  "command": "fixture",
                  "description": "A fixture plugin",
                  "dependency": "%s%s",
                  "camelVersions": "%s",
                  "camelVersionsSource": "inferred",
                  "selectedForCamel": "4.20",
                  "resolved": {
                    "gav": "%s%s"
                  }
                }""".formatted(GAV, version, camelVersions, GAV, version);
    }

    // written as a person would, so a rewrite of the file would show
    private String writeConfig(String... entries) throws Exception {
        String json = "{\n  \"plugins\": {\n" + String.join(",\n", entries) + "\n  }\n}\n";
        Files.writeString(config, json);
        return json;
    }

    private JsonObject entry(String name) {
        JsonObject plugins = PluginHelper.getPluginConfig(config).getMap("plugins");
        return plugins.getMap(name);
    }

    private TestPluginUpdate newCommand(String cli) {
        TestPluginUpdate command = new CamelJBangMain().withPrinter(printer).command(new TestPluginUpdate(downloader));
        command.cli = cli;
        command.pluginConfig = config.toString();
        return command;
    }

    /**
     * Reads the releases from a fake downloader, and does not download the plugin.
     */
    private static class TestPluginUpdate extends PluginUpdate {

        private final DependencyDownloader downloader;
        private final List<String> downloaded = new ArrayList<>();
        private String cli;
        private boolean downloadFails;

        TestPluginUpdate(DependencyDownloader downloader) {
            this.downloader = downloader;
        }

        @Override
        String cliVersion() {
            return cli;
        }

        @Override
        DependencyDownloader newDownloader(String repos) {
            return downloader;
        }

        @Override
        Optional<Plugin> downloadPlugin(String command, String dependency, String repos) {
            downloaded.add(dependency);
            return downloadFails ? Optional.empty() : Optional.of(new CachedFakePlugin());
        }
    }
}
