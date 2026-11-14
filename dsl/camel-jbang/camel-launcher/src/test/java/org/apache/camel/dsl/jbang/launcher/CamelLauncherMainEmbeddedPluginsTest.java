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

import org.apache.camel.dsl.jbang.core.commands.test.TestPlugin;
import org.apache.camel.dsl.jbang.core.common.Plugin;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * camel export from the launcher has the Citrus tests: the embedded test plugin contributes its exporter (CAMEL-26933).
 */
class CamelLauncherMainEmbeddedPluginsTest {

    @Test
    void theTestPluginContributesToExport() {
        assertThat(new CamelLauncherMain().getEmbeddedPlugins("export"))
                .hasSize(1)
                .allSatisfy(plugin -> {
                    assertThat(plugin).isInstanceOf(TestPlugin.class);
                    assertThat(plugin.getExporter()).isPresent();
                });
    }

    @Test
    void noEmbeddedPluginContributesToRun() {
        assertThat(new CamelLauncherMain().getEmbeddedPlugins("run")).extracting(Plugin::getClass)
                .doesNotContain(TestPlugin.class.asSubclass(Plugin.class));
    }
}
