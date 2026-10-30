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
package org.apache.camel.dsl.jbang.export.run;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.export.ExportContext;
import org.apache.camel.dsl.jbang.export.common.PluginRunCustomizer;
import org.apache.camel.dsl.jbang.export.common.Printer;
import org.apache.camel.main.BaseMainSupport;
import org.apache.camel.main.KameletMain;
import org.apache.camel.main.MainListenerSupport;

/**
 * Runs with the jbang runtime: the files run in this JVM, in the Camel Main of the run ({@link KameletMain}), which
 * {@link RunPipeline} configures. What only the caller does (camel run configures its logging, and loads --code and the
 * clipboard) comes in through the {@link RunContext}.
 */
final class JBangRunner {

    private final ProjectRunner runner;
    private final RunRequest request;

    JBangRunner(ProjectRunner runner, RunRequest request) {
        this.runner = runner;
        this.request = request;
    }

    RunRequest getRequest() {
        return request;
    }

    /**
     * Prepares and configures the run: the work directory, the application properties (the caller is told, such as to
     * configure its logging), the OpenAPI spec, the files to run, and the Camel Main of the run with the run settings.
     */
    ConfiguredRun configure() throws Exception {
        RunContext context = runner.getContext();

        RunPipeline.prepareWorkDir(request.workDir, context.getPrinter());
        Properties properties;
        if (request.transformMessageRun) {
            // do not load profile in transform message run as it should be vanilla empty
            properties = null;
        } else {
            // also an empty run: it has no files, but the application properties configure it (and the logging)
            properties = RunProfileProperties.load(request, p -> {
            });
        }
        Properties profileProperties = !request.empty ? properties : null;
        context.onPrepared(request, properties, RunPipeline.isMcpStdioEnabled(request.mcpStdio, profileProperties));

        // the printer of the caller once it knows how the run logs (such as quiet with MCP over stdio)
        Printer printer = context.getPrinter();
        if (request.openapi != null) {
            RunPipeline.generateOpenApi(request.openapi, request.workDir, request.files);
        }
        boolean autoDetectFiles = request.files.isEmpty() || RunPipeline.RUN_JAVA_SH.equals(request.files.get(0));
        // if no specific file to run then try to auto-detect
        if (!request.empty && autoDetectFiles
                && !RunPipeline.detectFiles(request.files, request.sourceDir, profileProperties, false)) {
            printer.printDiagnostic(RunPipeline.noFilesMessage(request));
            return new ConfiguredRun(this, null, 1);
        }
        // filter out duplicate files
        if (!request.files.isEmpty()) {
            request.files = request.files.stream().distinct().collect(Collectors.toList());
        }

        RunPipeline pipeline = new RunPipeline(request)
                .withPrinter(printer)
                .withInitialProperties(context.getInitialProperties())
                .withClipboard(context.getClipboard())
                .withDataDirWithoutSourceDir(context.getDataDirWithoutSourceDir());
        if (!request.skipPlugins) {
            pipeline.withPlugins(plugins(context, request));
        }
        KameletMain main = pipeline.createMain();
        int exit = pipeline.configure(main, profileProperties);
        return new ConfiguredRun(this, exit == 0 ? main : null, exit);
    }

    /**
     * Starts Camel, which {@link #configure()} configured.
     *
     * @return the running integration, or a run that ended (no routes, or a route that did not load with
     *         {@link RunRequest#ignoreLoadingError})
     */
    RunningIntegration start(KameletMain main) throws Exception {
        RunContext context = runner.getContext();
        // a route file that did not load, such as the report of the validator for a YAML route file, on a failed
        // start and on a failed reload in dev mode (CAMEL-24851)
        final List<String> runFiles = new ArrayList<>(request.files);
        main.setStartFailureListener(ex -> context.onStartFailure(ex, runFiles));
        main.addMainListener(new MainListenerSupport() {
            @Override
            public void afterConfigure(BaseMainSupport main) {
                main.getCamelContext().getManagementStrategy().addEventNotifier(new ReloadFailureNotifier(context));
            }
        });
        return new KameletMainIntegration(main, request, context).start();
    }

    /**
     * The active plugins of the context, found once: they customize the run before their exporters add their
     * dependencies.
     */
    static RunPipeline.Plugins plugins(ExportContext context, RunRequest request) {
        return (main, files) -> {
            ExportContext.ActivePlugins plugins = context.getPlugins(request.mavenResolver.repos());
            for (PluginRunCustomizer customizer : plugins.runCustomizers()) {
                customizer.beforeRun(main, files);
            }
            return new LinkedHashSet<>(plugins.exporters());
        };
    }
}
