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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Stack;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.JavaVersionCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.LoggingLevelCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.ProfileCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.RuntimeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.RuntimeTypeConverter;
import org.apache.camel.dsl.jbang.export.ExportContext;
import org.apache.camel.dsl.jbang.export.ExportRequest;
import org.apache.camel.dsl.jbang.export.Exporter;
import org.apache.camel.dsl.jbang.export.ProjectExporter;
import org.apache.camel.dsl.jbang.export.common.HawtioVersion;
import org.apache.camel.dsl.jbang.export.common.MavenResolver;
import org.apache.camel.dsl.jbang.export.common.Printer;
import org.apache.camel.dsl.jbang.export.common.QuarkusPlatform;
import org.apache.camel.dsl.jbang.export.common.RuntimeType;
import org.apache.camel.dsl.jbang.export.common.RuntimeUtil;
import org.apache.camel.tooling.maven.MavenGav;
import picocli.CommandLine;
import picocli.CommandLine.Mixin;

import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.*;

public abstract class ExportBaseCommand extends CamelCommand {

    protected Path exportBaseDir;
    private Printer quietPrinter;

    @CommandLine.Parameters(description = "The Camel file(s) to export. If no files is specified then what was last run will be exported.",
                            arity = "0..9", paramLabel = "<files>", parameterConsumer = FilesConsumer.class)
    protected Path[] filePaths; // Defined only for file path completion; the field never used

    protected List<String> files = new ArrayList<>();

    @CommandLine.Option(names = { "--dep", "--dependency" }, description = "Add additional dependencies",
                        split = ",")
    protected List<String> dependencies = new ArrayList<>();

    @CommandLine.Option(names = { "--runtime" },
                        completionCandidates = RuntimeCompletionCandidates.class,
                        converter = RuntimeTypeConverter.class,
                        description = "Runtime (${COMPLETION-CANDIDATES})")
    protected RuntimeType runtime;

    @CommandLine.Option(names = { "--name" },
                        description = "The integration name. Use this when the name should not get derived otherwise.")
    protected String name;

    @CommandLine.Option(names = { "--port" },
                        description = "Embeds a local HTTP server on this port")
    int port = -1;

    @CommandLine.Option(names = { "--management-port" },
                        description = "To use a dedicated port for HTTP management")
    int managementPort = -1;

    @CommandLine.Option(names = { "--gav" }, description = "The Maven group:artifact:version")
    protected String gav;

    @CommandLine.Option(names = { "--parent-pom" }, description = "The Maven parent POM group:artifact:version")
    protected String parentPom;

    @CommandLine.Option(names = { "--exclude" }, description = "Exclude files by name or pattern")
    protected List<String> excludes = new ArrayList<>();

    @CommandLine.Option(names = { "--maven-settings" },
                        description = "Optional location of Maven settings.xml file to configure servers, repositories, mirrors and proxies."
                                      + " If set to \"false\", not even the default ~/.m2/settings.xml will be used.")
    protected String mavenSettings;

    @CommandLine.Option(names = { "--maven-settings-security" },
                        description = "Optional location of Maven settings-security.xml file to decrypt settings.xml")
    protected String mavenSettingsSecurity;

    @CommandLine.Option(names = { "--maven-central-enabled" }, defaultValue = "true", arity = "0..1", fallbackValue = "true",
                        description = "Whether downloading JARs from Maven Central repository is enabled")
    protected boolean mavenCentralEnabled = true;

    @CommandLine.Option(names = { "--maven-apache-snapshot-enabled" }, defaultValue = "true", arity = "0..1",
                        fallbackValue = "true",
                        description = "Whether downloading JARs from ASF Maven Snapshot repository is enabled")
    protected boolean mavenApacheSnapshotEnabled = true;

    @CommandLine.Option(names = { "--main-classname" },
                        description = "The class name of the Camel Main application class",
                        defaultValue = "CamelApplication")
    protected String mainClassname = "CamelApplication";

