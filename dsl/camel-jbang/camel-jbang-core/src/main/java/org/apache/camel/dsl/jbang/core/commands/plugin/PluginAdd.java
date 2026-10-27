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

import java.util.Optional;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.Plugin;
import org.apache.camel.dsl.jbang.core.common.PluginCompatibility;
import org.apache.camel.dsl.jbang.core.common.PluginCompatibility.Compat;
import org.apache.camel.dsl.jbang.core.common.PluginCompatibility.Source;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.core.common.PluginType;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.NoMatch;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.Result;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.Selected;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.Unavailable;
import org.apache.camel.main.download.DependencyDownloader;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "add",
                     description = "Add new plugin", sortOptions = false, showDefaultValues = true,
                     footer = {
                             "%nExamples:",
                             "  camel plugin add --command=my-cmd --gav=com.foo:bar:1.0" })
public class PluginAdd extends PluginBaseCommand {

    @CommandLine.Parameters(description = "The Camel plugin to add",
                            paramLabel = "<name>")
    String name;

    @CommandLine.Option(names = { "--command" },
                        description = "The command that the plugin uses")
    String command;

    @CommandLine.Option(names = { "--description" },
                        description = "A short description of the plugin")
    String description;

    @CommandLine.Option(names = { "--repo", "--repos" },
                        description = "Additional maven repositories to use for downloading the plugin (Use commas to separate multiple repositories)")
    String repositories;

    @CommandLine.Option(names = { "--artifactId" },
                        description = "Maven artifactId")
    String artifactId;

    @CommandLine.Option(names = { "--groupId" },
                        defaultValue = "org.apache.camel",
                        description = "Maven groupId")
    String groupId = "org.apache.camel";

    @CommandLine.Option(names = { "--version" },
                        defaultValue = "${camel-version}",
                        description = "Maven artifact version")
    String version;

    @CommandLine.Option(names = { "--first-version" },
                        defaultValue = "${camel-version}",
                        description = "First version of this plugin")
    String firstVersion;

    @CommandLine.Option(names = { "--gav" },
                        description = "Maven group and artifact coordinates.")
    String gav;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec commandSpec;

    public PluginAdd(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        JsonObject pluginConfig = loadConfig();
        JsonObject plugins = pluginConfig.getMap("plugins");

        Optional<PluginType> camelPlugin = PluginType.findByName(name);
        if (camelPlugin.isPresent()) {
            if (command == null) {
                command = camelPlugin.get().getCommand();
            }

            if (description == null) {
                description = camelPlugin.get().getDescription();
            }
            if (firstVersion == null) {
                firstVersion = camelPlugin.get().getFirstVersion();
            }
        } else {
            Optional<JsonObject> known = PluginHelper.findKnownPlugin(name);
            if (known.isPresent()) {
                JsonObject kp = known.get();
                if (command == null) {
                    command = kp.getString("command");
                }
                if (description == null) {
                    description = kp.getString("description");
                }
                if (firstVersion == null) {
                    firstVersion = kp.getString("firstVersion");
                }
                if (gav == null) {
                    groupId = kp.getStringOrDefault("groupId", groupId);
                    artifactId = kp.getString("artifactId");
                }
                if (repositories == null) {
                    String knownRepos = kp.getString("repos");
                    if (knownRepos != null) {
                        repositories = knownRepos;
                    }
                }
            }
        }

        if (command == null) {
            // use plugin name as command
            command = name;
        }
        if (firstVersion == null) {
            // fallback to version specified
            firstVersion = version;
        }

        JsonObject plugin = new JsonObject();
        plugin.put("name", name);
        plugin.put("command", command);
        if (firstVersion != null) {
            plugin.put("firstVersion", firstVersion);
        }
        plugin.put("description",
                description != null ? description : "Plugin %s called with command %s".formatted(name, command));

        boolean explicit = gav != null || isVersionGiven();
        if (gav == null && (groupId != null && artifactId != null)) {
            if (version == null) {
                if ("org.apache.camel".equals(groupId)) {
                    CamelCatalog catalog = new DefaultCamelCatalog();
                    version = catalog.getCatalogVersion();
                } else {
                    // a community plugin: select the newest release built for this Camel CLI
                    if (!selectVersion(plugin)) {
                        // the selected release cannot be downloaded
                        return 1;
                    }
                    if (version == null) {
                        version = "LATEST";
                    }
                }
            }

            gav = "%s:%s:%s".formatted(groupId, artifactId, version);
        }

        if (gav != null) {
            plugin.put("dependency", gav);
            if (explicit) {
                warnOnMismatch(gav);
            }
        }
        if (repositories != null) {
            plugin.put("repos", repositories);
        }

        plugins.put(name, plugin);

        saveConfig(pluginConfig);
        printer().printf("Plugin %s added%n", name);
        return 0;
    }

