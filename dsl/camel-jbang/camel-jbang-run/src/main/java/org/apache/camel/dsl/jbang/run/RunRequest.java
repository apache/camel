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
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.camel.dsl.jbang.run.export.ExportRequest;

/**
 * What to run: the Camel files and the options of camel run, as {@link RunPipeline} reads them to configure the
 * {@link org.apache.camel.main.KameletMain} and to write the run settings.
 * <p>
 * A run is an export with a run on top: the files, the dependencies, the versions, the Maven resolver and the other
 * options that a run shares with an export are the fields of {@link ExportRequest}, and this request adds the options
 * that only a run has.
 * <p>
 * The fields are public and mutable, as the pipeline completes some of them, the same way as camel run does: the name
 * (from the first route file or the source directory), the files (the generated OpenAPI file), the dependencies, the
 * stub pattern, the properties files and the ports.
 *
 * @since 4.24
 */
public class RunRequest extends ExportRequest {

    /**
     * Run an empty Camel without loading source files.
     */
    public boolean empty;

    /**
     * The properties files to use (comma separated); the pipeline adds the ones among the files.
     */
    public String propertiesFiles;

    /**
     * The profile properties files that were loaded, whose dependencies are not added a second time.
     */
    public Set<Path> profilePropertiesFiles = new HashSet<>();

    /**
     * Source directory for dynamically loading Camel file(s) to run.
     */
    public String sourceDir;

    /**
     * Directories (comma separated) under the source directory that the routes read and write.
     */
    public String dataDir;

    /**
     * Enables dev mode (live reload when source files are updated and saved).
     */
    public boolean dev;

    /**
     * Stubs all the matching endpoint uri with the given component name or pattern.
     */
    public String stub;

    /**
     * Whether to add camel-chaos (camel run --chaos), which only a run gets, not an export.
     */
    public boolean chaos;

    /**
     * Enables tracing.
     */
    public boolean trace;

    /**
     * Enables backlog tracing.
     */
    public boolean backlogTrace;

    /**
     * Whether to support JBang style //DEPS to specify additional dependencies.
     */
    public boolean modeline = true;

    /**
     * Whether to prompt for the property placeholders that are missing.
     */
    public boolean prompt;

    /**
     * Whether to enable the MCP server for dev and diagnostics tools.
     */
    public boolean mcp;

    /**
     * Whether to serve the ai-tool routes over MCP stdio.
     */
    public boolean mcpStdio;

    /**
     * The MCP tags of the ai-tool routes to serve.
     */
    public String mcpTags;

    /**
     * Whether to enable the OpenAPI UI.
     */
    public boolean openapiUi;

    /**
     * Whether to enable the health check.
     */
    public boolean health;

    /**
     * Whether to enable the metrics.
     */
    public boolean metrics;

    /**
     * Max number of messages to process before stopping (0 for no limit).
     */
    public int maxMessages;

    /**
     * Max seconds to run before stopping (0 for no limit).
     */
    public int maxSeconds;

    /**
     * Max seconds to be idle before stopping (0 for no limit).
     */
    public int maxIdleSeconds;

    /**
     * Additional JVM arguments of the application (e.g. -Xmx256m -Xms128m), with the runtimes that run it in a JVM of
     * its own.
     */
    public String jvmArgs;

    /**
     * The port of the JVM remote debugging of the application (0 for none), with the runtimes that run it in a JVM of
     * its own.
     */
    public int jvmDebugPort;

    /**
     * Whether to enable Java Flight Recorder.
     */
    public boolean jfr;

    /**
     * The Java Flight Recorder profile.
     */
    public String jfrProfile;

    /**
     * Whether to run with the OpenTelemetry Java agent.
     */
    public boolean openTelemetryAgent;

    /**
     * Where the OpenTelemetry Java agent exports to (tui, otlp, jaeger or observability).
     */
    public String openTelemetryAgentExport = "tui";

    /**
     * The log file of a run in this JVM, which the caller logs to (camel run logs to ~/.camel/&lt;pid&gt;.log): it is
     * deleted when the run ends, or kept as the previous log of the integration (see {@link #keepPreviousLog}). Null
     * for none.
     */
    public Path logFile;

    /**
     * Whether the log file is kept as the previous log of the integration when the run ends, with the exit code and why
     * it failed, so camel log and the TUI show it after the run (camel run on the command line, not a Camel that
     * another command runs in its JVM).
     */
    public boolean keepPreviousLog;

    /**
     * The silent run of an export: it only resolves the dependencies.
     */
    public boolean exportRun;

    /**
     * A run to debug.
     */
    public boolean debugRun;

    /**
     * The silent run of a transformation of routes.
     */
    public boolean transformRun;

    /**
     * A run that transforms messages: no route is started.
     */
    public boolean transformMessageRun;

    /**
     * A run of a script: it stops once idle.
     */
    public boolean scriptRun;

    /**
     * A request with the defaults of camel run.
     */
    public RunRequest() {
        name = "CamelJBang";
        runtime = RuntimeType.jbang;
        lazyBean = false;
        // the Quarkus platform comes from the application properties when not given
        quarkusPlatform = null;
    }

    /**
     * The run of an export: a shallow copy of the export request, and the defaults of camel run for what only a run
     * has.
     */
    public RunRequest(ExportRequest export) {
        super(export);
    }

    /**
     * Whether the run records a flight recording ({@link #jfr} or {@link #jfrProfile}).
     */
    public boolean jfrEnabled() {
        return jfr || jfrProfile != null;
    }

    /**
     * The file of the flight recording: the name of the integration, else camel.
     */
    public String jfrFileName() {
        return (name != null ? name : "camel") + ".jfr";
    }
}
