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
package org.apache.camel.dsl.jbang.run;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.run.common.MavenResolver;
import org.apache.camel.dsl.jbang.run.common.Printer;
import org.apache.camel.dsl.jbang.run.common.QuarkusPlatform;
import org.apache.camel.dsl.jbang.run.export.DependencyResolver;
import org.apache.camel.dsl.jbang.run.export.ExportContext;
import org.apache.camel.dsl.jbang.run.export.ExportRequest;
import org.apache.camel.main.KameletMain;
import org.apache.camel.util.StringHelper;

/**
 * Resolves the dependencies of an export as camel run does for it: boots the integration silently in this JVM, with
 * stubs and without starting the routes, and writes the run settings, which the export reads. It uses the run settings
 * pipeline of this module, so it needs neither picocli nor camel-jbang-core.
 * <p>
 * The printer, the plugins (their run customizers and exporters) and what to report when the integration fails to start
 * come from the {@link ExportContext}. Logging is not configured: the export logs as its caller has configured.
 *
 * @since 4.24
 */
public class SilentRunDependencyResolver implements DependencyResolver {

    // the profile of the silent run when the export has none, as the one of camel run
    private static final String DEFAULT_PROFILE = "dev";

    @Override
    public int resolve(ExportRequest request) throws Exception {
        return resolve(request, new ExportContext());
    }

    @Override
    public int resolve(ExportRequest export, ExportContext context) throws Exception {
        RunRequest request = toRunRequest(export);
        Printer printer = context.getPrinter();

        // special if user type: camel export . or camel export dirName
        Path dir = RunPipeline.detectDirectory(request);
        if (dir != null) {
            request.baseDir = dir;
        }

        RunPipeline.prepareWorkDir(request.workDir, printer);

        Logging logging = new Logging();
        Properties profileProperties = RunProfileProperties.load(request, logging::apply);
        writeLoggingSettings(export.logging, logging, profileProperties, new RunSettings(request.workDir));

        if (request.openapi != null) {
            RunPipeline.generateOpenApi(request.openapi, request.workDir, request.files);
        }
        if (request.files.isEmpty()) {
            RunPipeline.detectFiles(request.files, request.sourceDir, profileProperties, true);
        }
        // filter out duplicate files
        if (!request.files.isEmpty()) {
            request.files = request.files.stream().distinct().collect(Collectors.toList());
        }

        RunPipeline pipeline = new RunPipeline(request)
                .withPrinter(printer)
                .withPlugins(JBangRunner.plugins(context, request));
        KameletMain main = pipeline.createMain();
        int exit = pipeline.configure(main, profileProperties);
        if (exit != 0) {
            return exit;
        }
        // the run only finds the dependencies: the security policy of the profile (such as prod, which fails on an
        // insecure option or a secret in plain text) is for running the exported project, not for exporting it
        main.addOverrideProperty("camel.security.policy", "warn");

        // such as the report of the validator for a YAML route file that did not load (CAMEL-24851)
        final List<String> runFiles = new ArrayList<>(request.files);
        main.setStartFailureListener(ex -> context.onStartFailure(ex, runFiles));

        try {
            main.start();
            main.run();
            int code = main.getExitCode();
            // no exit code is set when Camel is stopped from outside
            return code == Integer.MIN_VALUE ? 0 : code;
        } catch (Exception e) {
            if (request.ignoreLoadingError) {
                printer.printErr(e);
                return 0;
            }
            throw e;
        }
    }

    /**
     * The request of the silent run of the export: the run of the export request, so it shares the lists of the files
     * and the dependencies of the export, which the run completes.
     */
    static RunRequest toRunRequest(ExportRequest export) {
        RunRequest request = new RunRequest(export);
        request.exportRun = true;
        // the same profile as the export, so the run boots with the properties the exported project has
        request.profile = export.profile != null ? export.profile : DEFAULT_PROFILE;
        if (request.baseDir == null) {
            // the run loads application.properties (and the one of the profile) from its base directory
            request.baseDir = Path.of(".");
        }
        // the GAV is of the exported project, the run that finds its dependencies has none
        request.gav = null;
        // the run applies the application properties to its own Maven resolver and Quarkus platform
        request.mavenResolver = MavenResolver.of(new Properties(), export.mavenResolver);
        request.quarkusPlatform = QuarkusPlatform.of(new Properties(), export.quarkusPlatform);
        return request;
    }

    /**
     * Writes the logging settings of the run, as camel run does for an export, without configuring the logging.
     */
    private static void writeLoggingSettings(
            boolean enabled, Logging logging, Properties profileProperties, RunSettings settings) {
        if (enabled) {
            // the root logging level may be configured in application.properties
            if (profileProperties != null) {
                for (Object obj : profileProperties.keySet()) {
                    String key = obj.toString();
                    String value = profileProperties.getProperty(key);
                    if (key.startsWith("logging.level.")) {
                        key = key.substring(14);
                    } else if ("quarkus.log.level".equals(key)) {
                        key = "root";
                    } else if (key.startsWith("quarkus.log.category.")) {
                        key = key.substring(21);
                        if (key.endsWith(".level")) {
                            key = key.substring(0, key.length() - 6);
                        }
                    } else {
                        continue;
                    }
                    key = StringHelper.removeLeadingAndEndingQuotes(key);
                    if ("root".equalsIgnoreCase(key)) {
                        logging.level = value;
                    }
                }
            }
            write(settings, "loggingLevel", logging.level);
            write(settings, "loggingColor", logging.color ? "true" : "false");
            write(settings, "loggingJson", logging.json ? "true" : "false");
        } else {
            write(settings, "loggingLevel", logging.level);
        }
    }

    private static void write(RunSettings settings, String key, String value) {
        if (value != null) {
            settings.write(key, value);
        }
    }

    /**
     * The logging options of the run, as the application properties configure them (no terminal, so no color unless
     * configured).
     */
    private static final class Logging {
        String level;
        boolean color;
        boolean json;

        void apply(Properties properties) {
            level = properties.getProperty("loggingLevel", level);
            color = "true".equals(properties.getProperty("loggingColor", color ? "true" : "false"));
            json = "true".equals(properties.getProperty("loggingJson", json ? "true" : "false"));
        }
    }
}
