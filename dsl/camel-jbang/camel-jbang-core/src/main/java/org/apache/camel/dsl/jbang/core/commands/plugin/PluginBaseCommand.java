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

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

import org.aesh.command.option.Option;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.Plugin;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.main.download.DependencyDownloader;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.util.json.JsonObject;

/**
 * Base command for plugin subcommands. Provides the {@code --plugin-config} option that overrides the default plugin
 * configuration file location.
 */
abstract class PluginBaseCommand extends CamelCommand {

    @Option(name = "plugin-config",
            description = "Path to a custom plugin configuration file or directory. "
                          + "Only applies to the plugin subcommands; "
                          + "for plugin commands themselves (e.g. 'camel kubernetes'), "
                          + "set CAMEL_CLI_PLUGIN_CONFIG instead.")
    String pluginConfig;

    private Path pluginConfigPath() {
        if (pluginConfig != null) {
            try {
                return Path.of(pluginConfig);
            } catch (InvalidPathException e) {
                throw new IllegalStateException(
                        "Invalid path in --plugin-config option: " + pluginConfig, e);
            }
        }
        return null;
    }

    JsonObject loadConfig() {
        try {
            Path path = pluginConfigPath();
            if (path != null) {
                return PluginHelper.getOrCreatePluginConfig(path);
            }
            return PluginHelper.getOrCreatePluginConfig();
        } catch (RuntimeCamelException e) {
            throw CliException.failure(e.getMessage() + ". Fix the JSON by hand or delete the file.");
        }
    }

    /**
     * The plugin configuration, or null when there is none (it is not created).
     */
    JsonObject loadExistingConfig() {
        try {
            return PluginHelper.getPluginConfig(pluginConfigPath());
        } catch (RuntimeCamelException e) {
            throw CliException.failure(e.getMessage() + ". Fix the JSON by hand or delete the file.");
        }
    }

    void saveConfig(JsonObject plugins) {
        Path path = pluginConfigPath();
        if (path != null) {
            PluginHelper.savePluginConfig(plugins, path);
        } else {
            PluginHelper.savePluginConfig(plugins);
        }
    }

    /**
     * The version of this Camel CLI.
     */
    String cliVersion() {
        return new DefaultCamelCatalog().getCatalogVersion();
    }

    /**
     * A started downloader to read the releases and POMs of a plugin with.
     *
     * @param repos the additional Maven repositories of the plugin, or null
     */
    DependencyDownloader newDownloader(String repos) {
        DependencyDownloader downloader = new MavenDependencyDownloader();
        if (repos != null && !repos.isBlank()) {
            downloader.setRepositories(repos);
        }
        downloader.setPreferLocal(true);
        downloader.start();
        return downloader;
    }

    /**
     * Downloads a release of a plugin, as when it is used, so a release that cannot be used never gets into the
     * configuration.
     *
     * @param  gav      the release
     * @param  download downloads and loads the plugin
     * @return          false, with the reason printed, when the release cannot be downloaded or loaded
     */
    boolean loads(String gav, Supplier<Optional<Plugin>> download) {
        Optional<Plugin> resolved;
        try {
            resolved = download.get();
        } catch (RuntimeException e) {
            printer().printErr("Cannot download " + gav, e);
            return false;
        } catch (LinkageError e) {
            // built for another Camel version (CAMEL-26964)
            printer().printErr("Cannot load " + gav + ": " + PluginHelper.notLinked(cliVersion(), e));
            return false;
        }
        if (resolved.isEmpty()) {
            printer().printErr("Cannot download " + gav);
            return false;
        }
        return true;
    }
}
