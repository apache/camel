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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.dsl.jbang.export.common.MavenResolver;
import org.apache.camel.dsl.jbang.export.common.QuarkusPlatform;
import org.apache.camel.dsl.jbang.export.common.RuntimeType;

/**
 * What to run: the Camel files and the options of camel run, as {@link RunPipeline} reads them to configure the
 * {@link org.apache.camel.main.KameletMain} and to write the run settings.
 * <p>
 * The fields are public and mutable, as the pipeline completes some of them, the same way as camel run does: the name
 * (from the first route file or the source directory), the files (the generated OpenAPI file), the dependencies, the
 * stub pattern, the properties files and the ports.
 *
 * @since 4.24
 */
public class RunRequest {

    /**
     * The work directory, where the run settings file and the generated files are written.
     */
    public Path workDir;

    /**
     * The base directory of the files, where application.properties and the one of the profile are found.
     */
    public Path baseDir;

    /**
     * The files to run.
     */
    public List<String> files = new ArrayList<>();

    /**
     * The name of the integration; CamelJBang means none was given, and the name is derived from the files.
     */
    public String name = "CamelJBang";

    /**
     * The runtime (the runtime specific dependencies of the properties files are added for it).
     */
    public RuntimeType runtime = RuntimeType.jbang;

    /**
     * The profile (the application properties of the profile are loaded as well).
     */
    public String profile;

    /**
     * Run an empty Camel without loading source files.
     */
    public boolean empty;

    /**
     * Additional dependencies; the pipeline adds the ones it finds.
     */
    public List<String> dependencies = new ArrayList<>();

    /**
     * The properties files to use (comma separated); the pipeline adds the ones among the files.
     */
    public String propertiesFiles;

    /**
     * The profile properties files that were loaded, whose dependencies are not added a second time.
     */
    public Set<Path> profilePropertiesFiles = new HashSet<>();

    /**
     * Additional properties (key=value) from the command line.
     */
    public String[] property;

    /**
     * Exclude files by name or pattern.
     */
    public List<String> excludes = new ArrayList<>();

    /**
     * Directories with resources to add to the classpath, keeping their structure.
     */
    public List<String> resourceDirs = new ArrayList<>();

    /**
     * Source directory for dynamically loading Camel file(s) to run.
     */
    public String sourceDir;

    /**
     * Directories (comma separated) under the source directory that the routes read and write.
     */
    public String dataDir;

    /**
     * Directories (comma separated) with local Kamelets.
     */
    public String localKameletDir;

    /**
     * Enables dev mode (live reload when source files are updated and saved).
     */
    public boolean dev;

    /**
     * Stubs all the matching endpoint uri with the given component name or pattern.
     */
    public String stub;

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
     * Whether to ignore route loading and compilation errors.
     */
    public boolean ignoreLoadingError;

    /**
     * Whether to use lazy bean initialization.
     */
    public boolean lazyBean;

    /**
     * Whether to prompt for the property placeholders that are missing.
     */
    public boolean prompt;

    /**
     * The Maven group:artifact:version.
     */
    public String gav;

    /**
     * The OpenAPI spec to generate a Rest DSL route from.
     */
    public String openapi;

    /**
     * Whether to be verbose.
     */
    public boolean verbose;

    /**
     * The Maven resolver (download, repositories, fresh).
     */
    public MavenResolver mavenResolver = new MavenResolver();

    /**
     * Optional location of the Maven settings.xml file.
     */
    public String mavenSettings;

    /**
     * Optional location of the Maven settings-security.xml file.
     */
    public String mavenSettingsSecurity;

    /**
     * Whether downloading JARs from Maven Central repository is enabled.
     */
    public boolean mavenCentralEnabled = true;

    /**
     * Whether downloading JARs from ASF Maven Snapshot repository is enabled.
     */
    public boolean mavenApacheSnapshotEnabled = true;

    /**
     * Whether to automatically package scan JARs for custom Spring or Quarkus beans.
     */
    public boolean packageScanJars;

    /**
     * The Camel version (to resolve the Quarkus platform for).
     */
    public String camelVersion;

    /**
     * The Spring Boot version.
     */
    public String springBootVersion;

    /**
     * The Quarkus platform (null when not in use).
     */
    public QuarkusPlatform quarkusPlatform;

    /**
     * The Apache Camel Kamelets version.
     */
    public String kameletsVersion;

    /**
     * The Java version.
     */
    public String javaVersion;

    /**
     * The port of the embedded HTTP server (-1 when not given).
     */
    public int port = -1;

    /**
     * The port of the management server (-1 when not given).
     */
    public int managementPort = -1;

    /**
     * Whether to enable the developer console.
     */
    public boolean console;

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
     * Whether to enable the observability services.
     */
    public boolean observe;

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
}
