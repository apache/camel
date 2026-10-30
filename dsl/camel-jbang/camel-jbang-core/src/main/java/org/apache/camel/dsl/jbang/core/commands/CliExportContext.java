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

import java.util.Optional;

import org.apache.camel.dsl.jbang.core.commands.ai.YamlLoadFailureReport;
import org.apache.camel.dsl.jbang.core.common.Plugin;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.export.ExportContext;
import org.apache.camel.dsl.jbang.export.common.Printer;

/**
 * The {@link ExportContext} of the CLI: the exporters and run customizers are those of the active plugins, a YAML route
 * file that did not load is reported as camel run does, and a confirmation is asked on the terminal. The dependencies
 * are resolved by the default resolver of the export.
 */
public final class CliExportContext {

    private CliExportContext() {
    }

    /**
     * @param main    the CLI
     * @param printer where the export prints
     * @param yes     whether to answer yes to the confirmations
     */
    public static ExportContext create(CamelJBangMain main, Printer printer, boolean yes) {
        return new ExportContext()
                .withPrinter(printer)
                .withPluginExporters(repos -> PluginHelper.getActivePlugins(main, repos).values().stream()
                        .map(Plugin::getExporter)
                        .flatMap(Optional::stream)
                        .toList())
                .withPluginRunCustomizers(repos -> PluginHelper.getActivePlugins(main, repos).values().stream()
                        .map(Plugin::getRunCustomizer)
                        .flatMap(Optional::stream)
                        .toList())
                .withStartFailureListener(YamlLoadFailureReport::logIfYamlLoadFailure)
                .withConfirm(message -> CommandHelper.confirmOperation(message, yes));
    }
}
