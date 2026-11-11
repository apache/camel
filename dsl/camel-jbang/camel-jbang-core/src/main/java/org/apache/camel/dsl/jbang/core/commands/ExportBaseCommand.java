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
import java.util.stream.Stream;

import org.aesh.command.option.Arguments;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;
import org.aesh.command.option.OptionVisibility;
import org.apache.camel.dsl.jbang.core.common.JavaVersionCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.LoggingLevelCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.ProfileCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.RuntimeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.RuntimeTypeConverter;
import org.apache.camel.dsl.jbang.run.common.ExportHelper;
import org.apache.camel.dsl.jbang.run.common.HawtioVersion;
import org.apache.camel.dsl.jbang.run.common.MavenResolver;
import org.apache.camel.dsl.jbang.run.common.Printer;
import org.apache.camel.dsl.jbang.run.common.PropertyResolver;
import org.apache.camel.dsl.jbang.run.common.QuarkusPlatform;
import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.camel.dsl.jbang.run.common.RuntimeUtil;
import org.apache.camel.dsl.jbang.run.export.ExportContext;
import org.apache.camel.dsl.jbang.run.export.ExportRequest;
import org.apache.camel.dsl.jbang.run.export.ProjectExporter;
import org.apache.camel.tooling.maven.MavenGav;

import static org.apache.camel.dsl.jbang.run.common.CamelJBangConstants.*;

public abstract class ExportBaseCommand extends CamelCommand {

    protected Path exportBaseDir;
    private Printer quietPrinter;

    @Arguments(description = "The Camel file(s) to export. If no files is specified then what was last run will be exported.",
               paramLabel = "<files>")
    protected List<String> files = new ArrayList<>();

    @OptionList(name = "dep", aliases = { "dependency" }, valueSeparator = ',', description = "Add additional dependencies")
    protected List<String> dependencies = new ArrayList<>();

    @Option(name = "runtime", description = "Runtime (${COMPLETION-CANDIDATES})", completer = RuntimeCompletionCandidates.class,
            converter = RuntimeTypeConverter.class)
    protected RuntimeType runtime;

    @Option(name = "name", description = "The integration name. Use this when the name should not get derived otherwise.")
    protected String name;

    @Option(name = "port", description = "Embeds a local HTTP server on this port")
    int port = -1;

    @Option(name = "management-port", description = "To use a dedicated port for HTTP management")
    int managementPort = -1;

    @Option(name = "gav", description = "The Maven group:artifact:version")
    protected String gav;

    @Option(name = "parent-pom", description = "The Maven parent POM group:artifact:version")
    protected String parentPom;

    @OptionList(name = "exclude", valueSeparator = OptionList.NO_SEPARATOR, description = "Exclude files by name or pattern")
    protected List<String> excludes = new ArrayList<>();

    @Option(name = "maven-settings",
            description = "Optional location of Maven settings.xml file to configure servers, repositories, mirrors and proxies."
                          + " If set to \"false\", not even the default ~/.m2/settings.xml will be used.")
    protected String mavenSettings;

    @Option(name = "maven-settings-security",
            description = "Optional location of Maven settings-security.xml file to decrypt settings.xml")
    protected String mavenSettingsSecurity;

    @Option(name = "maven-central-enabled", hasValue = false,
            description = "Whether downloading JARs from Maven Central repository is enabled", defaultValue = "true")
    protected boolean mavenCentralEnabled = true;

    @Option(name = "maven-apache-snapshot-enabled", hasValue = false,
            description = "Whether downloading JARs from ASF Maven Snapshot repository is enabled", defaultValue = "true")
    protected boolean mavenApacheSnapshotEnabled = true;

    @Option(name = "main-classname", description = "The class name of the Camel Main application class",
            defaultValue = "CamelApplication")
    protected String mainClassname = "CamelApplication";

    @Option(name = "java-version", aliases = { "java" }, description = "Java version (${COMPLETION-CANDIDATES})",
            defaultValue = "21", completer = JavaVersionCompletionCandidates.class)
    protected String javaVersion = "21";

