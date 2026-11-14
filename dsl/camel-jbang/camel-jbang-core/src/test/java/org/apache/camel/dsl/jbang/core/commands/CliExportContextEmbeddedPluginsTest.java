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
package org.apache.camel.dsl.jbang.core.commands;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.apache.camel.dsl.jbang.core.common.CommandTree;
import org.apache.camel.dsl.jbang.core.common.Plugin;
import org.apache.camel.dsl.jbang.run.common.PluginExporter;
import org.apache.camel.dsl.jbang.run.common.Printer;
import org.apache.camel.dsl.jbang.run.export.ExportContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The plugins built into the CLI take part in camel export: the Camel launcher embeds the test plugin without a plugin
 * configuration, so an export from the launcher had no Citrus tests (CAMEL-26933).
 */
class CliExportContextEmbeddedPluginsTest extends CamelCommandBaseTestSupport {

    @Test
    void theExportHasTheExporterOfAnEmbeddedPlugin() throws Exception {
        useTestHomeDir();
        ExporterPlugin plugin = new ExporterPlugin();
        CamelJBangMain main = new CamelJBangMain() {
            @Override
            public Collection<Plugin> getEmbeddedPlugins(String target) {
                return "export".equals(target) ? List.of(plugin) : List.of();
            }
        }.withPrinter(printer);

        ExportContext.ActivePlugins plugins = CliExportContext.create(main, printer, true).getPlugins(null);

        assertThat(plugins.exporters()).containsExactly(plugin.exporter);
    }

    @Test
    void withoutEmbeddedPluginsTheExportHasThePluginsOfTheConfiguration() throws Exception {
        useTestHomeDir();
        CamelJBangMain main = new CamelJBangMain().withPrinter(printer);

        assertThat(CliExportContext.create(main, printer, true).getPlugins(null).exporters()).isEmpty();
    }

    private static class ExporterPlugin implements Plugin {

        final PluginExporter exporter = (Path buildDir, String packageName, Printer printer) -> {
        };

        @Override
        public void customize(CommandTree commandLine, CamelJBangMain main) {
        }

        @Override
        public Optional<PluginExporter> getExporter() {
            return Optional.of(exporter);
        }
    }
}
