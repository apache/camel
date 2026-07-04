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

import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.generate.GeneratePlugin;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.KubernetesPlugin;
import org.apache.camel.dsl.jbang.core.commands.test.TestPlugin;
import org.apache.camel.dsl.jbang.core.commands.tui.TuiPlugin;
import org.apache.camel.dsl.jbang.core.commands.validate.ValidatePlugin;
import org.apache.camel.dsl.jbang.core.common.Plugin;
import picocli.CommandLine;

/**
 * Main for Camel Launcher
 */
public class CamelLauncherMain extends CamelJBangMain {

    @Override
    public void postAddCommands(CommandLine commandLine, String[] args) {
        // Install embedded plugins. Each plugin must be given the classloader that loaded it (this
        // class's classloader, i.e. the fat-jar classloader) so that plugins relying on ServiceLoader
        // discovery can see the dependencies bundled in the launcher. The TUI plugin installs this
        // classloader as the thread-context classloader so tamboui can discover its terminal backend;
        // without it the discovery falls back to the system classloader, which cannot see the nested
        // BOOT-INF/lib jars of the Spring Boot fat-jar, and the TUI fails with "No BackendProvider found".
        ClassLoader classLoader = getClass().getClassLoader();
        List<Plugin> plugins = List.of(
                new GeneratePlugin(),
                new KubernetesPlugin(),
                new TuiPlugin(),
                new ValidatePlugin(),
                new TestPlugin());
        for (Plugin plugin : plugins) {
            plugin.setClassLoader(classLoader);
            plugin.customize(commandLine, this);
        }
    }

}
