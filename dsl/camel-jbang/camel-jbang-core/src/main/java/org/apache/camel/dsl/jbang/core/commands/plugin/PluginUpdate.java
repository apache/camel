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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.Plugin;
import org.apache.camel.dsl.jbang.core.common.PluginCompatibility;
import org.apache.camel.dsl.jbang.core.common.PluginCompatibility.Compat;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.core.common.PluginType;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.NoMatch;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.Older;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.Reason;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.Result;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.Selected;
import org.apache.camel.dsl.jbang.core.common.PluginVersionResolver.Unavailable;
import org.apache.camel.main.download.DependencyDownloader;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.json.JsonObject;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;

@CommandDefinition(name = "update",
                   description = "Update the community plugins to the releases built for this Camel CLI",
                   generateHelp = true,
                   footer = {
                           "Examples:",
                           "  camel plugin update",
                           "  camel plugin update forage",
                           "  camel plugin update --dry-run",
                           "",
                           "Only a community plugin whose release camel plugin add selected is changed.",
                           "A plugin added with --version or --gav, a plugin of the Apache Camel project,",
                           "and a plugin added before the CLI selected releases are only reported." })
public class PluginUpdate extends PluginBaseCommand {

    private static final GenericVersionScheme VERSION_SCHEME = new GenericVersionScheme();

    @Argument(description = "The plugin to update (default: all the plugins)", paramLabel = "name")
    String name;

    @Option(name = "dry-run", hasValue = false,
            description = "Report what would change, without downloading the plugins or changing anything")
    boolean dryRun;

    // the version of the Camel CLI to select the releases for, when it is not this CLI
    private String forCliVersion;

    private enum Outcome {
        KEPT,
        CHANGED,
        FAILED
    }

    /**
     * Reports what would change with another version of the Camel CLI, without downloading the plugins or changing
     * anything, as {@code camel self-update --check} does before installing it.
     *
     * @param cliVersion   the version of the Camel CLI
     * @param pluginConfig the plugin configuration file, or null for the one of this CLI
     */
    public void dryRunFor(String cliVersion, Path pluginConfig) {
        this.dryRun = true;
        this.forCliVersion = cliVersion;
        if (pluginConfig != null) {
            this.pluginConfig = pluginConfig.toString();
        }
    }

    @Override
    public Integer doCall() throws Exception {
        JsonObject pluginConfig = loadExistingConfig();
        JsonObject plugins = pluginConfig != null ? pluginConfig.getMap("plugins") : null;
        if (name != null && (plugins == null || !plugins.containsKey(name))) {
            printer().printErr("Plugin %s not found in configuration".formatted(name));
            return CliExitCode.NOT_FOUND;
        }
        if (plugins == null || plugins.isEmpty()) {
            printer().printDiagnostic("No plugins installed (camel plugin list lists the plugins that can be added)");
            return 0;
        }

        String cli = forCliVersion != null ? forCliVersion : cliVersion();
        if (dryRun) {
            printer().println("Dry run for Camel CLI %s, nothing is changed:".formatted(cli));
        }
        boolean changed = false;
        boolean failed = false;
        List<String> names = name != null ? List.of(name) : new ArrayList<>(plugins.keySet());
        for (String n : names) {
            Outcome outcome = update(n, plugins.getMap(n), cli);
            changed |= outcome == Outcome.CHANGED;
            failed |= outcome == Outcome.FAILED;
        }
        // an entry that did not change is left as it is: the file is only written when one did
        if (changed && !dryRun) {
            saveConfig(pluginConfig);
        }
        return failed ? 1 : 0;
    }