    @CommandLine.Option(names = {
            "--java-version",
            "--java" }, completionCandidates = JavaVersionCompletionCandidates.class,
                        description = "Java version (${COMPLETION-CANDIDATES})", defaultValue = "21")
    protected String javaVersion = "21";

    @CommandLine.Option(names = { "--camel-version" },
                        description = "To export using a different Camel version than the default version.")
    protected String camelVersion;

    @CommandLine.Option(names = {
            "--kamelets-version" }, description = "Apache Camel Kamelets version (auto-detected from classpath if not set)")
    protected String kameletsVersion;

    @CommandLine.Option(names = { "--profile" }, scope = CommandLine.ScopeType.INHERIT,
                        completionCandidates = ProfileCompletionCandidates.class,
                        description = "Profile to export (${COMPLETION-CANDIDATES}).")
    protected String profile;

    @CommandLine.Option(names = { "--local-kamelet-dir" },
                        description = "Local directory for loading Kamelets (takes precedence)")
    protected String localKameletDir;

    @CommandLine.Option(names = { "--spring-boot-version" }, description = "Spring Boot version",
                        defaultValue = RuntimeType.SPRING_BOOT_VERSION)
    protected String springBootVersion = RuntimeType.SPRING_BOOT_VERSION;

    @CommandLine.Option(names = { "--camel-spring-boot-version" }, description = "Camel version to use with Spring Boot")
    protected String camelSpringBootVersion;

    @CommandLine.Mixin
    protected QuarkusPlatformMixin quarkusPlatform;

    @Deprecated
    @CommandLine.Option(names = { "--quarkus-package-type" },
                        description = "Deprecated: Quarkus always uses fast-jar (layered) packaging",
                        defaultValue = "fast-jar",
                        hidden = true)
    protected String quarkusPackageType = "fast-jar";

    @CommandLine.Option(names = { "--maven-wrapper" }, defaultValue = "true", arity = "0..1", fallbackValue = "true",
                        description = "Include Maven Wrapper files in exported project")
    protected boolean mavenWrapper = true;

    @CommandLine.Option(names = { "--camel-wrapper" }, defaultValue = "false",
                        description = "Include Camel Wrapper scripts in exported project for version pinning")
    protected boolean camelWrapper;

    @CommandLine.Option(names = { "--docker" }, defaultValue = "true", arity = "0..1", fallbackValue = "true",
                        description = "Include Docker files in exported project")
    protected boolean docker = true;

    @CommandLine.Option(names = { "--open-api" }, description = "Adds an OpenAPI spec from the given file (json or yaml file)")
    protected String openapi;

    @CommandLine.Option(names = { "--observe" }, defaultValue = "false",
                        description = "Enable observability services")
    protected boolean observe;

    @CommandLine.Option(names = { "--console" }, defaultValue = "false",
                        description = "Developer console on the local HTTP server (port 8080 by default): /q/dev with Camel Main,"
                                      + " /actuator/camel with Spring Boot, and /q/camel/dev-console with Quarkus."
                                      + " The exported project only has the console with the dev profile.")
    protected boolean console;

    @CommandLine.Option(names = {
            "--dir",
            "--directory" }, description = "Directory where the project will be exported", defaultValue = ".")
    protected String exportDir;

    @CommandLine.Option(names = { "--clean-dir" }, defaultValue = "false",
                        description = "If exporting to current directory (default) then all existing files are preserved. Enabling this option will force cleaning current directory including all sub dirs (use this with care)")
    protected boolean cleanExportDir;

    @CommandLine.Option(names = { "--yes", "-y" }, defaultValue = "false",
                        description = "Automatically answer yes to confirmation prompts (e.g. when using --clean-dir)")
    protected boolean yes;