    /**
     * Selects the newest release of a community plugin built for this Camel CLI, and downloads it, so a release that
     * cannot be used never gets into the configuration. Sets {@link #version} to the selected release, or leaves it
     * null to use LATEST when no release can be selected.
     *
     * @return false when the selected release cannot be downloaded
     */
    private boolean selectVersion(JsonObject plugin) throws Exception {
        String cli = cliVersion();
        Result result;
        DependencyDownloader downloader = newDownloader();
        try {
            result = new PluginVersionResolver(downloader).resolve(groupId, artifactId, cli, repositories);
        } finally {
            downloader.stop();
            downloader.close();
        }

        if (result instanceof Selected selected) {
            version = selected.version();
            String selectedGav = "%s:%s:%s".formatted(groupId, artifactId, version);
            Optional<Plugin> resolved;
            try {
                resolved = downloadPlugin(selectedGav);
            } catch (Exception e) {
                printer().printErr("Cannot download " + selectedGav, e);
                return false;
            }
            if (resolved.isEmpty()) {
                printer().printErr("Cannot download " + selectedGav);
                return false;
            }
            Compat compat = selected.compat();
            plugin.put("camelVersions", compat.range());
            plugin.put("camelVersionsSource", compat.source().label());
            printer().printf("Selected %s %s for Camel CLI %s (%s)%n", name, version, cli, compat.describe());
        } else if (result instanceof NoMatch noMatch) {
            StringBuilder sb = new StringBuilder();
            sb.append("No release of %s is known to support Camel CLI %s. The newest, %s %s, is %s. Using LATEST."
                    .formatted(name, cli, name, noMatch.newestVersion(), noMatch.newest().describe()));
            if (!noMatch.newestPerLine().isEmpty()) {
                sb.append(System.lineSeparator()).append("Newest release per Camel version:");
                noMatch.newestPerLine().forEach((line, v) -> sb.append(System.lineSeparator())
                        .append("  Camel %s: %s %s".formatted(line, name, v)));
                sb.append(System.lineSeparator())
                        .append("To use one of them: camel plugin add %s --version <version>".formatted(name));
            }
            printer().printWarn(sb.toString());
        } else if (result instanceof Unavailable unavailable) {
            printer().printWarn("Could not check plugin compatibility (%s). Using LATEST.".formatted(unavailable.reason()));
        }
        return true;
    }

    /**
     * Warns when a release asked for with --version or --gav is known to be built for another Camel version. Best
     * effort: says nothing when the POM cannot be read, and never fails the command.
     */
    private void warnOnMismatch(String dependency) {
        MavenGav mavenGav;
        try {
            mavenGav = MavenGav.parseGav(dependency);
        } catch (Exception e) {
            return;
        }
        String g = mavenGav.getGroupId();
        String v = mavenGav.getVersion();
        // the Camel plugins are released with Camel, and LATEST or a range is not a release
        if (g == null || "org.apache.camel".equals(g) || v == null || v.isEmpty() || !Character.isDigit(v.charAt(0))) {
            return;
        }
        String cli = cliVersion();
        DependencyDownloader downloader = null;
        try {
            downloader = newDownloader();
            Compat compat = PluginCompatibility.of(
                    downloader.resolveArtifactDescriptor(g, mavenGav.getArtifactId(), v, null));
            if (compat.source() != Source.UNKNOWN && !PluginCompatibility.accepts(compat.range(), cli)) {
                printer().printWarn("%s %s is built for Camel %s; this CLI is %s".formatted(name, v, compat.range(), cli));
            }
        } catch (Exception e) {
            // best effort
        } finally {
            if (downloader != null) {
                try {
                    downloader.stop();
                    downloader.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    /**
     * Whether --version was given on the command line (its default comes from a {@code camel-version} system property
     * or environment variable).
     */
    private boolean isVersionGiven() {
        CommandLine.ParseResult result = commandSpec != null ? commandSpec.commandLine().getParseResult() : null;
        if (result != null) {
            return result.hasMatchedOption("--version");
        }
        // without picocli (such as in tests) the field is only set when asked for
        return version != null;
    }

    /**
     * The version of this Camel CLI.
     */
    String cliVersion() {
        return new DefaultCamelCatalog().getCatalogVersion();
    }

    /**
     * A started downloader to read the releases and POMs of a plugin with.
     */
    DependencyDownloader newDownloader() {
        DependencyDownloader downloader = new MavenDependencyDownloader();
        if (repositories != null && !repositories.isBlank()) {
            downloader.setRepositories(repositories);
        }
        downloader.setPreferLocal(true);
        downloader.start();
        return downloader;
    }

    /**
     * Downloads the plugin, as when it is used.
     */
    Optional<Plugin> downloadPlugin(String dependency) {
        return PluginHelper.getPlugin(command, cliVersion(), dependency, repositories, printer());
    }

}
