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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.impl.engine.DefaultFactoryFinder;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;

/**
 * Helper for command line plugins that add sub-commands and functionality when enabled by the user. Loads and saves
 * plugin configuration from/to the user home directory.
 */
public final class PluginHelper {

    public static final String PLUGIN_CONFIG = ".camel-jbang-plugins.json";

    private static final FactoryFinder FACTORY_FINDER
            = new DefaultFactoryFinder(new DefaultClassResolver(), FactoryFinder.DEFAULT_PATH + "camel-jbang-plugin/");

    private PluginHelper() {
        // prevent instantiation of utility class
    }

    /**
     * Loads the plugin Json configuration from the user home and goes through all configured plugins adding the plugin
     * commands to the current command line. Tries to resolve each plugin from the classpath with the factory finder
     * pattern. If present the plugin is called to customize the command line to add all sub-commands of the plugin.
     *
     * @param commandLine the command line to add commands to
     * @param main        the current Camel JBang main
     */
    public static void addPlugins(CommandLine commandLine, CamelJBangMain main) {
        JsonObject config = getPluginConfig();

        if (config != null) {
            JsonObject plugins = config.getMap("plugins");
            for (String pluginKey : plugins.keySet()) {
                JsonObject properties = plugins.getMap(pluginKey);

                String name = properties.getOrDefault("name", pluginKey).toString();
                String command = properties.getOrDefault("command", name).toString();

                Optional<Plugin> plugin = FACTORY_FINDER.newInstance("camel-jbang-plugin-" + command, Plugin.class);
                if (plugin.isPresent()) {
                    plugin.get().customize(commandLine, main);
                } else {
                    String description = properties.getOrDefault("description", "").toString();
                    String dependency = properties.getOrDefault("dependency",
                            "org.apache.camel:camel-jbang-plugin-%s:${camel-version}".formatted(command)).toString();
                    createSubCommand(commandLine, name, command, dependency, description, main);
                }
            }
        }
    }

    /**
     * Create sub-command as a placeholder for calling a plugin. When the command gets executed the plugin is added to
     * the classpath and a new JBang process is spawned with the same arguments. The factory finder mechanism will be
     * able to resolve the actual plugin from the classpath so the real plugin command is run.
     *
     * @param commandLine to receive the new command
     * @param name        the plugin name
     * @param command     the plugin command
     * @param dependency  the Maven dependency for the plugin
     * @param description optional description of the plugin command
     * @param main        current Camel JBang main
     */
    private static void createSubCommand(
            CommandLine commandLine, String name, String command,
            String dependency, String description, CamelJBangMain main) {
        commandLine.addSubcommand(command, CommandLine.Model.CommandSpec.wrapWithoutInspection(
                (Runnable) () -> {
                    List<String> args = commandLine.getParseResult().originalArgs();
                    if (args.contains("--help") || args.contains("--h")) {
                        main.getOut().printf("Loading plugin %s for command %s%n", name, command);
                    }

                    String gav = dependency;
                    if (gav.endsWith(":${camel-version}")) {
                        gav = gav.substring(0, gav.length() - "${camel-version}".length()) + getCamelVersion(args);
                    }

                    // need to use jbang command to call plugin
                    List<String> jbangArgs = new ArrayList<>();
                    jbangArgs.add("jbang");
                    // Add plugin dependency, so it is present on the classpath for the new JBang process
                    jbangArgs.add("--deps=" + gav);

                    jbangArgs.add("camel");
                    jbangArgs.addAll(args);

                    try {
                        ProcessBuilder pb = new ProcessBuilder();
                        pb.command(jbangArgs);

                        pb.inheritIO(); // run in foreground (with IO so logs are visible)
                        Process p = pb.start();

                        // wait for that process to exit as we run in foreground
                        int exitCode = p.waitFor();
                        main.quit(exitCode);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        main.getOut().printf("Interrupted while spawning JBang process");
                        main.quit(1);
                    } catch (IOException e) {
                        main.getOut().printf("Unable to spawn JBang process - %s%n", e.getMessage());
                        main.quit(1);
                    }
                })
                .usageMessage(new CommandLine.Model.UsageMessageSpec().description(description))
                .addUnmatchedArgsBinding(CommandLine.Model.UnmatchedArgsBinding
                        .forStringArrayConsumer(new CommandLine.Model.ISetter() {
                            @Override
                            public <T> T set(T value) throws Exception {
                                return value;
                            }
                        })));
    }

    private static String getCamelVersion(List<String> args) {
        Optional<String> version = args.stream()
                .filter(arg -> arg.startsWith("--camel.version="))
                .map(arg -> arg.substring("camel.version=".length()))
                .findFirst();

        if (version.isPresent()) {
            return version.get();
        }

        CamelCatalog catalog = new DefaultCamelCatalog();
        return catalog.getCatalogVersion();
    }

    public static JsonObject getOrCreatePluginConfig() {
        return Optional.ofNullable(getPluginConfig()).orElseGet(PluginHelper::createPluginConfig);
    }

    private static JsonObject getPluginConfig() {
        try {
            File f = new File(CommandLineHelper.getHomeDir(), PLUGIN_CONFIG);
            if (f.exists()) {
                try (FileInputStream fis = new FileInputStream(f)) {
                    String text = IOHelper.loadText(fis);
                    return (JsonObject) Jsoner.deserialize(text);
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return null;
    }

    public static JsonObject createPluginConfig() {
        File f = new File(CommandLineHelper.getHomeDir(), PLUGIN_CONFIG);
        JsonObject config = Jsoner.deserialize("{ \"plugins\": {} }", new JsonObject());
        try {
            Files.writeString(f.toPath(), config.toJson(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeCamelException("Failed to create plugin configuration", e);
        }

        return config;
    }

    public static void savePluginConfig(JsonObject plugins) {
        File f = new File(CommandLineHelper.getHomeDir(), PLUGIN_CONFIG);
        try {
            Files.writeString(f.toPath(), plugins.toJson(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeCamelException("Failed to save plugin configuration", e);
        }
    }

    public static void enable(PluginType pluginType) {
        JsonObject pluginConfig = PluginHelper.getOrCreatePluginConfig();
        JsonObject plugins = pluginConfig.getMap("plugins");

        JsonObject kubePlugin = new JsonObject();
        kubePlugin.put("name", pluginType.getName());
        kubePlugin.put("command", pluginType.getCommand());
        kubePlugin.put("description", pluginType.getDescription());
        plugins.put(pluginType.getName(), kubePlugin);

        PluginHelper.savePluginConfig(pluginConfig);
    }
}