    @CommandLine.Option(names = { "--logging-level" }, defaultValue = "info",
                        completionCandidates = LoggingLevelCompletionCandidates.class,
                        description = "Logging level (${COMPLETION-CANDIDATES})")
    protected String loggingLevel = "info";

    @CommandLine.Option(names = { "--package-name" },
                        description = "For Java source files should they have the given package name. By default the package name is computed from the Maven GAV. "
                                      + "Use false to turn off and not include package name in the Java source files.")
    protected String packageName;

    @CommandLine.Mixin
    protected MavenResolverMixin mavenResolver;

    @CommandLine.Option(names = { "--package-scan-jars" }, defaultValue = "false",
                        description = "Whether to automatic package scan JARs for custom Spring or Quarkus beans making them available for Camel CLI")
    protected boolean packageScanJars;

    @CommandLine.Option(names = { "--resource-dir", "--resource-dirs" },
                        description = "Additional resource directories to include in src/main/resources (recursive, preserving structure)",
                        split = ",")
    protected List<String> resourceDirs = new ArrayList<>();

    @CommandLine.Option(names = { "--build-property" },
                        description = "Maven build properties, ex. --build-property=prop1=foo")
    protected List<String> buildProperties = new ArrayList<>();

    @CommandLine.Option(names = { "--prop", "--property" },
                        description = "Camel application properties, ex. --property=prop1=foo")
    protected String[] applicationProperties;

    @CommandLine.Option(names = { "--logging" }, defaultValue = "false",
                        description = "Can be used to turn on logging to console (logs by default to file in <user home>/.camel directory)")
    protected boolean logging;

    @CommandLine.Option(names = { "--quiet" }, defaultValue = "false",
                        description = "Will be quiet, only print when error occurs")
    protected boolean quiet;

    @CommandLine.Option(names = { "--verbose" }, defaultValue = "false",
                        description = "Verbose output of startup activity (dependency resolution and downloading")
    protected boolean verbose;

    @CommandLine.Option(names = { "--dry-run" }, defaultValue = "false",
                        description = "Preview export without writing files")
    protected boolean dryRun;

    @CommandLine.Option(names = { "--ignore-loading-error" }, defaultValue = "false",
                        description = "Whether to ignore route loading and compilation errors (use this with care!)")
    protected boolean ignoreLoadingError;

    @CommandLine.Option(names = { "--lazy-bean" }, defaultValue = "true", arity = "0..1", fallbackValue = "true",
                        description = "Whether to use lazy bean initialization (can help with complex classloading issues")
    protected boolean lazyBean = true;

    @CommandLine.Option(names = { "--skip-plugins" }, defaultValue = "false",
                        description = "Skip plugins during export")
    protected boolean skipPlugins;

    @CommandLine.Option(names = { "--groovy-pre-compiled" }, defaultValue = "false",
                        description = "Whether to include pre-compiled Groovy classes in the export (only supported with runtime=camel-main)")
    protected boolean groovyPrecompiled;

    @CommandLine.Option(names = { "--hawtio" }, defaultValue = "false",
                        description = "Whether to include Hawtio web console (only available for exporting to Spring Boot or Quarkus)")
    protected boolean hawtio;

    @CommandLine.Option(names = { "--hawtio-version" },
                        description = "Version of the Hawtio web console", defaultValue = HawtioVersion.HAWTIO_VERSION)
    protected String hawtioVersion;

    // set by camel kubernetes run, but not passed on to the exporter (as before the export API)
    protected boolean symbolicLink;
    protected boolean javaLiveReload;

    protected ExportBaseCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // configure logging first
        if (logging) {
            // log to console instead of camel-export.log file
            RuntimeUtil.configureLog(loggingLevel, false, false, false, false, null, null);
        } else {
            RuntimeUtil.configureLog(loggingLevel, false, false, false, true, null, null);
        }

        if (!quiet) {
            printConfigurationValues("Exporting integration with the following configuration:");
        }

        if (dryRun) {
            return doDryRunExport();
        }

