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
package org.apache.camel.dsl.jbang.launcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.generate.GeneratePlugin;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.KubernetesPlugin;
import org.apache.camel.dsl.jbang.core.commands.test.TestPlugin;
import org.apache.camel.dsl.jbang.core.commands.tui.TuiPlugin;
import org.apache.camel.dsl.jbang.core.commands.validate.ValidatePlugin;
import org.apache.camel.dsl.jbang.core.common.CommandTree;
import org.apache.camel.dsl.jbang.core.common.Plugin;
import org.apache.camel.dsl.jbang.core.common.PluginType;
import org.apache.camel.dsl.jbang.launcher.selfupdate.SelfUpdatePlugin;
import org.apache.camel.dsl.jbang.launcher.selfupdate.UpdateChecker;

/**
 * Main for Camel Launcher
 */
public class CamelLauncherMain extends CamelJBangMain {

    // the plugins embedded in the launcher, with the commands they add
    private record EmbeddedPlugin(Supplier<Plugin> factory, Set<String> commands) {
    }

    private static final List<EmbeddedPlugin> PLUGINS = List.of(
            new EmbeddedPlugin(GeneratePlugin::new, Set.of("generate")),
            new EmbeddedPlugin(KubernetesPlugin::new, Set.of("kubernetes", "k8s")),
            new EmbeddedPlugin(SelfUpdatePlugin::new, Set.of("self-update")),
            new EmbeddedPlugin(TuiPlugin::new, Set.of("tui")),
            new EmbeddedPlugin(ValidatePlugin::new, Set.of("validate")),
            new EmbeddedPlugin(TestPlugin::new, Set.of("test")));

    @Override
    public void preExecute(CommandTree commandLine, String[] args) {
        UpdateChecker.maybeNotify(args);
    }

    @Override
    public void postExecute(CommandTree commandLine, String[] args, int exitCode) {
        // the check for a newer release that the command started, which most commands end before
        UpdateChecker.awaitRefresh();
    }

    @Override
    protected boolean isPluginCommand(String command) {
        return PLUGINS.stream().anyMatch(p -> p.commands().contains(command)) || super.isPluginCommand(command);
    }

    @Override
    public Collection<Plugin> getEmbeddedPlugins(String target) {
        // such as the test plugin for camel export: the launcher does not register its plugins in the plugin
        // configuration, so the export did not have the Citrus tests (CAMEL-26933)
        List<Plugin> answer = new ArrayList<>();
        for (EmbeddedPlugin embedded : PLUGINS) {
            boolean contributes = embedded.commands().stream()
                    .map(PluginType::findByName)
                    .flatMap(Optional::stream)
                    .anyMatch(type -> Arrays.asList(type.getAppliesTo()).contains(target));
            if (contributes) {
                Plugin plugin = embedded.factory().get();
                plugin.setClassLoader(getClass().getClassLoader());
                answer.add(plugin);
            }
        }
        return answer;
    }

    @Override
    public void postAddCommands(CommandTree commandLine, String[] args) {
        if (hasOnlyCommand(commandLine, args)) {
            // a command of the CLI, which needs none of the commands of the plugins
            return;
        }
        // a command of a plugin needs only that plugin
        String only = onlyPluginCommand(commandLine, args);
        // Install embedded plugins. Each plugin must be given the classloader that loaded it (this
        // class's classloader, i.e. the fat-jar classloader) so that plugins relying on ServiceLoader
        // discovery can see the dependencies bundled in the launcher. The TUI plugin installs this
        // classloader as the thread-context classloader so tamboui can discover its terminal backend;
        // without it the discovery falls back to the system classloader, which cannot see the nested
        // BOOT-INF/lib jars of the Spring Boot fat-jar, and the TUI fails with "No BackendProvider found".
        ClassLoader classLoader = getClass().getClassLoader();
        for (EmbeddedPlugin embedded : PLUGINS) {
            if (only == null || embedded.commands().contains(only)) {
                Plugin plugin = embedded.factory().get();
                plugin.setClassLoader(classLoader);
                plugin.customize(commandLine, this);
            }
        }
    }

}
