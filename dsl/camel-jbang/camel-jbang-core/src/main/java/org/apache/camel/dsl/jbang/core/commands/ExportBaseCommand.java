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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.catalog.KameletCatalogHelper;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.Plugin;
import org.apache.camel.dsl.jbang.core.common.PluginExporter;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.dsl.jbang.core.common.RuntimeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.RuntimeTypeConverter;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.tooling.maven.MavenDownloader;
import org.apache.camel.tooling.maven.MavenDownloaderImpl;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.tooling.maven.MavenResolutionException;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import picocli.CommandLine;

import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.*;

public abstract class ExportBaseCommand extends CamelCommand {

    protected static final String BUILD_DIR = CommandLineHelper.CAMEL_JBANG_WORK_DIR + "/work";

    protected static final String[] SETTINGS_PROP_SOURCE_KEYS = new String[] {
            "camel.main.routesIncludePattern",
            "camel.component.properties.location",
            "camel.component.kamelet.location",
            CLASSPATH_FILES,
            LOCAL_KAMELET_DIR,
            GROOVY_FILES,
            SCRIPT_FILES,
            TLS_FILES,
            JKUBE_FILES,
            "kamelet"
    };

    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "^\\s*package\\s+([a-zA-Z][.\\w]*)\\s*;.*$", Pattern.MULTILINE);

    private static final Set<String> EXCLUDED_GROUP_IDS = Set.of("org.fusesource.jansi", "org.apache.logging.log4j");

    protected Path exportBaseDir;
    private MavenDownloader downloader;
    private Printer quietPrinter;

    @CommandLine.Parameters(description = "The Camel file(s) to export. If no files is specified then what was last run will be exported.",
                            arity = "0..9", paramLabel = "<files>", parameterConsumer = FilesConsumer.class)
    protected Path[] filePaths; // Defined only for file path completion; the field never used

    protected List<String> files = new ArrayList<>();

    @CommandLine.Option(names = { "--repo", "--repos" },
                        description = "Additional maven repositories (Use commas to separate multiple repositories)")
    protected String repositories;

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

    @CommandLine.Option(names = { "--exclude" }, description = "Exclude files by name or pattern")
    protected List<String> excludes = new ArrayList<>();

    @CommandLine.Option(names = { "--maven-settings" },
                        description = "Optional location of Maven settings.xml file to configure servers, repositories, mirrors and proxies."
                                      + " If set to \"false\", not even the default ~/.m2/settings.xml will be used.")
    protected String mavenSettings;

    @CommandLine.Option(names = { "--maven-settings-security" },
                        description = "Optional location of Maven settings-security.xml file to decrypt settings.xml")
    protected String mavenSettingsSecurity;

    @CommandLine.Option(names = { "--maven-central-enabled" }, defaultValue = "true",
                        description = "Whether downloading JARs from Maven Central repository is enabled")
    protected boolean mavenCentralEnabled = true;

    @CommandLine.Option(names = { "--maven-apache-snapshot-enabled" }, defaultValue = "true",
                        description = "Whether downloading JARs from ASF Maven Snapshot repository is enabled")
    protected boolean mavenApacheSnapshotEnabled = true;

    @CommandLine.Option(names = { "--main-classname" },
                        description = "The class name of the Camel Main application class",
                        defaultValue = "CamelApplication")
    protected String mainClassname = "CamelApplication";

    @CommandLine.Option(names = { "--java-version" }, description = "Java version", defaultValue = "21")
    protected String javaVersion = "21";

    @CommandLine.Option(names = { "--camel-version" },
                        description = "To export using a different Camel version than the default version.")
    protected String camelVersion;

    @CommandLine.Option(names = {
            "--kamelets-version" }, description = "Apache Camel Kamelets version",
                        defaultValue = RuntimeType.KAMELETS_VERSION)
    protected String kameletsVersion = RuntimeType.KAMELETS_VERSION;

    @CommandLine.Option(names = { "--profile" }, scope = CommandLine.ScopeType.INHERIT,
                        description = "Profile to export (dev, test, or prod).")
    protected String profile;

    @CommandLine.Option(names = { "--local-kamelet-dir" },
                        description = "Local directory for loading Kamelets (takes precedence)")
    protected String localKameletDir;

    @CommandLine.Option(names = { "--spring-boot-version" }, description = "Spring Boot version",
                        defaultValue = RuntimeType.SPRING_BOOT_VERSION)
    protected String springBootVersion = RuntimeType.SPRING_BOOT_VERSION;

    @CommandLine.Option(names = { "--camel-spring-boot-version" }, description = "Camel version to use with Spring Boot")
    protected String camelSpringBootVersion;

    @CommandLine.Option(names = { "--quarkus-group-id" }, description = "Quarkus Platform Maven groupId",
                        defaultValue = "io.quarkus.platform")
    protected String quarkusGroupId = "io.quarkus.platform";

    @CommandLine.Option(names = { "--quarkus-artifact-id" }, description = "Quarkus Platform Maven artifactId",
                        defaultValue = "quarkus-bom")
    protected String quarkusArtifactId = "quarkus-bom";

    @CommandLine.Option(names = { "--quarkus-version" }, description = "Quarkus Platform version",
                        defaultValue = RuntimeType.QUARKUS_VERSION)
    protected String quarkusVersion = RuntimeType.QUARKUS_VERSION;

    @CommandLine.Option(names = { "--maven-wrapper" }, defaultValue = "true",
                        description = "Include Maven Wrapper files in exported project")
    protected boolean mavenWrapper = true;

    @CommandLine.Option(names = { "--gradle-wrapper" }, defaultValue = "true",
                        description = "Include Gradle Wrapper files in exported project")
    protected boolean gradleWrapper = true;

    @CommandLine.Option(names = { "--build-tool" }, defaultValue = "maven",
                        description = "Build tool to use (maven or gradle)")
    protected String buildTool = "maven";

    @CommandLine.Option(names = { "--open-api" }, description = "Adds an OpenAPI spec from the given file (json or yaml file)")
    protected String openapi;

    @CommandLine.Option(names = { "--observe" }, defaultValue = "false",
                        description = "Enable observability services")
    protected boolean observe;

    @CommandLine.Option(names = {
            "--dir",
            "--directory" }, description = "Directory where the project will be exported", defaultValue = ".")
    protected String exportDir;

    @CommandLine.Option(names = { "--clean-dir" }, defaultValue = "false",
                        description = "If exporting to current directory (default) then all existing files are preserved. Enabling this option will force cleaning current directory including all sub dirs (use this with care)")
    protected boolean cleanExportDir;

    @CommandLine.Option(names = { "--logging-level" }, defaultValue = "info", description = "Logging level")
    protected String loggingLevel = "info";

    @CommandLine.Option(names = { "--package-name" },
                        description = "For Java source files should they have the given package name. By default the package name is computed from the Maven GAV. "
                                      + "Use false to turn off and not include package name in the Java source files.")
    protected String packageName;

    @CommandLine.Option(names = { "--fresh" }, defaultValue = "false",
                        description = "Make sure we use fresh (i.e. non-cached) resources")
    protected boolean fresh;

    @CommandLine.Option(names = { "--download" }, defaultValue = "true",
                        description = "Whether to allow automatic downloading JAR dependencies (over the internet)")
    protected boolean download = true;

    @CommandLine.Option(names = { "--package-scan-jars" }, defaultValue = "false",
                        description = "Whether to automatic package scan JARs for custom Spring or Quarkus beans making them available for Camel JBang")
    protected boolean packageScanJars;

    @CommandLine.Option(names = { "--build-property" },
                        description = "Maven/Gradle build properties, ex. --build-property=prop1=foo")
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

    @CommandLine.Option(names = { "--ignore-loading-error" }, defaultValue = "false",
                        description = "Whether to ignore route loading and compilation errors (use this with care!)")
    protected boolean ignoreLoadingError;

    @CommandLine.Option(names = { "--lazy-bean" }, defaultValue = "true",
                        description = "Whether to use lazy bean initialization (can help with complex classloading issues")
    protected boolean lazyBean = true;

    @CommandLine.Option(names = { "--skip-plugins" }, defaultValue = "false",
                        description = "Skip plugins during export")
    protected boolean skipPlugins;

    @CommandLine.Option(names = { "--groovy-pre-compiled" }, defaultValue = "false",
                        description = "Whether to include pre-compiled Groovy classes in the export (only supported with runtime=camel-main)")
    protected boolean groovyPrecompiled;

    protected boolean symbolicLink;     // copy source files using symbolic link
    protected boolean javaLiveReload; // reload java codes in dev
    public String pomTemplateName;   // support for specialised pom templates

    public ExportBaseCommand(CamelJBangMain main) {
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
        // export
        return export();
    }

    protected static String mavenRepositoriesAsPomXml(String repos) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        sb.append("    <repositories>\n");
        if (!repos.isEmpty()) {
            for (String repo : repos.split(",")) {
                sb.append("        <repository>\n");
                sb.append("            <id>custom").append(i++).append("</id>\n");
                sb.append("            <url>").append(repo).append("</url>\n");
                if (repo.contains("snapshots")) {
                    sb.append("            <releases>\n");
                    sb.append("                <enabled>false</enabled>\n");
                    sb.append("            </releases>\n");
                    sb.append("            <snapshots>\n");
                    sb.append("                <enabled>true</enabled>\n");
                    sb.append("            </snapshots>\n");
                }
                sb.append("        </repository>\n");
            }
        }
        sb.append("    </repositories>\n");
        sb.append("    <pluginRepositories>\n");
        if (!repos.isEmpty()) {
            for (String repo : repos.split(",")) {
                sb.append("        <pluginRepository>\n");
                sb.append("            <id>custom").append(i++).append("</id>\n");
                sb.append("            <url>").append(repo).append("</url>\n");
                if (repo.contains("snapshots")) {
                    sb.append("            <releases>\n");
                    sb.append("                <enabled>false</enabled>\n");
                    sb.append("            </releases>\n");
                    sb.append("            <snapshots>\n");
                    sb.append("                <enabled>true</enabled>\n");
                    sb.append("            </snapshots>\n");
                }
                sb.append("        </pluginRepository>\n");
            }
        }
        sb.append("    </pluginRepositories>\n");
        return sb.toString();
    }

    protected abstract Integer export() throws Exception;

    protected static String getScheme(String name) {
        int pos = name.indexOf(":");
        if (pos != -1) {
            return name.substring(0, pos);
        }
        return null;
    }

    protected Integer runSilently(boolean ignoreLoadingError, boolean lazyBean, boolean verbose) throws Exception {
        Run run = new Run(getMain());
        // need to declare the profile to use for run
        run.exportBaseDir = exportBaseDir;
        run.dependencies = dependencies;
        run.files = files;
        run.name = name;
        run.port = port;
        run.managementPort = managementPort;
        run.excludes = excludes;
        run.openapi = openapi;
        run.observe = observe;
        run.download = download;
        run.packageScanJars = packageScanJars;
        run.runtime = runtime;
        run.camelVersion = camelVersion;
        run.camelSpringBootVersion = camelSpringBootVersion;
        run.quarkusVersion = quarkusVersion;
        run.quarkusGroupId = quarkusGroupId;
        run.springBootVersion = springBootVersion;
        run.kameletsVersion = kameletsVersion;
        run.localKameletDir = localKameletDir;
        run.ignoreLoadingError = ignoreLoadingError;
        run.lazyBean = lazyBean;
        run.property = applicationProperties;
        run.repositories = repositories;
        run.verbose = verbose;
        run.logging = logging;
        return run.runExport(ignoreLoadingError);
    }

    protected void addDependencies(String... deps) {
        var depsArray = Optional.ofNullable(deps).orElse(new String[0]);
        dependencies.addAll(Arrays.asList(depsArray));
    }

    protected String replaceBuildProperties(String context) {
        Properties properties = mapBuildProperties();

        if (!skipPlugins) {
            Set<PluginExporter> exporters = PluginHelper.getActivePlugins(getMain(), repositories).values()
                    .stream()
                    .map(Plugin::getExporter)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toSet());

            for (PluginExporter exporter : exporters) {
                exporter.getBuildProperties().forEach(properties::putIfAbsent);
            }
        }

        String mavenProperties = properties.entrySet().stream()
                .map(item -> {
                    return String.format("        <%s>%s</%s>", item.getKey(), item.getValue(), item.getKey());
                })
                .collect(Collectors.joining(System.lineSeparator()));

        if (!mavenProperties.isEmpty()) {
            context = context.replaceFirst(Pattern.quote("{{ .BuildProperties }}"), Matcher.quoteReplacement(mavenProperties));
        } else {
            context = context.replaceFirst(Pattern.quote("{{ .BuildProperties }}"), "");
        }
        return context;
    }

    protected Set<String> resolveDependencies(Path settings, Path profile) throws Exception {
        Set<String> answer = new TreeSet<>((o1, o2) -> {
            // favour org.apache.camel first
            boolean c1 = o1.contains("org.apache.camel:");
            boolean c2 = o2.contains("org.apache.camel:");
            if (c1 && !c2) {
                return -1;
            } else if (!c1 && c2) {
                return 1;
            }
            return o1.compareTo(o2);
        });

        if (kameletsVersion == null) {
            kameletsVersion = VersionHelper.extractKameletsVersion();
        }

        // custom dependencies
        for (String d : dependencies) {
            answer.add(normalizeDependency(d));
        }

        List<String> lines = RuntimeUtil.loadPropertiesLines(settings);

        // check if we use custom and/or official ASF kamelets
        List<String> officialKamelets = KameletCatalogHelper.findKameletNames(kameletsVersion, repositories);
        boolean kamelets = false;
        boolean asfKamelets = false;
        for (String line : lines) {
            if (line.startsWith("kamelet=")) {
                kamelets = true;
                String name = StringHelper.after(line, "kamelet=");
                if (officialKamelets.contains(name)) {
                    asfKamelets = true;
                    break;
                }
            }
        }
        // any other custom kamelets that are loaded via routes
        kamelets |= lines.stream()
                .anyMatch(l -> (l.startsWith("camel.main.routesIncludePattern=") && l.contains(".kamelet.yaml"))
                        || l.startsWith("camel.component.kamelet.location=") && l.contains(".kamelet.yaml"));
        for (String line : lines) {
            if (line.startsWith("dependency=")) {
                String v = StringHelper.after(line, "dependency=");
                // skip endpointdsl as its already included, and core-languages and java-joor as
                // we let quarkus compile
                boolean skip = v == null || v.contains("org.apache.camel:camel-core-languages")
                        || v.contains("org.apache.camel:camel-java-joor-dsl")
                        || v.contains("camel-endpointdsl")
                        || (v.contains("org.apache.camel.kamelets:camel-kamelets"))
                        || !(kamelets) && v.contains("org.apache.camel:camel-kamelet");
                if (!skip) {
                    answer.add(v);
                }
                if (kamelets && v != null && v.contains("org.apache.camel:camel-kamelet")) {
                    // kamelets also need yaml-dsl
                    answer.add("camel:kamelet");
                    answer.add("camel:yaml-dsl");
                    if (asfKamelets) {
                        // include JARs for official ASF kamelets
                        answer.add("org.apache.camel.kamelets:camel-kamelets:" + kameletsVersion);
                        if (VersionHelper.compare(camelVersion, "4.10.0") < 0) {
                            answer.add("org.apache.camel.kamelets:camel-kamelets-utils:" + kameletsVersion);
                        }
                    }
                }
            } else if (line.startsWith(DEPENDENCIES + "=")) {
                String deps = StringHelper.after(line, DEPENDENCIES + "=");
                if (!deps.isEmpty()) {
                    for (String d : deps.split(",")) {
                        answer.add(d.trim());
                        if (kamelets && d.contains("org.apache.camel:camel-kamelet")) {
                            // kamelets also need yaml-dsl
                            answer.add("camel:kamelet");
                            answer.add("camel:yaml-dsl");
                            if (asfKamelets) {
                                // include JARs for official ASF kamelets
                                answer.add("org.apache.camel.kamelets:camel-kamelets:" + kameletsVersion);
                                if (VersionHelper.compare(camelVersion, "4.10.0") < 0) {
                                    answer.add("org.apache.camel.kamelets:camel-kamelets-utils:" + kameletsVersion);
                                }
                            }
                        }
                    }
                }
            } else if (line.startsWith(DEPENDENCIES_MAIN + "=") && runtime == RuntimeType.main) {
                addRuntimeSpecificDependencies(StringHelper.after(line, DEPENDENCIES_MAIN + "="), answer);
            } else if (line.startsWith(DEPENDENCIES_SPRING_BOOT + "=") && runtime == RuntimeType.springBoot) {
                addRuntimeSpecificDependencies(StringHelper.after(line, DEPENDENCIES_SPRING_BOOT + "="), answer);
            } else if (line.startsWith(DEPENDENCIES_QUARKUS + "=") && runtime == RuntimeType.quarkus) {
                addRuntimeSpecificDependencies(StringHelper.after(line, DEPENDENCIES_QUARKUS + "="), answer);
            } else if (line.startsWith(CLASSPATH_FILES + "=")) {
                String deps = StringHelper.after(line, CLASSPATH_FILES + "=");
                if (!deps.isEmpty()) {
                    for (String d : deps.split(",")) {
                        // special to include local JARs in export lib folder
                        if (d.endsWith(".jar")) {
                            answer.add("lib:" + d.trim());
                        }
                    }
                }
            } else if (line.startsWith("camel.main.routesIncludePattern=")) {
                String routes = StringHelper.after(line, "camel.main.routesIncludePattern=");
                if (!routes.isEmpty()) {
                    for (String r : routes.split(",")) {
                        String ext = FileUtil.onlyExt(r, true);
                        if (ext != null) {
                            // java is moved into src/main/java and compiled during build
                            // for the other DSLs we need to add dependencies
                            if ("xml".equals(ext)) {
                                answer.add("mvn:org.apache.camel:camel-xml-io-dsl");
                            } else if ("yaml".equals(ext)) {
                                answer.add("mvn:org.apache.camel:camel-yaml-dsl");
                                // is it a kamelet?
                                ext = FileUtil.onlyExt(r, false);
                                if ("kamelet.yaml".equals(ext)) {
                                    answer.add("camel:kamelet");
                                    if (asfKamelets) {
                                        // include JARs for official ASF kamelets
                                        answer.add("mvn:org.apache.camel.kamelets:camel-kamelets:" + kameletsVersion);
                                        if (VersionHelper.compare(camelVersion, "4.10.0") < 0) {
                                            answer.add("mvn:org.apache.camel.kamelets:camel-kamelets-utils:" + kameletsVersion);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (kamelets && line.startsWith("camel.component.kamelet.location=")) {
                // kamelets need yaml-dsl
                answer.add("camel:kamelet");
                answer.add("camel:yaml-dsl");
                if (asfKamelets) {
                    // include JARs for official ASF kamelets
                    answer.add("mvn:org.apache.camel.kamelets:camel-kamelets:" + kameletsVersion);
                    if (VersionHelper.compare(camelVersion, "4.10.0") < 0) {
                        answer.add("mvn:org.apache.camel.kamelets:camel-kamelets-utils:" + kameletsVersion);
                    }
                }
            } else if (line.startsWith("modeline=")) {
                answer.add("camel:dsl-modeline");
            }
        }

        // include custom dependencies defined in profile
        if (profile != null && Files.exists(profile)) {
            Properties prop = new CamelCaseOrderedProperties();
            RuntimeUtil.loadProperties(prop, profile);
            for (String d : RuntimeUtil.getDependenciesAsArray(prop)) {
                answer.add(d.trim());
            }
            // automatic add needed dependencies when dev-console is enabled
            if ("true".equalsIgnoreCase(prop.getProperty("camel.main.devConsoleEnabled"))
                    || "true".equalsIgnoreCase(prop.getProperty("camel.management.devConsoleEnabled"))
                    || "true".equalsIgnoreCase(prop.getProperty("camel.server.devConsoleEnabled"))) {
                answer.add("camel:console");
                answer.add("camel:management");
            }
            // automatic add needed dependencies when main server enabled plugins
            if ("true".equalsIgnoreCase(prop.getProperty("camel.management.jolokiaEnabled"))
                    || "true".equalsIgnoreCase(prop.getProperty("camel.server.jolokiaEnabled"))) {
                answer.add("camel:platform-http-jolokia");
            }
            if ("true".equalsIgnoreCase(prop.getProperty("camel.management.metricsEnabled"))
                    || "true".equalsIgnoreCase(prop.getProperty("camel.server.metricsEnabled"))) {
                answer.add("camel:micrometer-prometheus");
            }
        }

        if (!skipPlugins) {
            Set<PluginExporter> exporters = PluginHelper.getActivePlugins(getMain(), repositories).values()
                    .stream()
                    .map(Plugin::getExporter)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toSet());

            for (PluginExporter exporter : exporters) {
                answer.addAll(exporter.getDependencies(runtime));
            }
        }

        // remove duplicate versions (keep first)
        Map<String, String> versions = new HashMap<>();
        Set<String> toBeRemoved = new HashSet<>();
        for (String line : answer) {
            MavenGav gav = MavenGav.parseGav(line);
            String ga = gav.getGroupId() + ":" + gav.getArtifactId();
            if (!versions.containsKey(ga)) {
                versions.put(ga, gav.getVersion());
            } else {
                toBeRemoved.add(line);
            }
        }
        answer.removeAll(toBeRemoved);

        return answer;
    }

    private static void addRuntimeSpecificDependencies(String deps, Set<String> answer) {
        if (!deps.isEmpty()) {
            for (String d : deps.split(",")) {
                answer.add(d.trim());
            }
        }
    }

    protected void copySourceFiles(
            Path settings, Path profile, Path srcJavaDirRoot, Path srcJavaDir, Path srcResourcesDir, Path srcCamelResourcesDir,
            Path srcKameletsResourcesDir, String packageName)
            throws Exception {

        // read the settings file and find the files to copy
        Properties prop = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(prop, settings);

        String localKameletDir = prop.getProperty(LOCAL_KAMELET_DIR);
        if (localKameletDir != null) {
            String scheme = getScheme(localKameletDir);
            if (scheme != null) {
                localKameletDir = localKameletDir.substring(scheme.length() + 1);
            }
        }
        for (String k : SETTINGS_PROP_SOURCE_KEYS) {
            String files;
            if ("kamelet".equals(k)) {
                // special for kamelet as there can be multiple entries
                files = RuntimeUtil.loadPropertiesLines(settings).stream()
                        .filter(l -> l.startsWith("kamelet="))
                        .map(l -> StringHelper.after(l, "="))
                        .collect(Collectors.joining(","));
            } else {
                files = prop.getProperty(k);
            }
            if (files != null && !files.isEmpty()) {
                for (String f : files.split(",")) {
                    String scheme = getScheme(f);
                    if (scheme != null) {
                        f = f.substring(scheme.length() + 1);
                    }
                    boolean skip = profile.getFileName().toString().equals(f); // skip copying profile
                    if (skip) {
                        continue;
                    }
                    if ("github".equals(scheme)) {
                        continue;
                    }
                    String ext = FileUtil.onlyExt(f, true);
                    String ext2 = FileUtil.onlyExt(f, false);
                    if (!"kamelet".equals(k) && ext == null) {
                        continue;
                    }
                    boolean java = "java".equals(ext);
                    boolean kamelet = "kamelet".equals(k) || "camel.component.kamelet.location".equals(k)
                            || LOCAL_KAMELET_DIR.equals(k) || "kamelet.yaml".equalsIgnoreCase(ext2);
                    boolean camel = !kamelet && "camel.main.routesIncludePattern".equals(k);
                    boolean jkube = JKUBE_FILES.equals(k);
                    boolean script = SCRIPT_FILES.equals(k);
                    boolean groovy = GROOVY_FILES.equals(k);
                    boolean tls = TLS_FILES.equals(k);
                    boolean web = ext != null && List.of("css", "html", "ico", "jpeg", "jpg", "js", "png").contains(ext);
                    Path targetDir;
                    if (java) {
                        targetDir = srcJavaDir;
                    } else if (camel) {
                        targetDir = srcCamelResourcesDir;
                    } else if (kamelet) {
                        targetDir = srcKameletsResourcesDir;
                    } else if (script) {
                        targetDir = srcJavaDirRoot.getParent().resolve("scripts");
                    } else if (groovy) {
                        targetDir = srcResourcesDir.resolve("camel-groovy");
                    } else if (tls) {
                        targetDir = srcJavaDirRoot.getParent().resolve("tls");
                    } else if (web) {
                        targetDir = srcResourcesDir.resolve("META-INF/resources");
                    } else {
                        targetDir = srcResourcesDir;
                    }
                    Files.createDirectories(targetDir);

                    Path source;
                    if ("kamelet".equals(k) && localKameletDir != null) {
                        // source is a local kamelet
                        source = Paths.get(localKameletDir, f + ".kamelet.yaml");
                    } else {
                        source = Paths.get(f);
                    }
                    Path out;
                    if (Files.isDirectory(source)) {
                        out = targetDir;
                    } else {
                        out = targetDir.resolve(source.getFileName());
                    }
                    if (!java) {
                        if (kamelet) {
                            ExportHelper.safeCopy(source, out, true, symbolicLink);
                        } else if (jkube) {
                            // file should be renamed and moved into src/main/jkube
                            f = f.replace(".jkube.yaml", ".yaml");
                            f = f.replace(".jkube.yml", ".yml");
                            out = srcCamelResourcesDir.getParent().getParent().resolve("jkube/" + f);
                            Files.createDirectories(out.getParent());
                            ExportHelper.safeCopy(source, out, true, symbolicLink);
                        } else {
                            Files.createDirectories(out.getParent());
                            ExportHelper.safeCopy(getClass().getClassLoader(), scheme, source, out, true, symbolicLink);
                        }
                    } else {
                        // need to append package name in java source file
                        List<String> lines = Files.readAllLines(source);
                        Optional<String> hasPackage = lines.stream().filter(l -> l.trim().startsWith("package ")).findFirst();
                        OutputStream fos;

                        if (hasPackage.isPresent()) {
                            String pn = determinePackageName(hasPackage.get());
                            if (pn != null) {
                                Path dir = srcJavaDirRoot.resolve(pn.replace('.', '/'));
                                Files.createDirectories(dir);
                                out = dir.resolve(source.getFileName());
                            } else {
                                throw new IOException("Cannot determine package name from source: " + source);
                            }
                        } else {
                            if (javaLiveReload) {
                                out = srcJavaDirRoot.resolve(source.getFileName());
                            } else {
                                if (packageName != null && !"false".equalsIgnoreCase(packageName)) {
                                    lines.add(0, "");
                                    lines.add(0, "package " + packageName + ";");
                                }
                            }
                        }
                        if (javaLiveReload) {
                            ExportHelper.safeCopy(source, out, true, symbolicLink);
                        } else {
                            fos = Files.newOutputStream(out);
                            for (String line : lines) {
                                adjustJavaSourceFileLine(line, fos);
                                fos.write(line.getBytes(StandardCharsets.UTF_8));
                                fos.write("\n".getBytes(StandardCharsets.UTF_8));
                            }
                            IOHelper.close(fos);
                        }
                    }
                }
            }
        }

        if (!skipPlugins) {
            Set<PluginExporter> exporters = PluginHelper.getActivePlugins(getMain(), repositories).values()
                    .stream()
                    .map(Plugin::getExporter)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toSet());

            for (PluginExporter exporter : exporters) {
                exporter.addSourceFiles(srcJavaDirRoot.getParent().getParent().getParent(),
                        packageName, getMain().getOut());
            }
        }
    }

    protected void adjustJavaSourceFileLine(String line, OutputStream fos) throws Exception {
        // noop
    }

    protected void copySettingsAndProfile(
            Path settings, Path profile, Path targetDir,
            Function<Properties, Object> customize)
            throws Exception {
        Properties settingsProps = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(settingsProps, settings);

        Properties profileProps = new CamelCaseOrderedProperties();
        if (Files.exists(profile)) {
            RuntimeUtil.loadProperties(profileProps, profile);
        }
        profileProps.putAll(settingsProps);
        prepareApplicationProperties(profileProps);

        for (Map.Entry<Object, Object> entry : settingsProps.entrySet()) {
            String key = entry.getKey().toString();
            boolean skip = !key.startsWith("camel.main")
                    || "camel.main.routesCompileDirectory".equals(key)
                    || "camel.main.routesReloadEnabled".equals(key);
            if (skip) {
                profileProps.remove(key);
            }
        }

        if (customize != null) {
            customize.apply(profileProps);
        }

        StringBuilder content = new StringBuilder();
        for (Map.Entry<Object, Object> entry : profileProps.entrySet()) {
            String k = entry.getKey().toString();
            String v = entry.getValue().toString();

            boolean skip = k.startsWith("camel.jbang.") || k.startsWith("jkube.");
            if (skip) {
                continue;
            }

            // files are now loaded in classpath
            v = v.replaceAll("file:", "classpath:");
            if ("camel.main.routesIncludePattern".equals(k)) {
                // camel.main.routesIncludePattern should remove all .java as we use move them to regular src/main/java
                // camel.main.routesIncludePattern should remove all file: classpath: as we copy
                // them to src/main/resources/camel where camel autoload from
                v = Arrays.stream(v.split(","))
                        .filter(n -> !n.endsWith(".java") && !n.startsWith("file:") && !n.startsWith("classpath:"))
                        .collect(Collectors.joining(","));
            }
            if (!v.isBlank()) {
                String line = applicationPropertyLine(k, v);
                if (line != null && !line.isBlank()) {
                    content.append(line).append("\n");
                }
            }
        }

        // User properties
        Properties userProps = new CamelCaseOrderedProperties();
        userProps.putAll(propertiesMap(this.applicationProperties));
        for (Map.Entry<Object, Object> entryUserProp : userProps.entrySet()) {
            String uK = entryUserProp.getKey().toString();
            String uV = entryUserProp.getValue().toString();
            String line = applicationPropertyLine(uK, uV);
            // properties from the profile are already included so skip them
            if (profileProps.get(uK) == null && ObjectHelper.isNotEmpty(line)) {
                content.append(line).append("\n");
            }
        }

        // write all the properties
        Path appPropsPath = targetDir.resolve("application.properties");
        Files.writeString(appPropsPath, content.toString(), StandardCharsets.UTF_8);
    }

    protected void prepareApplicationProperties(Properties properties) {
        // NOOP
    }

    protected Map<String, String> propertiesMap(String[]... propertySources) {
        Map<String, String> result = new LinkedHashMap<>();
        if (propertySources != null) {
            for (String[] props : Arrays.stream(propertySources).filter(Objects::nonNull).toList()) {
                for (String s : props) {
                    String[] kv = s.split("=");
                    if (kv.length != 2) {
                        // likely a user mistake, we warn the user
                        printer().println("WARN: property '" + s + "'' has a bad format (should be 'key=value'), skipping.");
                    } else {
                        result.put(kv[0], kv[1]);
                    }
                }
            }
        }
        return result;
    }

    // Returns true if it has either an openapi spec or it uses contract-first DSL
    protected boolean hasOpenapi(Set<String> dependencies) {
        return openapi != null || dependencies.stream().anyMatch(s -> s.contains("mvn:org.apache.camel:camel-rest-openapi"));
    }

    protected Properties mapBuildProperties() {
        var answer = new Properties();
        buildProperties.stream()
                .filter(item -> !item.isEmpty())
                .map(item -> item.split("="))
                .forEach(toks -> answer.setProperty(toks[0], toks[1]));
        return answer;
    }

    protected void copyMavenWrapper() throws Exception {
        Path wrapperPath = Paths.get(BUILD_DIR, ".mvn/wrapper");
        Files.createDirectories(wrapperPath);
        // copy files
        Path mvnwPath = Paths.get(BUILD_DIR, "mvnw");
        Path mvnwCmdPath = Paths.get(BUILD_DIR, "mvnw.cmd");
        Path wrapperJarPath = wrapperPath.resolve("maven-wrapper.jar");
        Path wrapperPropsPath = wrapperPath.resolve("maven-wrapper.properties");

        try (InputStream is = ExportBaseCommand.class.getClassLoader().getResourceAsStream("maven-wrapper/mvnw")) {
            Files.copy(is, mvnwPath, StandardCopyOption.REPLACE_EXISTING);
        }
        try (InputStream is = ExportBaseCommand.class.getClassLoader().getResourceAsStream("maven-wrapper/mvnw.cmd")) {
            Files.copy(is, mvnwCmdPath, StandardCopyOption.REPLACE_EXISTING);
        }
        try (InputStream is = ExportBaseCommand.class.getClassLoader().getResourceAsStream("maven-wrapper/maven-wrapper.jar")) {
            Files.copy(is, wrapperJarPath, StandardCopyOption.REPLACE_EXISTING);
        }
        try (InputStream is
                = ExportBaseCommand.class.getClassLoader().getResourceAsStream("maven-wrapper/maven-wrapper.properties")) {
            Files.copy(is, wrapperPropsPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // set execute file permission on mvnw/mvnw.cmd files
        FileUtil.setPosixFilePermissions(mvnwPath, "rwxr-xr-x");
        FileUtil.setPosixFilePermissions(mvnwCmdPath, "rwxr-xr-x");
    }

    protected void copyGradleWrapper() throws Exception {
        Path wrapperPath = Paths.get(BUILD_DIR, "gradle/wrapper");
        Files.createDirectories(wrapperPath);
        // copy files
        Path gradlewPath = Paths.get(BUILD_DIR, "gradlew");
        Path gradlewBatPath = Paths.get(BUILD_DIR, "gradlew.bat");
        Path wrapperJarPath = wrapperPath.resolve("gradle-wrapper.jar");
        Path wrapperPropsPath = wrapperPath.resolve("gradle-wrapper.properties");

        try (InputStream is = ExportBaseCommand.class.getClassLoader().getResourceAsStream("gradle-wrapper/gradlew")) {
            Files.copy(is, gradlewPath, StandardCopyOption.REPLACE_EXISTING);
        }
        try (InputStream is = ExportBaseCommand.class.getClassLoader().getResourceAsStream("gradle-wrapper/gradlew.bat")) {
            Files.copy(is, gradlewBatPath, StandardCopyOption.REPLACE_EXISTING);
        }
        try (InputStream is
                = ExportBaseCommand.class.getClassLoader().getResourceAsStream("gradle-wrapper/gradle-wrapper.jar")) {
            Files.copy(is, wrapperJarPath, StandardCopyOption.REPLACE_EXISTING);
        }
        try (InputStream is
                = ExportBaseCommand.class.getClassLoader().getResourceAsStream("gradle-wrapper/gradle-wrapper.properties")) {
            Files.copy(is, wrapperPropsPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // set execute file permission on gradlew/gradlew.bat files
        FileUtil.setPosixFilePermissions(gradlewPath, "rwxr-xr-x");
        FileUtil.setPosixFilePermissions(gradlewBatPath, "rwxr-xr-x");
    }

    protected String applicationPropertyLine(String key, String value) {
        return key + "=" + value;
    }

    /**
     * Gets the maven repositories
     *
     * @param  prop         settings
     * @param  camelVersion the camel version
     * @return              repositories or null if none are in use
     */
    protected String getMavenRepositories(Path settings, Properties prop, String camelVersion) throws Exception {
        Set<String> answer = new LinkedHashSet<>();

        String propRepositories = prop.getProperty(REPOS);
        if (propRepositories != null) {
            answer.add(propRepositories);
        }

        if (camelVersion == null) {
            camelVersion = new DefaultCamelCatalog().getCatalogVersion();
        }
        // include apache snapshot repo if we use SNAPSHOT version of Camel
        if (camelVersion.endsWith("-SNAPSHOT")) {
            answer.add("https://repository.apache.org/content/groups/snapshots/");
        }

        // there may be additional extra repositories
        List<String> lines = RuntimeUtil.loadPropertiesLines(settings);
        for (String line : lines) {
            if (line.startsWith("repository=")) {
                String r = StringHelper.after(line, "repository=");
                answer.add(r);
            }
        }

        if (repositories != null) {
            Collections.addAll(answer, this.repositories.split(","));
        }

        return answer.stream()
                .filter(item -> !item.isEmpty())
                .collect(Collectors.joining(","));
    }

    protected static boolean hasModeline(Path settings) {
        try {
            List<String> lines = RuntimeUtil.loadPropertiesLines(settings);
            return lines.stream().anyMatch(l -> l.startsWith("modeline="));
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    protected static int httpServerPort(Path settings) {
        try {
            List<String> lines = RuntimeUtil.loadPropertiesLines(settings);
            String port = lines.stream().filter(l -> l.startsWith("camel.server.port="))
                    .map(s -> StringHelper.after(s, "=")).findFirst().orElse("-1");
            return Integer.parseInt(port);
        } catch (Exception e) {
            // ignore
        }
        return -1;
    }

    protected static int httpManagementPort(Path settings) {
        try {
            List<String> lines = RuntimeUtil.loadPropertiesLines(settings);
            String port = lines.stream().filter(l -> l.startsWith("camel.management.port="))
                    .map(s -> StringHelper.after(s, "=")).findFirst().orElse("-1");
            return Integer.parseInt(port);
        } catch (Exception e) {
            // ignore
        }
        return -1;
    }

    protected static String jibMavenPluginVersion(Path settings, Properties prop) {
        String answer = null;
        if (prop != null) {
            answer = prop.getProperty(JIB_MAVEN_PLUGIN_VERSION);
        }
        if (answer == null) {
            try {
                List<String> lines = RuntimeUtil.loadPropertiesLines(settings);
                answer = lines.stream().filter(l -> l.startsWith(JIB_MAVEN_PLUGIN_VERSION + "="))
                        .map(s -> StringHelper.after(s, "=")).findFirst().orElse(null);
            } catch (Exception e) {
                // ignore
            }
        }
        return answer != null ? answer : "3.4.5";
    }

    protected static String jkubeMavenPluginVersion(Path settings, Properties props) {
        String answer = null;
        if (props != null) {
            answer = props.getProperty(JKUBE_MAVEN_PLUGIN_VERSION);
        }
        if (answer == null) {
            try {
                List<String> lines = RuntimeUtil.loadPropertiesLines(settings);
                answer = lines.stream()
                        .filter(l -> l.startsWith(JKUBE_MAVEN_PLUGIN_VERSION + "=") || l.startsWith("jkube.version="))
                        .map(s -> StringHelper.after(s, "=")).findFirst().orElse(null);
            } catch (Exception e) {
                // ignore
            }
        }
        return answer != null ? answer : "1.18.1";
    }

    // This method is kept for backward compatibility with derived classes
    @Deprecated
    protected void safeCopy(java.io.File source, java.io.File target, boolean override) throws Exception {
        ExportHelper.safeCopy(source.toPath(), target.toPath(), override, symbolicLink);
    }

    // This method is kept for backward compatibility with derived classes
    @Deprecated
    protected void safeCopy(InputStream source, java.io.File target) throws Exception {
        ExportHelper.safeCopy(source, target.toPath());
    }

    private static String determinePackageName(String content) {
        final Matcher matcher = PACKAGE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Normalize dependency expression. Basically replaces "camel-" based artifact names to use proper "camel:" prefix.
     *
     * @param  dependency to normalize.
     * @return            normalized dependency.
     */
    private static String normalizeDependency(String dependency) {
        if (dependency.startsWith("camel-quarkus-")) {
            return "camel:" + dependency.substring("camel-quarkus-".length());
        }

        if (dependency.startsWith("camel-quarkus:")) {
            return "camel:" + dependency.substring("camel-quarkus:".length());
        }

        if (dependency.startsWith("camel-")) {
            return "camel:" + dependency.substring("camel-".length());
        }

        return dependency;
    }

    protected MavenGav parseMavenGav(String dep) {
        MavenGav gav;
        if (dep.startsWith("lib:") && dep.endsWith(".jar")) {
            // lib:commons-lang3-3.12.0.jar
            String n = dep.substring(4);
            n = n.substring(0, n.length() - 4);
            // scan inside JAR in META-INF/maven and find pom.properties file
            gav = parseLocalJar(n);
            if (gav == null) {
                // okay JAR was not maven build
                gav = new MavenGav();
                String v = "1.0";
                String a = n;
                int pos = n.lastIndexOf("-");
                if (pos != -1) {
                    a = n.substring(0, pos);
                    v = n.substring(pos + 1);
                }
                gav.setGroupId("local");
                gav.setArtifactId(a);
                gav.setVersion(v);
                gav.setPackaging("lib");
            }
        } else {
            gav = MavenGav.parseGav(dep);
        }
        return gav;
    }

    private MavenGav parseLocalJar(String dep) {
        Path path = exportBaseDir.resolve(dep + ".jar");
        if (!Files.isRegularFile(path) || !Files.exists(path)) {
            return null;
        }

        try (JarFile jf = new JarFile(path.toFile())) {
            Optional<JarEntry> je = jf.stream().filter(e -> e.getName().startsWith("META-INF/maven/")
                    && e.getName().endsWith("/pom.properties")).findFirst();
            if (je.isPresent()) {
                JarEntry e = je.get();
                try (InputStream is = jf.getInputStream(e)) {
                    Properties prop = new Properties();
                    prop.load(is);
                    MavenGav gav = new MavenGav();
                    gav.setGroupId(prop.getProperty("groupId"));
                    gav.setArtifactId(prop.getProperty("artifactId"));
                    gav.setVersion(prop.getProperty("version"));
                    gav.setPackaging("lib");
                    return gav;
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return null;
    }

    protected void copyLocalLibDependencies(Set<String> deps) throws Exception {
        for (String d : deps) {
            if (d.startsWith("lib:")) {
                Path libDirPath = Paths.get(BUILD_DIR, "lib");
                Files.createDirectories(libDirPath);
                String n = d.substring(4);
                Path sourcePath = Paths.get(n);
                Path targetPath = libDirPath.resolve(n);
                ExportHelper.safeCopy(sourcePath, targetPath, true, symbolicLink);
            }
        }
    }

    protected void copyAgentDependencies(Set<String> deps) throws Exception {
        for (String d : deps) {
            if (d.startsWith("agent:")) {
                Path libDirPath = Paths.get(BUILD_DIR, "agent");
                Files.createDirectories(libDirPath);
                String n = d.substring(6);
                MavenGav gav = MavenGav.parseGav(n);
                copyAgentLibDependencies(gav);
            }
        }
    }

    private void copyAgentLibDependencies(MavenGav gav) {
        try {
            List<MavenArtifact> artifacts = getDownloader().resolveArtifacts(
                    List.of(gav.toString()), Set.of(), true, gav.getVersion().contains("SNAPSHOT"));
            for (MavenArtifact artifact : artifacts) {
                Path target = Paths.get(BUILD_DIR, "agent", artifact.getFile().getName());
                if (Files.exists(target) || EXCLUDED_GROUP_IDS.contains(artifact.getGav().getGroupId())) {
                    continue;
                }
                Files.copy(artifact.getFile().toPath(), target);
            }
        } catch (MavenResolutionException e) {
            printer().printErr("Error resolving the artifact: " + gav + " due to: " + e.getMessage());
        } catch (IOException e) {
            printer().printErr("Error copying the artifact: " + gav + " due to: " + e.getMessage());
        }
    }

    protected void copyApplicationPropertiesFiles(Path srcResourcesDir) throws Exception {
        try (Stream<Path> files = Files.list(exportBaseDir)) {
            files.filter(p -> Files.isRegularFile(p))
                    .filter(p -> {
                        String fileName = p.getFileName().toString();
                        String ext = FileUtil.onlyExt(fileName);
                        String name = FileUtil.onlyName(fileName);
                        if (!"properties".equals(ext)) {
                            return false;
                        }
                        if (name.equals("application")) {
                            // skip generic as its handled specially
                            return false;
                        }
                        if (profile == null) {
                            // accept all kind of configuration files
                            return name.startsWith("application");
                        } else {
                            // only accept the configuration file that matches the profile
                            return name.equals("application-" + profile);
                        }
                    })
                    .forEach(p -> {
                        try {
                            ExportHelper.safeCopy(p, srcResourcesDir.resolve(p.getFileName()), true, symbolicLink);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            printer().printErr("Error copying application properties due to: " + e.getMessage());
        }
    }

    // This method is kept for backward compatibility with derived classes
    @Deprecated
    protected void copyApplicationPropertiesFiles(java.io.File srcResourcesDir) throws Exception {
        copyApplicationPropertiesFiles(srcResourcesDir.toPath());
    }

    private MavenDownloader getDownloader() {
        if (downloader == null) {
            init();
        }
        return downloader;
    }

    private void init() {
        this.downloader = new MavenDownloaderImpl();
        ((MavenDownloaderImpl) downloader).build();
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