        // export
        return export();
    }

    /**
     * Performs a dry-run export: runs the export to a temporary directory, lists the files that would be generated, and
     * cleans up.
     */
    private Integer doDryRunExport() throws Exception {
        // Save original state
        String originalExportDir = this.exportDir;
        boolean originalCleanExportDir = this.cleanExportDir;
        boolean originalQuiet = this.quiet;
        Path tempDir = Files.createTempDirectory("camel-export-dry-run-");
        try {
            // Redirect export to temp directory
            this.exportDir = tempDir.toString();
            this.cleanExportDir = false;
            this.dryRun = false; // avoid recursion in subclasses
            this.quiet = true; // suppress normal output

            Integer result = export();

            // Restore quiet so output works correctly
            this.quiet = originalQuiet;

            printer().println("Dry-run export preview:");
            printer().println();
            printer().println("Target directory: " + (originalExportDir != null ? originalExportDir : "."));
            printer().println();
            printer().println("Files that would be created:");
            try (Stream<Path> walk = Files.walk(tempDir)) {
                walk.filter(Files::isRegularFile)
                        .sorted()
                        .forEach(p -> {
                            Path rel = tempDir.relativize(p);
                            try {
                                long size = Files.size(p);
                                printer().printf("  %s (%s)%n", rel, humanReadableSize(size));
                            } catch (IOException e) {
                                printer().printf("  %s%n", rel);
                            }
                        });
            }

            return result;
        } finally {
            // Restore original state
            this.exportDir = originalExportDir;
            this.cleanExportDir = originalCleanExportDir;
            this.quiet = originalQuiet;
            this.dryRun = true;
            // Clean up temp directory
            try (Stream<Path> walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                // ignore
                            }
                        });
            }
        }
    }

    private static String humanReadableSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    protected abstract Integer export() throws Exception;

    /**
     * Whether the directory is the current directory, or one that contains it, such as <tt>./</tt>, <tt>$PWD</tt> or
     * <tt>..</tt>: cleaning it before the export deletes the files of the project, so it is only cleaned with
     * --clean-dir.
     */
    static boolean isCurrentDirOrParent(String dir) {
        return Exporter.isCurrentDirOrParent(dir);
    }

    /**
     * Exports with the exporter of the runtime, without the preparation of camel export (the options of
     * application.properties and the profile).
     */
    protected Integer exportRuntime(Path exportBaseDir) throws Exception {
        ExportRequest request = toRequest();
        request.exportBaseDir = exportBaseDir;
        Exporter exporter = new ProjectExporter(exportContext()).createExporter(request);
        if (exporter == null) {
            printer().printErr("Unknown runtime: " + runtime);
            return 1;
        }
        return exporter.export();
    }

    protected void addDependencies(String... deps) {
        var depsArray = Optional.ofNullable(deps).orElse(new String[0]);
        dependencies.addAll(Arrays.asList(depsArray));
    }

    public Comparator<MavenGav> mavenGavComparator() {
        return Exporter.mavenGavComparator();
    }

    /**
     * The request of the export with the options of this command.
     */
    protected ExportRequest toRequest() {
        ExportRequest request = new ExportRequest();
        request.exportBaseDir = this.exportBaseDir;
        request.files = this.files;
        request.dependencies = this.dependencies;
        request.runtime = this.runtime;
        request.name = this.name;
        request.port = this.port;
        request.managementPort = this.managementPort;
        request.gav = this.gav;
        request.parentPom = this.parentPom;
        request.excludes = this.excludes;
        request.mavenSettings = this.mavenSettings;
        request.mavenSettingsSecurity = this.mavenSettingsSecurity;
        request.mavenCentralEnabled = this.mavenCentralEnabled;
        request.mavenApacheSnapshotEnabled = this.mavenApacheSnapshotEnabled;
        request.mainClassname = this.mainClassname;
        request.javaVersion = this.javaVersion;
        request.camelVersion = this.camelVersion;
        request.kameletsVersion = this.kameletsVersion;
        request.profile = this.profile;
        request.localKameletDir = this.localKameletDir;
        request.springBootVersion = this.springBootVersion;
        request.camelSpringBootVersion = this.camelSpringBootVersion;
        request.quarkusPlatform = quarkusPlatform != null ? quarkusPlatform.toPlatform() : new QuarkusPlatform();
        request.quarkusPackageType = this.quarkusPackageType;
        request.mavenWrapper = this.mavenWrapper;
        request.camelWrapper = this.camelWrapper;
        request.docker = this.docker;
        request.openapi = this.openapi;
        request.observe = this.observe;
        request.console = this.console;
        request.exportDir = this.exportDir;
        request.cleanExportDir = this.cleanExportDir;
        request.yes = this.yes;
        request.loggingLevel = this.loggingLevel;
        request.packageName = this.packageName;
        request.mavenResolver = mavenResolver != null ? mavenResolver.toResolver() : new MavenResolver();
        request.packageScanJars = this.packageScanJars;
        request.resourceDirs = this.resourceDirs;
        request.buildProperties = this.buildProperties;
        request.applicationProperties = this.applicationProperties;
        request.logging = this.logging;
        request.quiet = this.quiet;
        request.verbose = this.verbose;
        request.dryRun = this.dryRun;
        request.ignoreLoadingError = this.ignoreLoadingError;
        request.lazyBean = this.lazyBean;
        request.skipPlugins = this.skipPlugins;
        request.groovyPrecompiled = this.groovyPrecompiled;
        request.hawtio = this.hawtio;
        request.hawtioVersion = this.hawtioVersion;
        return request;
    }

    /**
     * Takes the options that preparing the request has set from application.properties, the profile and the system
     * properties, as subclasses read them after the export.
     */
    protected void fromRequest(ExportRequest request) {
        this.files = request.files;
        this.runtime = request.runtime;
        this.gav = request.gav;
        this.parentPom = request.parentPom;
        this.javaVersion = request.javaVersion;
        this.camelVersion = request.camelVersion;
        this.kameletsVersion = request.kameletsVersion;
        this.localKameletDir = request.localKameletDir;
        this.camelSpringBootVersion = request.camelSpringBootVersion;
        this.springBootVersion = request.springBootVersion;
        this.mavenWrapper = request.mavenWrapper;
        this.camelWrapper = request.camelWrapper;
        this.exportDir = request.exportDir;
        this.openapi = request.openapi;
        this.mavenSettings = request.mavenSettings;
        this.mavenSettingsSecurity = request.mavenSettingsSecurity;
        this.mavenCentralEnabled = request.mavenCentralEnabled;
        this.mavenApacheSnapshotEnabled = request.mavenApacheSnapshotEnabled;
        this.excludes = request.excludes;
        this.quarkusPlatform = QuarkusPlatformMixin.of(new Properties(), request.quarkusPlatform);
        this.mavenResolver = MavenResolverMixin.of(new Properties(), request.mavenResolver);
    }

    /**
     * The context of the export: the dependencies are resolved by a silent camel run, the exporters are those of the
     * active plugins.
     */
    protected ExportContext exportContext() {
        return CliExportContext.create(getMain(), outPrinter(), yes);
    }

    protected Printer outPrinter() {
        return super.printer();
    }

    @Override
    protected Printer printer() {
        if (quiet) {
            if (quietPrinter == null) {
                quietPrinter = new Printer.QuietPrinter(super.printer());
            }

            CommandHelper.setPrinter(quietPrinter);
            return quietPrinter;
        }

        return super.printer();
    }

    static class FilesConsumer extends ParameterConsumer<Export> {
        @Override
        protected void doConsumeParameters(Stack<String> args, Export cmd) {
            String arg = args.pop();
            cmd.files.add(arg);
        }
    }
}