    @Option(name = "camel-version", description = "To export using a different Camel version than the default version.")
    protected String camelVersion;

    @Option(name = "kamelets-version", description = "Apache Camel Kamelets version (auto-detected from classpath if not set)")
    protected String kameletsVersion;

    @Option(name = "profile", description = "Profile to export (${COMPLETION-CANDIDATES}).", inherited = true,
            completer = ProfileCompletionCandidates.class)
    protected String profile;

    @Option(name = "local-kamelet-dir", description = "Local directory for loading Kamelets (takes precedence)")
    protected String localKameletDir;

    @Option(name = "spring-boot-version", description = "Spring Boot version", defaultValue = RuntimeType.SPRING_BOOT_VERSION)
    protected String springBootVersion = RuntimeType.SPRING_BOOT_VERSION;

    @Option(name = "camel-spring-boot-version", description = "Camel version to use with Spring Boot")
    protected String camelSpringBootVersion;

    @Mixin
    protected QuarkusPlatformMixin quarkusPlatform;

    @Deprecated
    @Option(name = "quarkus-package-type", description = "Deprecated: Quarkus always uses fast-jar (layered) packaging",
            defaultValue = "fast-jar", visibility = OptionVisibility.HIDDEN)
    protected String quarkusPackageType = "fast-jar";

    @Option(name = "maven-wrapper", hasValue = false, description = "Include Maven Wrapper files in exported project",
            defaultValue = "true")
    protected boolean mavenWrapper = true;

    @Option(name = "camel-wrapper", hasValue = false,
            description = "Include Camel Wrapper scripts in exported project for version pinning", defaultValue = "false")
    protected boolean camelWrapper;

    @Option(name = "docker", hasValue = false, description = "Include Docker files in exported project", defaultValue = "true")
    protected boolean docker = true;

    @Option(name = "open-api", description = "Adds an OpenAPI spec from the given file (json or yaml file)")
    protected String openapi;

    @Option(name = "observe", hasValue = false, description = "Enable observability services", defaultValue = "false")
    protected boolean observe;

    @Option(name = "console", hasValue = false,
            description = "Developer console on the local HTTP server (port 8080 by default): /q/dev with Camel Main,"
                          + " /actuator/camel with Spring Boot, and /q/camel/dev-console with Quarkus."
                          + " The exported project only has the console with the dev profile.",
            defaultValue = "false")
    protected boolean console;

    @Option(name = "dir", aliases = { "directory" }, description = "Directory where the project will be exported",
            defaultValue = ".")
    protected String exportDir;

    @Option(name = "clean-dir", hasValue = false,
            description = "If exporting to current directory (default) then all existing files are preserved. Enabling this option will force cleaning current directory including all sub dirs (use this with care)",
            defaultValue = "false")
    protected boolean cleanExportDir;

    @Option(name = "yes", shortName = 'y', hasValue = false,
            description = "Automatically answer yes to confirmation prompts (e.g. when using --clean-dir)",
            defaultValue = "false")
    protected boolean yes;

    @Option(name = "logging-level", description = "Logging level (${COMPLETION-CANDIDATES})", defaultValue = "info",
            completer = LoggingLevelCompletionCandidates.class)
    protected String loggingLevel = "info";

    @Option(name = "package-name",
            description = "For Java source files should they have the given package name. By default the package name is computed from the Maven GAV. "
                          + "Use false to turn off and not include package name in the Java source files.")
    protected String packageName;

    @Mixin
    protected MavenResolverMixin mavenResolver;

    @Option(name = "package-scan-jars", hasValue = false,
            description = "Whether to automatic package scan JARs for custom Spring or Quarkus beans making them available for Camel CLI",
            defaultValue = "false")
    protected boolean packageScanJars;

    @OptionList(name = "resource-dir", aliases = { "resource-dirs" }, valueSeparator = ',',
                description = "Additional resource directories to include in src/main/resources (recursive, preserving structure)")
    protected List<String> resourceDirs = new ArrayList<>();

