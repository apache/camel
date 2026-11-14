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

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.Plugin;
import org.apache.camel.dsl.jbang.core.common.PluginCompatibility;
import org.apache.camel.dsl.jbang.core.common.PluginCompatibility.Compat;
import org.apache.camel.dsl.jbang.core.common.PluginCompatibility.Source;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.core.common.PluginType;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.NoMatch;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.Older;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.Result;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.Selected;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.Unavailable;
import org.apache.camel.dsl.jbang.run.common.Printer;
import org.apache.camel.dsl.jbang.run.common.StringPrinter;
import org.apache.camel.main.download.DependencyDownloader;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.main.util.SuggestSimilarHelper;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.json.JsonObject;

@CommandDefinition(name = "add", description = "Add new plugin", generateHelp = true,
                   footer = {
                           "Examples:",
                           "  camel plugin add --command=my-cmd --gav=com.foo:bar:1.0" })
public class PluginAdd extends PluginBaseCommand {

    @Argument(required = true, description = "The Camel plugin to add", paramLabel = "<name>")
    String name;

    @Option(name = "command", description = "The command that the plugin uses")
    String command;

    @Option(name = "description", description = "A short description of the plugin")
    String description;

    @Option(name = "repo", aliases = { "repos" },
            description = "Additional maven repositories to use for downloading the plugin (Use commas to separate multiple repositories)")
    String repositories;

    @Option(name = "artifactId", description = "Maven artifactId")
    String artifactId;

    @Option(name = "groupId", description = "Maven groupId", defaultValue = "org.apache.camel")
    String groupId = "org.apache.camel";

    @Option(name = "version", description = "Maven artifact version", defaultValue = "${camel-version}")
    String version;

    @Option(name = "first-version", description = "First version of this plugin", defaultValue = "${camel-version}")
    String firstVersion;

    @Option(name = "gav", description = "Maven group and artifact coordinates.")
    String gav;

    public PluginAdd(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        JsonObject pluginConfig = loadConfig();
        JsonObject plugins = pluginConfig.getMap("plugins");

        String[] appliesTo = null;
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
            appliesTo = camelPlugin.get().getAppliesTo();
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
            } else if (gav == null && artifactId == null && !resolves()) {
                // stored, a plugin that cannot be loaded broke every camel run and camel export (CAMEL-26931)
                throw unknownPlugin(name);
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
        if (appliesTo != null && appliesTo.length > 0) {
            plugin.put("appliesTo", PluginHelper.toJsonArray(appliesTo));
        }

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
     * Whether a plugin of a name that is neither a Camel plugin nor a known one can be loaded, as it is when used: from
     * the classpath, or as org.apache.camel:camel-jbang-plugin-&lt;command&gt;.
     */
    private boolean resolves() {
        if (command == null) {
            command = name;
        }
        try {
            // quietly: the answer says why it is not added
            return downloadPlugin(null, new StringPrinter()).isPresent();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * A plugin that is neither a Camel plugin nor a known one, given without --gav or --artifactId, and that cannot be
     * loaded: exit code 3, with the plugins of a similar name.
     */
    static CliException unknownPlugin(String name) {
        List<String> names = new ArrayList<>();
        for (PluginType type : PluginType.values()) {
            names.add(type.getName());
        }
        for (JsonObject known : PluginHelper.loadKnownPlugins()) {
            String n = known.getString("name");
            if (n != null) {
                names.add(n);
            }
        }
        List<String> similar = SuggestSimilarHelper.didYouMean(names, name);
        return CliException.notFound("Unknown plugin: " + name
                                     + (similar.isEmpty() ? "" : ". Did you mean: " + String.join(", ", similar))
                                     + ". camel plugin list shows the plugins; give --gav (or --artifactId) to add a"
                                     + " plugin of your own");
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
            result = new PluginVersionResolver(downloader).resolve(groupId, artifactId, cli);
        } finally {
            downloader.stop();
            downloader.close();
        }

        if (result instanceof Selected selected) {
            if (!useRelease(plugin, selected.version(), selected.compat())) {
                return false;
            }
            printer().printf("Selected %s %s for Camel CLI %s (%s)%n", name, version, cli, selected.compat().describe());
        } else if (result instanceof Older older) {
            if (!useRelease(plugin, older.version(), older.compat())) {
                return false;
            }
            // warned here, so the load-time check stays quiet until the CLI moves to another Camel version
            plugin.put("selectedForCamel", PluginCompatibility.minorLine(cli));
            printer().printWarn(
                    "No release of %s is built for Camel CLI %s. Selected %s %s, the newest release for an older Camel version (%s)."
                            .formatted(name, cli, name, version, older.compat().describe()));
        } else if (result instanceof NoMatch noMatch) {
            StringBuilder sb = new StringBuilder();
            switch (noMatch.reason()) {
                case CLI_NEWER -> sb.append("No release of %s is built for Camel CLI %s yet. The newest, %s %s, is %s."
                        .formatted(name, cli, name, noMatch.newestVersion(), noMatch.newest().describe()));
                case CLI_OLDER -> sb.append("No release of %s is built for Camel CLI %s or an older Camel version."
                        .formatted(name, cli));
                default -> sb.append("No release of %s tells which Camel versions it supports.".formatted(name));
            }
            sb.append(" Using LATEST.");
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
     * Downloads the release, and stores the Camel versions it supports in the plugin entry.
     *
     * @return false when the release cannot be downloaded
     */
    private boolean useRelease(JsonObject plugin, String release, Compat compat) {
        version = release;
        String releaseGav = "%s:%s:%s".formatted(groupId, artifactId, version);
        Optional<Plugin> resolved;
        try {
            resolved = downloadPlugin(releaseGav);
        } catch (Exception e) {
            printer().printErr("Cannot download " + releaseGav, e);
            return false;
        }
        if (resolved.isEmpty()) {
            printer().printErr("Cannot download " + releaseGav);
            return false;
        }
        plugin.put("camelVersions", compat.range());
        plugin.put("camelVersionsSource", compat.source().label());
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
        // without a command line (such as in tests) the field is only set when asked for
        return isOptionGiven("--version") || version != null;
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
        return downloadPlugin(dependency, printer());
    }

    /**
     * Downloads the plugin, as when it is used, reporting to the given printer.
     */
    Optional<Plugin> downloadPlugin(String dependency, Printer printer) {
        return PluginHelper.getPlugin(command, cliVersion(), dependency, repositories, printer);
    }

}