    private Outcome update(String plugin, JsonObject entry, String cli) throws Exception {
        String dependency = entry.getString("dependency");
        if (dependency == null || dependency.isBlank()) {
            printer().println("%s is released with the Camel CLI, and uses its version".formatted(plugin));
            return Outcome.KEPT;
        }
        MavenGav gav = parseGav(dependency);
        String current = gav != null ? gav.getVersion() : null;
        if (!PluginHelper.isRelease(current)) {
            // added before the CLI selected releases (or when it could not), as LATEST
            printer().println(("%s is kept as %s: it was added without the Camel versions it is built for. Run '%s' to"
                               + " select the release built for this Camel CLI")
                    .formatted(plugin, current != null ? current : dependency, addCommand(plugin, entry, gav)));
            return Outcome.KEPT;
        }
        if (!PluginHelper.isSelectedRelease(entry)) {
            printer().println(("%s %s is kept: camel plugin add did not select this release (such as one given with"
                               + " --version or --gav)")
                    .formatted(plugin, current));
            return Outcome.KEPT;
        }

        String range = entry.getString("camelVersions");
        Result result;
        try {
            result = resolve(gav, entry.getString("repos"), cli);
        } catch (Exception e) {
            result = new Unavailable(e.getMessage() != null ? e.getMessage() : e.getClass().getName());
        }

        String keeping = "Keeping %s %s.".formatted(plugin, current);
        if (result instanceof Unavailable unavailable) {
            printer().printWarn("Could not check the releases of %s (%s). %s".formatted(plugin, unavailable.reason(), keeping));
            return Outcome.KEPT;
        }
        boolean builtForCli = PluginCompatibility.accepts(range, cli);
        String selected;
        Compat compat;
        boolean older = false;
        // a release built for this CLI is never replaced by an older one, such as when the POM of the stored release
        // cannot be read, or the stored release is of a release line beyond those read
        if (result instanceof Selected s && !(builtForCli && isOlder(s.version(), current))) {
            selected = s.version();
            compat = s.compat();
        } else if (result instanceof Older o && !builtForCli) {
            selected = o.version();
            compat = o.compat();
            older = true;
        } else if (builtForCli) {
            printer().println("%s %s is up to date (built for Camel %s)".formatted(plugin, current, range));
            return Outcome.KEPT;
        } else if (result instanceof NoMatch noMatch && noMatch.reason() == Reason.CLI_NEWER) {
            // a plugin may publish its release for a new Camel version some time after the Camel release; one built for
            // another Camel version gains nothing, so the stored one is kept, and the load-time check keeps reminding
            printer().printWarn(("No release of %s is built for Camel CLI %s yet (the newest, %s, is %s). Keeping %s %s:"
                                 + " run 'camel plugin update %s' again once a release built for this Camel version is"
                                 + " published.")
                    .formatted(plugin, cli, noMatch.newestVersion(), noMatch.newest().describe(), plugin, current,
                            plugin));
            return Outcome.KEPT;
        } else {
            printer().printWarn(PluginAdd.noMatchWarning(plugin, cli, (NoMatch) result, keeping, true));
            return Outcome.KEPT;
        }

        String line = PluginCompatibility.minorLine(cli);
        if (selected.equals(current)) {
            if (older && line != null && !line.equals(entry.getString("selectedForCamel"))) {
                printer().printWarn(("No release of %s is built for Camel CLI %s. Keeping %s %s, the newest release for"
                                     + " an older Camel version (%s).")
                        .formatted(plugin, cli, plugin, current, compat.describe()));
                // warned here, so the load-time check stays quiet until the CLI moves to another Camel version
                entry.put("selectedForCamel", line);
                return Outcome.CHANGED;
            }
            printer().println("%s %s is up to date (%s%s)".formatted(plugin, current,
                    older ? "the newest release for an older Camel version, " : "", compat.describe()));
            return Outcome.KEPT;
        }

        String selectedGav = "%s:%s:%s".formatted(gav.getGroupId(), gav.getArtifactId(), selected);
        if (!dryRun) {
            String command = entry.getStringOrDefault("command", plugin);
            String repos = entry.getString("repos");
            // the release is downloaded first, so the entry keeps a release that can be used
            if (!loads(selectedGav, () -> downloadPlugin(command, selectedGav, repos))) {
                printer().printWarn("Keeping %s %s.".formatted(plugin, current));
                return Outcome.FAILED;
            }
        }
        printer().println("%s %s -> %s (%s)".formatted(plugin, current, selected, compat.describe()));
        if (older) {
            printer().printWarn(
                    "No release of %s is built for Camel CLI %s: %s is the newest release for an older Camel version."
                            .formatted(plugin, cli, selected));
        }
        entry.put("dependency", selectedGav);
        entry.put("camelVersions", compat.range());
        entry.put("camelVersionsSource", compat.source().label());
        if (older && line != null) {
            entry.put("selectedForCamel", line);
        } else {
            entry.remove("selectedForCamel");
        }
        // the class path of the release before
        entry.remove("resolved");
        return Outcome.CHANGED;
    }

    private Result resolve(MavenGav gav, String repos, String cli) throws Exception {
        DependencyDownloader downloader = newDownloader(repos);
        try {
            return new PluginVersionResolver(downloader).resolve(gav.getGroupId(), gav.getArtifactId(), cli);
        } finally {
            downloader.stop();
            downloader.close();
        }
    }

    private static boolean isOlder(String version, String than) {
        try {
            Version v = VERSION_SCHEME.parseVersion(version);
            return v.compareTo(VERSION_SCHEME.parseVersion(than)) < 0;
        } catch (InvalidVersionSpecificationException e) {
            return false;
        }
    }

    private static MavenGav parseGav(String dependency) {
        try {
            return MavenGav.parseGav(dependency);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The camel plugin add command that selects a release for the plugin, as it was added.
     */
    private static String addCommand(String plugin, JsonObject entry, MavenGav gav) {
        StringBuilder sb = new StringBuilder("camel plugin add ").append(plugin);
        boolean known = PluginType.findByName(plugin).isPresent() || PluginHelper.findKnownPlugin(plugin).isPresent();
        if (!known && gav != null) {
            sb.append(" --groupId ").append(gav.getGroupId()).append(" --artifactId ").append(gav.getArtifactId());
            String command = entry.getString("command");
            if (command != null && !command.equals(plugin)) {
                sb.append(" --command ").append(command);
            }
            String repos = entry.getString("repos");
            if (repos != null && !repos.isBlank()) {
                sb.append(" --repo ").append(repos);
            }
        }
        return sb.toString();
    }

    /**
     * Downloads the plugin, as when it is used.
     */
    Optional<Plugin> downloadPlugin(String command, String dependency, String repos) {
        return PluginHelper.getPlugin(command, cliVersion(), dependency, repos, printer());
    }
}