    @OptionList(name = "build-property", valueSeparator = OptionList.NO_SEPARATOR,
                description = "Maven build properties, ex. --build-property=prop1=foo")
    protected List<String> buildProperties = new ArrayList<>();

    @OptionList(name = "prop", aliases = { "property" }, valueSeparator = OptionList.NO_SEPARATOR,
                description = "Camel application properties, ex. --property=prop1=foo")
    protected String[] applicationProperties;

    @Option(name = "logging", hasValue = false,
            description = "Can be used to turn on logging to console (logs by default to file in <user home>/.camel directory)",
            defaultValue = "false")
    protected boolean logging;

    @Option(name = "quiet", hasValue = false, description = "Will be quiet, only print when error occurs",
            defaultValue = "false")
    protected boolean quiet;

    @Option(name = "verbose", hasValue = false,
            description = "Verbose output of startup activity (dependency resolution and downloading", defaultValue = "false")
    protected boolean verbose;

    @Option(name = "dry-run", hasValue = false, description = "Preview export without writing files", defaultValue = "false")
    protected boolean dryRun;

    @Option(name = "ignore-loading-error", hasValue = false,
            description = "Whether to ignore route loading and compilation errors (use this with care!)",
            defaultValue = "false")
    protected boolean ignoreLoadingError;

    @Option(name = "lazy-bean", hasValue = false,
            description = "Whether to use lazy bean initialization (can help with complex classloading issues",
            defaultValue = "true")
    protected boolean lazyBean = true;

    @Option(name = "skip-plugins", hasValue = false, description = "Skip plugins during export", defaultValue = "false")
    protected boolean skipPlugins;

    @Option(name = "groovy-pre-compiled", hasValue = false,
            description = "Whether to include pre-compiled Groovy classes in the export (only supported with runtime=camel-main)",
            defaultValue = "false")
    protected boolean groovyPrecompiled;

    @Option(name = "hawtio", hasValue = false,
            description = "Whether to include Hawtio web console (only available for exporting to Spring Boot or Quarkus)",
            defaultValue = "false")
    protected boolean hawtio;

    @Option(name = "hawtio-version", description = "Version of the Hawtio web console",
            defaultValue = HawtioVersion.HAWTIO_VERSION)
    protected String hawtioVersion;

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
        return ExportHelper.isCurrentDirOrParent(dir);
    }

    /**
     * Exports with the exporter of the runtime, without the preparation of camel export (the options of
     * application.properties and the profile).
     */
    protected Integer exportRuntime(Path exportBaseDir) throws Exception {
        ExportRequest request = toRequest();
        request.baseDir = exportBaseDir;
        applySystemProperties(request);
        return new ProjectExporter(exportContext()).export(exportBaseDir, request);
    }

    /**
     * Sets what the system properties of the CLI configure on the request, as the export engine reads no system
     * properties: the Quarkus platform, the Camel Spring Boot version, and the repositories (camel.jbang.repos) when
     * none are configured. The extra default repositories of the camel.extra.repos system property are left to the
     * Maven downloader.
     */
    static void applySystemProperties(ExportRequest request) {
        request.quarkusPlatform = QuarkusPlatform.of(System.getProperties(), request.quarkusPlatform);
        request.camelSpringBootVersion
                = PropertyResolver.fromSystemProperty(CAMEL_SPRING_BOOT_VERSION, () -> request.camelSpringBootVersion);
        MavenResolver resolver = request.mavenResolver;
        String repos = System.getProperty(REPOS);
        if (resolver.repos() == null && repos != null) {
            request.mavenResolver = new MavenResolver(resolver.download(), repos, resolver.fresh(), resolver.extraRepos());
        }
    }

    protected void addDependencies(String... deps) {
        var depsArray = Optional.ofNullable(deps).orElse(new String[0]);
        dependencies.addAll(Arrays.asList(depsArray));
    }

    public Comparator<MavenGav> mavenGavComparator() {
        return ExportHelper.mavenGavComparator();
    }

    /**
     * The request of the export with the options of this command.
     */
    protected ExportRequest toRequest() {
        ExportRequest request = new ExportRequest();
        request.baseDir = this.exportBaseDir;
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
}
