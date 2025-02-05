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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.catalog.KameletCatalogHelper;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
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
import org.apache.camel.util.StringHelper;
import picocli.CommandLine;

public abstract class ExportBaseCommand extends CamelCommand {

    protected static final String BUILD_DIR = CommandLineHelper.CAMEL_JBANG_WORK_DIR + "/work";

    protected static final String[] SETTINGS_PROP_SOURCE_KEYS = new String[] {
            "camel.main.routesIncludePattern",
            "camel.component.properties.location",
            "camel.component.kamelet.location",
            "camel.jbang.classpathFiles",
            "camel.jbang.localKameletDir",
            "camel.jbang.jkubeFiles",
            "kamelet"
    };

    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "^\\s*package\\s+([a-zA-Z][.\\w]*)\\s*;.*$", Pattern.MULTILINE);

    private static final Set<String> EXCLUDED_GROUP_IDS = Set.of("org.fusesource.jansi", "org.apache.logging.log4j");

    private MavenDownloader downloader;

    private Printer quietPrinter;

    @CommandLine.Parameters(description = "The Camel file(s) to export. If no files is specified then what was last run will be exported.",
                            arity = "0..9", paramLabel = "<files>", parameterConsumer = FilesConsumer.class)
    protected Path[] filePaths; // Defined only for file path completion; the field never used

    protected List<String> files = new ArrayList<>();

    @CommandLine.Option(names = { "--repos" },
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

    @CommandLine.Option(names = { "--maven-central-enabled" },
                        description = "Whether downloading JARs from Maven Central repository is enabled")
    protected boolean mavenCentralEnabled = true;

    @CommandLine.Option(names = { "--maven-apache-snapshot-enabled" },
                        description = "Whether downloading JARs from ASF Maven Snapshot repository is enabled")
    protected boolean mavenApacheSnapshotEnabled = true;

    @CommandLine.Option(names = { "--main-classname" },
                        description = "The class name of the Camel Main application class",
                        defaultValue = "CamelApplication")
    protected String mainClassname = "CamelApplication";

    @CommandLine.Option(names = { "--java-version" }, description = "Java version", defaultValue = "17")
    protected String javaVersion = "17";

    @CommandLine.Option(names = { "--camel-version" },
                        description = "To export using a different Camel version than the default version.")
    protected String camelVersion;

    @CommandLine.Option(names = {
            "--kamelets-version" }, description = "Apache Camel Kamelets version")
    protected String kameletsVersion;

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

    @CommandLine.Option(names = {
            "--dir",
            "--directory" }, description = "Directory where the project will be exported", defaultValue = ".")
    protected String exportDir;

    @CommandLine.Option(names = { "--clean-dir" },
                        description = "If exporting to current directory (default) then all existing files are preserved. Enabling this option will force cleaning current directory including all sub dirs (use this with care)")
    protected boolean cleanExportDir;

    @CommandLine.Option(names = { "--logging-level" }, defaultValue = "info", description = "Logging level")
    protected String loggingLevel = "info";

    @CommandLine.Option(names = { "--package-name" },
                        description = "For Java source files should they have the given package name. By default the package name is computed from the Maven GAV. "
                                      + "Use false to turn off and not include package name in the Java source files.")
    protected String packageName;

    @CommandLine.Option(names = { "--fresh" }, description = "Make sure we use fresh (i.e. non-cached) resources")
    protected boolean fresh;

    @CommandLine.Option(names = { "--download" }, defaultValue = "true",
                        description = "Whether to allow automatic downloading JAR dependencies (over the internet)")
    protected boolean download = true;

    @CommandLine.Option(names = { "--build-property" },
                        description = "Maven/Gradle build properties, ex. --build-property=prop1=foo")
    protected List<String> buildProperties = new ArrayList<>();

    @CommandLine.Option(names = { "--property" },
                        description = "Camel application properties, ex. --property=prop1=foo")
    protected String[] applicationProperties;

    @CommandLine.Option(names = { "--logging" }, defaultValue = "false",
                        description = "Can be used to turn on logging (logs to file in <user home>/.camel directory)")
    protected boolean logging;

    @CommandLine.Option(names = { "--quiet" }, defaultValue = "false",
                        description = "Will be quiet, only print when error occurs")
    protected boolean quiet;

    @CommandLine.Option(names = { "--ignore-loading-error" },
                        description = "Whether to ignore route loading and compilation errors (use this with care!)")
    protected boolean ignoreLoadingError;

    @CommandLine.Option(names = { "--lazy-bean" },
                        description = "Whether to use lazy bean initialization (can help with complex classloading issues")
    protected boolean lazyBean;

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
            RuntimeUtil.configureLog(loggingLevel, false, false, false, true, null, null);
        } else {
            RuntimeUtil.configureLog("off", false, false, false, true, null, null);
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

    protected Integer runSilently(boolean ignoreLoadingError, boolean lazyBean) throws Exception {
        Run run = new Run(getMain());
        // need to declare the profile to use for run
        run.dependencies = dependencies;
        run.files = files;
        run.excludes = excludes;
        run.openapi = openapi;
        run.download = download;
        run.runtime = runtime;
        run.camelVersion = camelVersion;
        run.camelSpringBootVersion = camelSpringBootVersion;
        run.quarkusVersion = quarkusVersion;
        run.springBootVersion = springBootVersion;
        run.kameletsVersion = kameletsVersion;
        run.localKameletDir = localKameletDir;
        run.ignoreLoadingError = ignoreLoadingError;
        run.lazyBean = lazyBean;
        run.property = applicationProperties;
        run.repositories = repositories;
        run.logging = false;
        run.loggingLevel = "off";

        return run.runExport(ignoreLoadingError);
    }

    protected void addDependencies(String... deps) {
        var depsArray = Optional.ofNullable(deps).orElse(new String[0]);
        dependencies.addAll(Arrays.asList(depsArray));
    }

    protected String replaceBuildProperties(String context) {
        String properties = buildProperties.stream()
                .filter(item -> !item.isEmpty())
                .map(item -> {
                    String[] keyValueProperty = item.split("=");
                    return String.format("        <%s>%s</%s>", keyValueProperty[0], keyValueProperty[1],
                            keyValueProperty[0]);
                })
                .collect(Collectors.joining(System.lineSeparator()));
        if (!properties.isEmpty()) {
            context = context.replaceFirst(Pattern.quote("{{ .BuildProperties }}"), Matcher.quoteReplacement(properties));
        } else {
            context = context.replaceFirst(Pattern.quote("{{ .BuildProperties }}"), "");
        }
        return context;
    }

    protected Set<String> resolveDependencies(File settings, File profile) throws Exception {
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
        List<String> officialKamelets = KameletCatalogHelper.findKameletNames(kameletsVersion);
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
                        || !(kamelets) && v.contains("org.apache.camel:camel-kamelet");
                if (!skip) {
                    answer.add(v);
                }
                if (kamelets && v != null && v.contains("org.apache.camel:camel-kamelet")) {
                    // kamelets need yaml-dsl
                    answer.add("camel:yaml-dsl");
                    if (asfKamelets) {
                        // include JARs for official ASF kamelets
                        answer.add("org.apache.camel.kamelets:camel-kamelets:" + kameletsVersion);
                        if (VersionHelper.compare(camelVersion, "4.10.0") < 0) {
                            answer.add("org.apache.camel.kamelets:camel-kamelets-utils:" + kameletsVersion);
                        }
                    }
                }
            } else if (line.startsWith("camel.jbang.dependencies=")) {
                String deps = StringHelper.after(line, "camel.jbang.dependencies=");
                if (!deps.isEmpty()) {
                    for (String d : deps.split(",")) {
                        answer.add(d.trim());
                        if (kamelets && d.contains("org.apache.camel:camel-kamelet")) {
                            // kamelets need yaml-dsl
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
            } else if (line.startsWith("camel.jbang.classpathFiles")) {
                String deps = StringHelper.after(line, "camel.jbang.classpathFiles=");
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
        if (profile != null && profile.exists()) {
            Properties prop = new CamelCaseOrderedProperties();
            RuntimeUtil.loadProperties(prop, profile);
            for (String d : RuntimeUtil.getDependenciesAsArray(prop)) {
                answer.add(d.trim());
            }
            // automatic add needed dependencies when dev-console is enabled
            if ("true".equalsIgnoreCase(prop.getProperty("camel.main.devConsoleEnabled"))
                    || "true".equalsIgnoreCase(prop.getProperty("camel.server.devConsoleEnabled"))) {
                answer.add("camel:console");
                answer.add("camel:management");
            }
            // automatic add needed dependencies when main server enabled plugins
            if ("true".equalsIgnoreCase(prop.getProperty("camel.server.jolokiaEnabled"))) {
                answer.add("camel:platform-http-jolokia");
            }
            if ("true".equalsIgnoreCase(prop.getProperty("camel.server.metricsEnabled"))) {
                answer.add("camel:micrometer-prometheus");
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

    protected void copySourceFiles(
            File settings, File profile, File srcJavaDirRoot, File srcJavaDir, File srcResourcesDir, File srcCamelResourcesDir,
            File srcKameletsResourcesDir, String packageName)
            throws Exception {
        // read the settings file and find the files to copy
        Properties prop = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(prop, settings);

        String localKameletDir = prop.getProperty("camel.jbang.localKameletDir");
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
                    boolean skip = profile.getName().equals(f); // skip copying profile
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
                            || "camel.jbang.localKameletDir".equals(k) || "kamelet.yaml".equalsIgnoreCase(ext2);
                    boolean camel = !kamelet && "camel.main.routesIncludePattern".equals(k);
                    boolean jkube = "camel.jbang.jkubeFiles".equals(k);
                    boolean web = "html".equals(ext) || "js".equals(ext) || "css".equals(ext) || "jpeg".equals(ext)
                            || "jpg".equals(ext) || "png".equals(ext) || "ico".equals(ext);
                    File srcWeb = new File(srcResourcesDir, "META-INF/resources");
                    File targetDir = java ? srcJavaDir : camel ? srcCamelResourcesDir : kamelet ? srcKameletsResourcesDir
                            : web ? srcWeb : srcResourcesDir;
                    targetDir.mkdirs();

                    File source;
                    if ("kamelet".equals(k) && localKameletDir != null) {
                        // source is a local kamelet
                        source = new File(localKameletDir, f + ".kamelet.yaml");
                    } else {
                        source = new File(f);
                    }
                    File out;
                    if (source.isDirectory()) {
                        out = targetDir;
                    } else {
                        out = new File(targetDir, source.getName());
                    }
                    if (!java) {
                        if (kamelet) {
                            safeCopy(source, out, true);
                        } else if (jkube) {
                            // file should be renamed and moved into src/main/jkube
                            f = f.replace(".jkube.yaml", ".yaml");
                            f = f.replace(".jkube.yml", ".yml");
                            out = new File(srcCamelResourcesDir.getParentFile().getParentFile(), "jkube/" + f);
                            out.getParentFile().mkdirs();
                            safeCopy(source, out, true);
                        } else {
                            out.getParentFile().mkdirs();
                            safeCopy(source, out, true);
                        }
                    } else {
                        // need to append package name in java source file
                        List<String> lines = Files.readAllLines(source.toPath());
                        Optional<String> hasPackage = lines.stream().filter(l -> l.trim().startsWith("package ")).findFirst();
                        FileOutputStream fos;

                        if (hasPackage.isPresent()) {
                            String pn = determinePackageName(hasPackage.get());
                            if (pn != null) {
                                File dir = new File(srcJavaDirRoot, pn.replace('.', File.separatorChar));
                                dir.mkdirs();
                                out = new File(dir, source.getName());
                            } else {
                                throw new IOException("Cannot determine package name from source: " + source);
                            }
                        } else {
                            if (javaLiveReload) {
                                out = new File(srcJavaDirRoot, source.getName());
                            } else {
                                if (packageName != null && !"false".equalsIgnoreCase(packageName)) {
                                    lines.add(0, "");
                                    lines.add(0, "package " + packageName + ";");
                                }
                            }
                        }
                        if (javaLiveReload) {
                            safeCopy(source, out, true);
                        } else {
                            fos = new FileOutputStream(out);
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
    }

    protected void adjustJavaSourceFileLine(String line, FileOutputStream fos) throws Exception {
        // noop
    }

    protected String exportPackageName(String groupId, String artifactId, String packageName) {
        if ("false".equalsIgnoreCase(packageName)) {
            return null; // package names are turned off (we should use root package)
        }
        if (packageName != null) {
            return packageName; // use specific package name
        }

        // compute package name based on Maven GAV
        // for package name it must be in lower-case and alpha/numeric
        String s = groupId + "." + artifactId;
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '.' || Character.isAlphabetic(ch) || Character.isDigit(ch)) {
                ch = Character.toLowerCase(ch);
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    protected void copySettingsAndProfile(
            File settings, File profile, File targetDir,
            Function<Properties, Object> customize)
            throws Exception {
        Properties prop = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(prop, settings);

        Properties prop2 = new CamelCaseOrderedProperties();
        if (profile.exists()) {
            RuntimeUtil.loadProperties(prop2, profile);
        }
        prop2.putAll(prop);
        prepareApplicationProperties(prop2);

        for (Map.Entry<Object, Object> entry : prop.entrySet()) {
            String key = entry.getKey().toString();
            boolean skip = !key.startsWith("camel.main")
                    || "camel.main.routesCompileDirectory".equals(key)
                    || "camel.main.routesReloadEnabled".equals(key);
            if (skip) {
                prop2.remove(key);
            }
        }

        if (customize != null) {
            customize.apply(prop2);
        }

        // User properties
        Properties prop3 = new CamelCaseOrderedProperties();
        prepareUserProperties(prop3);

        FileOutputStream fos = new FileOutputStream(new File(targetDir, "application.properties"), false);
        try {
            for (Map.Entry<Object, Object> entry : prop2.entrySet()) {
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
                        fos.write(line.getBytes(StandardCharsets.UTF_8));
                        fos.write("\n".getBytes(StandardCharsets.UTF_8));
                    }
                }
                for (Map.Entry<Object, Object> entryUserProp : prop3.entrySet()) {
                    String uK = entryUserProp.getKey().toString();
                    String uV = entryUserProp.getValue().toString();
                    String line = applicationPropertyLine(uK, uV);
                    if (line != null && !line.isBlank()) {
                        fos.write(line.getBytes(StandardCharsets.UTF_8));
                        fos.write("\n".getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        } finally {
            IOHelper.close(fos);
        }
    }

    protected void prepareApplicationProperties(Properties properties) {
        // NOOP
    }

    protected void prepareUserProperties(Properties properties) {
        if (this.applicationProperties != null) {
            for (String s : this.applicationProperties) {
                String[] kv = s.split("=");
                if (kv.length != 2) {
                    // likely a user mistake, we warn the user
                    printer().println("WARN: property '" + s + "'' has a bad format (should be 'key=value'), skipping.");
                } else {
                    properties.put(kv[0], kv[1]);
                }
            }
        }
    }

    // Returns true if it has either an openapi spec or it uses contract-first DSL
    protected boolean hasOpenapi(Set<String> dependencies) {
        return openapi != null || dependencies.stream().anyMatch(s -> s.contains("mvn:org.apache.camel:camel-rest-openapi"));
    }

    protected Properties mapBuildProperties() {
        var answer = new Properties();
        buildProperties.stream().map(item -> item.split("=")).forEach(toks -> answer.setProperty(toks[0], toks[1]));
        return answer;
    }

    protected void copyMavenWrapper() throws Exception {
        File wrapper = new File(BUILD_DIR, ".mvn/wrapper");
        wrapper.mkdirs();
        // copy files
        InputStream is = ExportBaseCommand.class.getClassLoader().getResourceAsStream("maven-wrapper/mvnw");
        IOHelper.copyAndCloseInput(is, new FileOutputStream(new File(BUILD_DIR, "mvnw")));
        is = ExportBaseCommand.class.getClassLoader().getResourceAsStream("maven-wrapper/mvnw.cmd");
        IOHelper.copyAndCloseInput(is, new FileOutputStream(new File(BUILD_DIR, "mvnw.cmd")));
        is = ExportBaseCommand.class.getClassLoader().getResourceAsStream("maven-wrapper/.mvn/wrapper/maven-wrapper.jar");
        IOHelper.copyAndCloseInput(is, new FileOutputStream(new File(wrapper, "maven-wrapper.jar")));
        is = ExportBaseCommand.class.getClassLoader()
                .getResourceAsStream("maven-wrapper/.mvn/wrapper/maven-wrapper.properties");
        IOHelper.copyAndCloseInput(is, new FileOutputStream(new File(wrapper, "maven-wrapper.properties")));
        // set execute file permission on mvnw/mvnw.cmd files
        File file = new File(BUILD_DIR, "mvnw");
        file.setExecutable(true);
        file = new File(BUILD_DIR, "mvnw.cmd");
        file.setExecutable(true);
    }

    protected void copyGradleWrapper() throws Exception {
        File wrapper = new File(BUILD_DIR, "gradle/wrapper");
        wrapper.mkdirs();
        // copy files
        InputStream is = ExportBaseCommand.class.getClassLoader().getResourceAsStream("gradle-wrapper/gradlew");
        IOHelper.copyAndCloseInput(is, new FileOutputStream(new File(BUILD_DIR, "gradlew")));
        is = ExportBaseCommand.class.getClassLoader().getResourceAsStream("gradle-wrapper/gradlew.bat");
        IOHelper.copyAndCloseInput(is, new FileOutputStream(new File(BUILD_DIR, "gradlew.bat")));
        is = ExportBaseCommand.class.getClassLoader().getResourceAsStream("gradle-wrapper/gradle-wrapper.jar");
        IOHelper.copyAndCloseInput(is, new FileOutputStream(new File(wrapper, "gradle-wrapper.jar")));
        is = ExportBaseCommand.class.getClassLoader().getResourceAsStream("gradle-wrapper/gradle-wrapper.properties");
        IOHelper.copyAndCloseInput(is, new FileOutputStream(new File(wrapper, "gradle-wrapper.properties")));
        // set execute file permission on gradlew/gradlew.cmd files
        File file = new File(BUILD_DIR, "gradlew");
        file.setExecutable(true);
        file = new File(BUILD_DIR, "gradlew.bat");
        file.setExecutable(true);
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
    protected String getMavenRepositories(File settings, Properties prop, String camelVersion) throws Exception {
        Set<String> answer = new LinkedHashSet<>();

        String propRepositories = prop.getProperty("camel.jbang.repositories");
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

    protected static boolean hasModeline(File settings) {
        try {
            List<String> lines = RuntimeUtil.loadPropertiesLines(settings);
            return lines.stream().anyMatch(l -> l.startsWith("modeline="));
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    protected static int httpServerPort(File settings) {
        try {
            List<String> lines = RuntimeUtil.loadPropertiesLines(settings);
            String port = lines.stream().filter(l -> l.startsWith("camel.jbang.platform-http.port="))
                    .map(s -> StringHelper.after(s, "=")).findFirst().orElse("-1");
            return Integer.parseInt(port);
        } catch (Exception e) {
            // ignore
        }
        return -1;
    }

    protected static String jibMavenPluginVersion(File settings, Properties prop) {
        String answer = null;
        if (prop != null) {
            answer = prop.getProperty("camel.jbang.jib-maven-plugin-version");
        }
        if (answer == null) {
            try {
                List<String> lines = RuntimeUtil.loadPropertiesLines(settings);
                answer = lines.stream().filter(l -> l.startsWith("camel.jbang.jib-maven-plugin-version="))
                        .map(s -> StringHelper.after(s, "=")).findFirst().orElse(null);
            } catch (Exception e) {
                // ignore
            }
        }
        return answer != null ? answer : "3.4.3";
    }

    protected static String jkubeMavenPluginVersion(File settings, Properties props) {
        String answer = null;
        if (props != null) {
            answer = props.getProperty("camel.jbang.jkube-maven-plugin-version");
        }
        if (answer == null) {
            try {
                List<String> lines = RuntimeUtil.loadPropertiesLines(settings);
                answer = lines.stream()
                        .filter(l -> l.startsWith("camel.jbang.jkube-maven-plugin-version=") || l.startsWith("jkube.version="))
                        .map(s -> StringHelper.after(s, "=")).findFirst().orElse(null);
            } catch (Exception e) {
                // ignore
            }
        }
        return answer != null ? answer : "1.18.0";
    }

    protected void safeCopy(File source, File target, boolean override) throws Exception {
        if (!source.exists()) {
            return;
        }

        if (source.isDirectory()) {
            // flatten files if they are from a directory
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isFile()) {
                        safeCopy(child, new File(target, child.getName()), override);
                    }
                }
            }
            return;
        }

        if (symbolicLink) {
            try {
                // must use absolute paths
                Path link = target.toPath().toAbsolutePath();
                Path src = source.toPath().toAbsolutePath();
                if (Files.exists(link)) {
                    Files.delete(link);
                }
                Files.createSymbolicLink(link, src);
                return; // success
            } catch (IOException e) {
                // ignore
            }
        }

        if (!target.exists()) {
            Files.copy(source.toPath(), target.toPath());
        } else if (override) {
            Files.copy(source.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    protected void safeCopy(InputStream source, File target) throws Exception {
        if (source == null) {
            return;
        }

        File dir = target.getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        if (!target.exists()) {
            Files.copy(source, target.toPath());
        }
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

    protected static MavenGav parseMavenGav(String dep) {
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

    private static MavenGav parseLocalJar(String dep) {
        File file = new File(dep + ".jar");
        if (!file.isFile() || !file.exists()) {
            return null;
        }

        try {
            JarFile jf = new JarFile(file);
            Optional<JarEntry> je = jf.stream().filter(e -> e.getName().startsWith("META-INF/maven/")
                    && e.getName().endsWith("/pom.properties")).findFirst();
            if (je.isPresent()) {
                JarEntry e = je.get();
                InputStream is = jf.getInputStream(e);
                Properties prop = new Properties();
                prop.load(is);
                IOHelper.close(is);
                MavenGav gav = new MavenGav();
                gav.setGroupId(prop.getProperty("groupId"));
                gav.setArtifactId(prop.getProperty("artifactId"));
                gav.setVersion(prop.getProperty("version"));
                gav.setPackaging("lib");
                return gav;
            }
        } catch (Exception e) {
            // ignore
        }

        return null;
    }

    protected void copyLocalLibDependencies(Set<String> deps) throws Exception {
        for (String d : deps) {
            if (d.startsWith("lib:")) {
                File libDir = new File(BUILD_DIR, "lib");
                libDir.mkdirs();
                String n = d.substring(4);
                File source = new File(n);
                File target = new File(libDir, n);
                safeCopy(source, target, true);
            }
        }
    }

    protected void copyAgentDependencies(Set<String> deps) throws Exception {
        for (String d : deps) {
            if (d.startsWith("agent:")) {
                File libDir = new File(BUILD_DIR, "agent");
                libDir.mkdirs();
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
            System.err.println("Error resolving the artifact: " + gav + " due to: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error copying the artifact: " + gav + " due to: " + e.getMessage());
        }
    }

    protected void copyApplicationPropertiesFiles(File srcResourcesDir) throws Exception {
        File[] files = new File(".").listFiles(f -> {
            if (!f.isFile()) {
                return false;
            }
            String ext = FileUtil.onlyExt(f.getName());
            String name = FileUtil.onlyName(f.getName());
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
        });
        if (files != null) {
            for (File f : files) {
                safeCopy(f, new File(srcResourcesDir, f.getName()), true);
            }
        }
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
