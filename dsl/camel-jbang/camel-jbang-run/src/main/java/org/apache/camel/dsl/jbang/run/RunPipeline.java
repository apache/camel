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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.run.common.ExportHelper;
import org.apache.camel.dsl.jbang.run.common.GenAiDependencyHelper;
import org.apache.camel.dsl.jbang.run.common.MavenProjectHelper;
import org.apache.camel.dsl.jbang.run.common.PluginExporter;
import org.apache.camel.dsl.jbang.run.common.Printer;
import org.apache.camel.dsl.jbang.run.common.QuarkusHelper.QuarkusPlatformBom;
import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.camel.dsl.jbang.run.common.RuntimeUtil;
import org.apache.camel.dsl.jbang.run.common.TemplateHelper;
import org.apache.camel.main.BaseMainSupport;
import org.apache.camel.main.KameletMain;
import org.apache.camel.main.SimpleMainShutdownStrategy;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.dsl.jbang.run.common.CamelJBangConstants.*;
import static org.apache.camel.dsl.jbang.run.common.GistHelper.asGistSingleUrl;
import static org.apache.camel.dsl.jbang.run.common.GistHelper.fetchGistUrls;
import static org.apache.camel.dsl.jbang.run.common.GitHubHelper.asGithubSingleUrl;
import static org.apache.camel.dsl.jbang.run.common.GitHubHelper.fetchGithubUrls;

/**
 * The settings pipeline of camel run: configures the {@link KameletMain} of a run from a {@link RunRequest}, and writes
 * the run settings, which an export reads. It sorts the files into routes, properties, classpath files, scripts, Groovy
 * sources, TLS, JKube and readme files, and Kamelets, and adds the dependencies of the properties files, the plugins
 * and the options.
 * <p>
 * What only the command line does arrives as hooks: where to print, the active plugins, the initial properties of a
 * command, loading a file from the clipboard, and the warning for a data directory without a source directory.
 *
 * @since 4.24
 */
public class RunPipeline {

    /**
     * The type of the Camel instance of a run.
     */
    public static final String CAMEL_INSTANCE_TYPE = "camel.jbang";

    /**
     * The exit code when a file or the source directory to run does not exist. The same value as CliExitCode.NOT_FOUND
     * of camel-jbang-core, which this module cannot depend on: keep the two in sync.
     */
    public static final int FILE_NOT_FOUND = 3;

    /**
     * The template for running camel-jbang in docker containers: as the first file to run, the files are still
     * auto-detected.
     */
    public static final String RUN_JAVA_SH = "classpath:templates/run-java.sh";

    private static final String OPENAPI_GENERATED_FILE = "generated-openapi.yaml";

    /**
     * The folders of a project layout, whose name says nothing about the app (CAMEL-25830).
     */
    private static final Set<String> LAYOUT_FOLDERS = Set.of("src", "main", "resources", "java", "camel", "routes");

    /**
     * Loads a file that is not on the file system, such as from the clipboard, and answers the file to run instead.
     */
    @FunctionalInterface
    public interface FileLoader {
        String load(String file) throws Exception;
    }

    /**
     * The active plugins: they customize the run, and answer their exporters.
     */
    @FunctionalInterface
    public interface Plugins {
        Collection<PluginExporter> beforeRun(KameletMain main, List<String> files) throws Exception;
    }

    private final RunRequest request;
    private final RunSettings settings;
    private Printer printer = new Printer.SystemOutPrinter();
    private Plugins plugins;
    private Consumer<KameletMain> initialProperties = main -> {
    };
    private FileLoader clipboard = file -> file;
    private Runnable dataDirWithoutSourceDir = () -> {
    };

    public RunPipeline(RunRequest request) {
        this.request = request;
        this.settings = new RunSettings(request.workDir);
    }

    public RunSettings getSettings() {
        return settings;
    }

    /**
     * Where the pipeline prints (default the console).
     */
    public RunPipeline withPrinter(Printer printer) {
        this.printer = printer;
        return this;
    }

    /**
     * The active plugins (default none).
     */
    public RunPipeline withPlugins(Plugins plugins) {
        this.plugins = plugins;
        return this;
    }

    /**
     * Adds initial properties of the command to Camel Main, after the ones of the kind of run.
     */
    public RunPipeline withInitialProperties(Consumer<KameletMain> initialProperties) {
        this.initialProperties = initialProperties;
        return this;
    }

    /**
     * Loads a file starting with clipboard that does not exist (default none).
     */
    public RunPipeline withClipboard(FileLoader clipboard) {
        this.clipboard = clipboard;
        return this;
    }

    /**
     * Warns that the data directory has no effect without a source directory, in dev mode.
     */
    public RunPipeline withDataDirWithoutSourceDir(Runnable warning) {
        this.dataDirWithoutSourceDir = warning;
        return this;
    }

    /**
     * The Rest DSL file generated from the OpenAPI spec, in the given work directory.
     */
    public static String openApiGeneratedFile(Path workDir) {
        return workDir + "/" + OPENAPI_GENERATED_FILE;
    }

    /**
     * Generates the Rest DSL file of the OpenAPI spec in the work directory, and adds the spec to the files, as it must
     * be on the classpath.
     */
    public static void generateOpenApi(String openapi, Path workDir, List<String> files) throws Exception {
        Path filePath = Paths.get(openapi);
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new FileNotFoundException("Cannot find file: " + filePath);
        }

        Map<String, Object> model = new HashMap<>();
        model.put("Spec", filePath.toString());
        String content = TemplateHelper.processTemplate("rest-dsl.yaml.ftl", model);
        Files.writeString(Paths.get(openApiGeneratedFile(workDir)), content);

        // we need to include the spec on the classpath
        files.add(openapi);
    }

    /**
     * Whether a directory given as the only file (camel run . or camel run dirName) runs as --source-dir. A Maven
     * project keeps running its files, and so do the Spring Boot and Quarkus runtimes, which export the files to run
     * them.
     */
    public static boolean runsDirectoryAsSourceDir(RuntimeType runtime, Path dir) {
        return (RuntimeType.jbang == runtime || RuntimeType.main == runtime)
                && !Files.exists(dir.resolve("pom.xml"));
    }

    /**
     * A single directory as the file to run (camel run . or camel run dirName): the directory is run as the source
     * directory (see {@link #runsDirectoryAsSourceDir(RuntimeType, Path)}), else its files are run.
     *
     * @return the directory, which is the base directory of a run in this JVM, or null when the files are not a
     *         directory
     */
    public static Path detectDirectory(RunRequest request) {
        if (request.sourceDir == null && request.files != null && request.files.size() == 1) {
            String name = FileUtil.stripTrailingSeparator(request.files.get(0));
            // no scheme
            if (name.indexOf(':') == -1) {
                Path dir = Path.of(name);
                if (Files.isDirectory(dir)) {
                    if (runsDirectoryAsSourceDir(request.runtime, dir)) {
                        // run the directory as --source-dir, so dev mode watches it: changed and new files are reloaded
                        request.sourceDir = name;
                        request.files.clear();
                    } else {
                        ExportHelper.dirToFiles(name, request.files);
                    }
                    return dir;
                }
            }
        }
        return null;
    }

    /**
     * The runtime of an existing Maven project (a pom.xml as the only file to run with the jbang runtime): Spring Boot
     * or Quarkus from its pom, else Camel Main, as an existing Maven project cannot run in this JVM.
     */
    public static void detectRuntime(RunRequest request) {
        if (RuntimeType.jbang == request.runtime
                && request.files != null && request.files.size() == 1 && request.files.get(0).endsWith("pom.xml")) {
            RuntimeType detected = MavenProjectHelper.detectRuntimeFromPom(Path.of(request.files.get(0)).toAbsolutePath());
            request.runtime = detected != null ? detected : RuntimeType.main;
        }
    }

    /**
     * Why a run without files cannot run (see {@link #detectFiles(List, String, Properties, boolean)}).
     */
    public static String noFilesMessage(RunRequest request) {
        String run = "run";
        if (request.transformRun) {
            run = "transform";
        } else if (request.debugRun) {
            run = "debug";
        }
        return "Cannot " + run
               + " because application.properties file does not exist or camel.main.routesIncludePattern is not configured";
    }

    /**
     * Cleans the work directory of a run, and creates it.
     */
    public static void prepareWorkDir(Path workDir, Printer printer) {
        removeDir(workDir);
        if (!Files.exists(workDir)) {
            try {
                Files.createDirectories(workDir);
            } catch (IOException e) {
                printer.printWarn("Failed to create working directory: " + workDir.toAbsolutePath());
            }
        }
    }

    /**
     * Deletes the directory with its files; a file that cannot be deleted now is deleted when the JVM exits.
     */
    public static void removeDir(Path directory) {
        if (Files.exists(directory)) {
            try (Stream<Path> files = Files.walk(directory)) {
                files.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                // Fallback to deleteOnExit if we can't delete immediately
                                try {
                                    path.toFile().deleteOnExit();
                                } catch (Exception ex) {
                                    // Ignore
                                }
                            }
                        });
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    /**
     * Finds the files to run when none are given: the files of the source directory, else none when
     * application.properties configures the routes (camel.main.routesIncludePattern). Without either, the silent run of
     * an export runs the files of the current directory, and another run cannot run.
     *
     * @return false when there is nothing to run
     */
    public static boolean detectFiles(
            List<String> files, String sourceDir, Properties profileProperties, boolean exportRun) {
        if (sourceDir != null) {
            // silent-run then auto-detect all initial files for source-dir
            Path sourceDirPath = Paths.get(sourceDir);
            try (Stream<Path> paths = Files.list(sourceDirPath)) {
                paths.forEach(p -> files.add(sourceDirPath.resolve(p.getFileName()).toString()));
            } catch (IOException e) {
                // Ignore
            }
        } else {
            String routes
                    = profileProperties != null ? profileProperties.getProperty("camel.main.routesIncludePattern") : null;
            if (routes == null) {
                if (!exportRun) {
                    return false;
                } else {
                    // silent-run then auto-detect all files
                    try {
                        Files.list(Paths.get("."))
                                .map(p -> p.getFileName().toString())
                                .forEach(files::add);
                    } catch (IOException e) {
                        // Ignore
                    }
                }
            }
        }
        return true;
    }

    /**
     * Creates the Camel Main of the run, with the local Kamelet directories.
     */
    public KameletMain createMain() {
        KameletMain main;
        if (request.localKameletDir == null || request.localKameletDir.isEmpty()) {
            main = new KameletMain(CAMEL_INSTANCE_TYPE);
        } else {
            StringJoiner sj = new StringJoiner(",");
            String[] parts = request.localKameletDir.split(",");
            for (String part : parts) {
                // automatic map github https urls to github resolver
                if (part.startsWith("https://github.com/")) {
                    part = asGithubSingleUrl(part);
                } else if (part.startsWith("https://gist.github.com/")) {
                    part = asGistSingleUrl(part);
                }
                part = FileUtil.compactPath(part);
                if (!ResourceHelper.hasScheme(part) && !part.startsWith("github:")) {
                    part = "file:" + part;
                }
                sj.add(part);
            }
            main = new KameletMain(CAMEL_INSTANCE_TYPE, sj.toString());
            writeSettings(LOCAL_KAMELET_DIR, sj.toString());
        }
        return main;
    }

    /**
     * Configures Camel Main from the request, and writes the run settings.
     *
     * @param  main              the Camel Main of the run, from {@link #createMain()}
     * @param  profileProperties the application properties of the profile, null when none
     * @return                   0, or {@link #FILE_NOT_FOUND} when a file or the source directory does not exist
     */
    public int configure(KameletMain main, Properties profileProperties) throws Exception {
        if (request.exportRun) {
            // the silent run of an export boots and stops in this JVM, which may run many exports (camel export
            // through the Java API): the default strategy adds a JVM shutdown hook that keeps the run until the JVM exits
            main.setShutdownStrategy(new SimpleMainShutdownStrategy());
        }
        main.setProfile(request.profile);
        if (request.mavenResolver.repos() != null && !request.mavenResolver.repos().isBlank()) {
            main.setRepositories(request.mavenResolver.repos());
        }
        main.setExtraRepositories(request.mavenResolver.extraRepos());
        main.setDownload(request.mavenResolver.download());
        main.setPackageScanJars(request.packageScanJars);
        main.setFresh(request.mavenResolver.fresh());
        main.setMavenSettings(request.mavenSettings);
        main.setMavenSettingsSecurity(request.mavenSettingsSecurity);
        main.setMavenCentralEnabled(request.mavenCentralEnabled);
        main.setMavenApacheSnapshotEnabled(request.mavenApacheSnapshotEnabled);
        main.setDownloadListener(new RunDownloadListener(settings));
        main.setAppName("Apache Camel (JBang)");

        if (request.stub != null) {
            String stub = request.stub;
            if ("all".equals(stub)) {
                // stub all components only
                stub = "component:*";
            } else if ("remote".equals(stub)) {
                // stub only remote components (resolved via catalog)
                stub = "component:remote";
            }
            if (!"component:remote".equals(stub)) {
                // we need to match by wildcard, to make it easier
                StringJoiner sj = new StringJoiner(",");
                for (String n : stub.split(",")) {
                    // you can either refer to a name or a specific endpoint
                    // if there is a colon then we assume it's a specific endpoint then we should not add wildcard
                    boolean colon = n.contains(":");
                    if (!colon && !n.endsWith("*")) {
                        n = "component:" + n + "*";
                    }
                    sj.add(n);
                }
                stub = sj.toString();
            }
            request.stub = stub;
            writeSetting(main, profileProperties, STUB, stub);
            main.setStubPattern(stub);
        }

        if (request.dev) {
            writeSetting(main, profileProperties, "camel.main.routesReloadEnabled", "true");
            // allow quick shutdown during development
            writeSetting(main, profileProperties, "camel.main.shutdownTimeout", "5");
        }
        if (request.sourceDir != null) {
            writeSetting(main, profileProperties, SOURCE_DIR, request.sourceDir);
        }
        if (request.dataDir != null) {
            writeSetting(main, profileProperties, DATA_DIR, request.dataDir);
        }
        if (request.trace) {
            writeSetting(main, profileProperties, "camel.main.tracing", "true");
        }
        if (request.backlogTrace) {
            writeSetting(main, profileProperties, "camel.trace.enabled", "true");
        }
        if (request.modeline) {
            writeSetting(main, profileProperties, "camel.main.modeline", "true");
            // configure eager
            main.configure().withModeline(true);
        }
        if (request.ignoreLoadingError) {
            writeSetting(main, profileProperties, IGNORE_LOADING_ERROR, "true");
        }
        if (request.lazyBean) {
            writeSetting(main, profileProperties, LAZY_BEAN, "true");
        }
        if (request.prompt) {
            writeSetting(main, profileProperties, PROMPT, "true");
        }
        writeSetting(main, profileProperties, COMPILE_WORK_DIR, request.workDir.resolve("compile").toString());

        if (request.gav != null) {
            writeSetting(main, profileProperties, GAV, request.gav);
        }
        writeSetting(main, profileProperties, OPEN_API, request.openapi);
        if (request.mavenResolver != null) {
            request.mavenResolver.putTo((k, v) -> {
                writeSetting(main, profileProperties, k, v);
            });
        }
        writeSetting(main, profileProperties, HEALTH, request.health ? "true" : "false");
        writeSetting(main, profileProperties, METRICS, request.metrics ? "true" : "false");
        writeSetting(main, profileProperties, CONSOLE, request.console ? "true" : "false");
        writeSetting(main, profileProperties, MCP, request.mcp ? "true" : "false");
        writeSetting(main, profileProperties, OPENAPI_UI, request.openapiUi ? "true" : "false");
        writeSetting(main, profileProperties, VERBOSE, request.verbose ? "true" : "false");
        // the runtime version of Camel is what is loaded via the catalog
        writeSetting(main, profileProperties, CAMEL_VERSION, new DefaultCamelCatalog().getCatalogVersion());
        writeSetting(main, profileProperties, SPRING_BOOT_VERSION, request.springBootVersion);

        if (request.quarkusPlatform != null) {
            QuarkusPlatformBom platformBoms
                    = request.quarkusPlatform.resolve(
                            request.camelVersion,
                            request.mavenResolver.downloader()::resolveArtifact,
                            request.mavenResolver.download(),
                            request.mavenResolver.fresh());
            platformBoms.putTo((k, v) -> {
                writeSetting(main, profileProperties, k, v);
            });
        }

        writeSetting(main, profileProperties, JAVA_VERSION, request.javaVersion);

        // command line arguments
        if (request.applicationProperties != null) {
            for (String p : request.applicationProperties) {
                String k = StringHelper.before(p, "=");
                String v = StringHelper.after(p, "=");
                if (k != null && v != null) {
                    main.addArgumentProperty(k, v);
                    writeSettings(k, v);
                }
            }
        }

        if (request.exportRun) {
            if (!request.verbose) {
                main.setSilent(true);
            }
            main.addInitialProperty(EXPORT, "true");
            // enable stub in silent mode so we do not use real components
            main.setStubPattern("*");
            // do not run for very long in silent run
            main.addInitialProperty("camel.main.autoStartup", "false");
            main.addInitialProperty("camel.main.durationMaxSeconds", "-1");
        } else if (request.debugRun) {
            main.addInitialProperty(DEBUG, "true");
        } else if (request.transformRun) {
            main.setSilent(true);
            // enable stub in silent mode so we do not use real components
            main.setStubPattern("*");
            // do not run for very long in silent run
            main.addInitialProperty("camel.main.autoStartup", "false");
            main.addInitialProperty("camel.main.durationMaxSeconds", "-1");
        } else if (request.transformMessageRun) {
            // do not start any routes
            main.addInitialProperty("camel.main.autoStartup", "false");
        } else if (request.scriptRun) {
            // auto terminate if being idle
            main.addInitialProperty("camel.main.durationMaxIdleSeconds", "1");
        }
        // any custom initial property
        initialProperties.accept(main);

        writeSetting(main, profileProperties, "camel.main.durationMaxMessages",
                () -> request.maxMessages > 0
                        ? String.valueOf(request.maxMessages) : null);
        writeSetting(main, profileProperties, "camel.main.durationMaxSeconds",
                () -> request.maxSeconds > 0
                        ? String.valueOf(request.maxSeconds) : null);
        writeSetting(main, profileProperties, "camel.main.durationMaxIdleSeconds",
                () -> request.maxIdleSeconds > 0
                        ? String.valueOf(request.maxIdleSeconds) : null);
        prepareOpenApiUiServerOptions();
        if (request.port != -1) {
            // enable the main HTTP server when --port is explicitly specified
            writeSetting(main, profileProperties, "camel.server.enabled", "true");
            if (request.port != 8080) {
                writeSetting(main, profileProperties, "camel.server.port", () -> String.valueOf(request.port));
            }
        }
        if (request.port == 0 && request.managementPort == -1) {
            // use same port for management
            request.managementPort = 0;
        }
        if (request.managementPort != -1 && request.managementPort != 8080) {
            writeSetting(main, profileProperties, "camel.management.port",
                    () -> String.valueOf(request.managementPort));
        }
        writeSetting(main, profileProperties, JFR, request.jfrEnabled() ? "jfr" : null);
        writeSetting(main, profileProperties, JFR_PROFILE,
                request.jfrProfile != null ? request.jfrProfile : null);
        if (request.jfrEnabled()) {
            // camel-jfr is always on the classpath, but its runtime instrumentation is opt-in
            writeSetting(main, profileProperties, "camel.main.startupRecorderRuntimeEnabled", "true");
        }

        writeSetting(main, profileProperties, KAMELETS_VERSION, request.kameletsVersion);

        StringJoiner js = new StringJoiner(",");
        StringJoiner sjReload = new StringJoiner(",");
        StringJoiner sjClasspathFiles = new StringJoiner(",");
        StringJoiner sjScriptFiles = new StringJoiner(",");
        StringJoiner sjGroovyFiles = new StringJoiner(",");
        StringJoiner sjTlsFiles = new StringJoiner(",");
        StringJoiner sjKamelets = new StringJoiner(",");
        StringJoiner sjJKubeFiles = new StringJoiner(",");
        StringJoiner sjReadmeFiles = new StringJoiner(",");

        // include generated openapi to files to run
        if (request.openapi != null) {
            request.files.add(openApiGeneratedFile(request.workDir));
        }

        // if we only run pom.xml then auto discover from the Maven based project
        if (request.files.size() == 1 && (request.files.get(0).endsWith("pom.xml"))) {
            Path projectDir = Path.of(request.files.get(0)).toAbsolutePath();
            // use a better name when running
            if (request.name == null || "CamelJBang".equals(request.name)) {
                request.name = MavenProjectHelper.mavenArtifactId(projectDir);
            }
            // find source files
            request.files = MavenProjectHelper.scanMavenProject(projectDir.getParent());
            // include extra dependencies from pom.xml
            var pomDependencies = MavenProjectHelper.scanMavenDependenciesFromPom(projectDir);
            addDependencies(pomDependencies.toArray(new String[0]));
        }

        // auto-detect application.properties and include it
        if (!request.empty && request.sourceDir == null) {
            String appProps = request.baseDir.resolve("application.properties").toString();
            if (Files.exists(Paths.get(appProps)) && !request.files.contains(appProps)) {
                request.files.add(appProps);
            }
        }
        if (request.profile != null) {
            // need to include profile application properties if exists
            String name = request.baseDir + "/application-" + request.profile + ".properties";
            if (Files.exists(Paths.get(name)) && !request.files.contains(name)) {
                request.files.add(name);
            }
        }

        // walk resource directories and add files to classpath preserving directory structure
        if (request.resourceDirs != null) {
            for (String dir : request.resourceDirs) {
                Path dirPath = ExportHelper.validateResourceDir(dir);
                for (Path f : ExportHelper.walkResourceDir(dirPath)) {
                    sjClasspathFiles.add(f.toString());
                }
            }
        }

        if ("CamelJBang".equals(request.name) && request.sourceDir != null && !request.exportRun
                && !request.transformRun) {
            // a whole directory is one app, named after the directory (CAMEL-25830): its first file depends on the
            // order of the file system, and can be any file, such as a Java class that a route uses
            String dirName = directoryName(request.sourceDir);
            if (dirName != null) {
                request.name = dirName;
            }
        }

        String openApiGeneratedFile = openApiGeneratedFile(request.workDir);
        for (String file : request.files) {
            if (file.startsWith("clipboard") && !(Files.exists(Paths.get(file)))) {
                file = clipboard.load(file);
            } else if (RunFiles.skipFile(file, request.excludes, openApiGeneratedFile)) {
                continue;
            } else if (RunFiles.isScriptFile(file)) {
                // script files
                sjScriptFiles.add(file);
                continue;
            } else if (RunFiles.isGroovyFile(file)) {
                // groovy files
                sjGroovyFiles.add("file:" + file);
                if (request.dev) {
                    // groovy files can also be reloaded
                    sjReload.add(file);
                }
                continue;
            } else if (RunFiles.isTlsFile(file)) {
                // tls files
                sjTlsFiles.add(file);
                continue;
            } else if (RunFiles.isJKubeFile(file)) {
                // jkube
                sjJKubeFiles.add(file);
                continue;
            } else if (RunFiles.isReadmeFile(file)) {
                sjReadmeFiles.add(file);
                sjClasspathFiles.add(file);
                continue;
            } else if (!RunFiles.knownFile(file) && !file.endsWith(".properties")) {
                // unknown files to be added on classpath
                sjClasspathFiles.add(file);
                continue;
            }

            // process known files as its likely DSLs or configuration files

            // check for properties files
            if (file.endsWith(".properties")) {
                if (RunFiles.acceptPropertiesFile(file, request.profile)) {
                    if (!ResourceHelper.hasScheme(file) && !file.startsWith("github:")) {
                        file = "file:" + file;
                    }
                    if (ObjectHelper.isEmpty(request.propertiesFiles)) {
                        request.propertiesFiles = file;
                    } else {
                        request.propertiesFiles = request.propertiesFiles + "," + file;
                    }
                    if (request.dev && file.startsWith("file:")) {
                        // we can only reload if file based
                        sjReload.add(file.substring(5));
                    }
                }
                continue;
            }

            // Camel DSL files
            if (!ResourceHelper.hasScheme(file) && !file.startsWith("github:")) {
                file = "file:" + file;
            }
            if (file.startsWith("file:")) {
                // check if file exist
                Path inputPath = Paths.get(file.substring(5));
                if (!Files.exists(inputPath) || !Files.isRegularFile(inputPath)) {
                    printer.printErr("File does not exist: " + file);
                    return FILE_NOT_FOUND;
                }
            }

            if (file.startsWith("file:") && file.endsWith(".kamelet.yaml")) {
                sjKamelets.add(file);
            }

            // automatic map github https urls to github resolver
            if (file.startsWith("https://github.com/")) {
                file = evalGithubSource(main, file);
                if (file == null) {
                    continue; // all mapped continue to next
                }
            } else if (file.startsWith("https://gist.github.com/")) {
                file = evalGistSource(main, file);
                if (file == null) {
                    continue; // all mapped continue to next
                }
            }

            if ("CamelJBang".equals(request.name)) {
                // no specific name was given so lets use the name from the first integration file
                // remove scheme and keep only the name (no path or ext)
                String s = StringHelper.after(file, ":");
                if (s.contains(":")) {
                    // its maybe a gist/github url so we need only the last part which has the name
                    s = StringHelper.afterLast(s, ":");
                }
                request.name = FileUtil.onlyName(s);
            }

            js.add(file);
            if (request.dev && file.startsWith("file:")) {
                // we can only reload if file based
                sjReload.add(file.substring(5));
            }
        }
        writeSetting(main, profileProperties, "camel.main.name", request.name);

        if (request.sourceDir != null) {
            // must be an existing directory
            Path dirPath = Paths.get(request.sourceDir);
            if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                printer.printErr("Directory does not exist: " + request.sourceDir);
                return FILE_NOT_FOUND;
            }
            // make it a pattern as we load all files from this directory
            // (optional=true as there may be non Camel routes files as well)
            String sdir = "file:" + request.sourceDir + "/**?optional=true";
            main.addInitialProperty("camel.main.routesIncludePattern", sdir);
            writeSettings("camel.main.routesIncludePattern", sdir);
        } else if (js.length() > 0) {
            main.addInitialProperty("camel.main.routesIncludePattern", js.toString());
            writeSettings("camel.main.routesIncludePattern", js.toString());
        } else {
            writeSetting(main, profileProperties, "camel.main.routesIncludePattern", () -> null);
        }
        if (sjClasspathFiles.length() > 0) {
            main.addInitialProperty(CLASSPATH_FILES, sjClasspathFiles.toString());
            writeSettings(CLASSPATH_FILES, sjClasspathFiles.toString());
        } else {
            writeSetting(main, profileProperties, CLASSPATH_FILES, () -> null);
        }
        if (sjReadmeFiles.length() > 0) {
            main.addInitialProperty(README_FILES, sjReadmeFiles.toString());
            writeSettings(README_FILES, sjReadmeFiles.toString());
        } else {
            writeSetting(main, profileProperties, README_FILES, () -> null);
        }
        if (sjScriptFiles.length() > 0) {
            main.addInitialProperty(SCRIPT_FILES, sjScriptFiles.toString());
            writeSettings(SCRIPT_FILES, sjScriptFiles.toString());
        } else {
            writeSetting(main, profileProperties, SCRIPT_FILES, () -> null);
        }
        if (sjGroovyFiles.length() > 0) {
            main.addInitialProperty(GROOVY_FILES, sjGroovyFiles.toString());
            writeSettings(GROOVY_FILES, sjGroovyFiles.toString());
            // the groovy sources are compiled by camel-groovy, which nothing else on the classpath pulls in
            // (camel:groovy, camel:groovy:x.y.z, mvn:org.apache.camel:camel-groovy:x.y.z)
            if (request.dependencies.stream()
                    .noneMatch(d -> d.startsWith("camel:groovy") || d.contains(":camel-groovy"))) {
                request.dependencies.add("camel:groovy");
            }
        } else {
            writeSetting(main, profileProperties, GROOVY_FILES, () -> null);
        }
        if (sjTlsFiles.length() > 0) {
            main.addInitialProperty(TLS_FILES, sjTlsFiles.toString());
            writeSettings(TLS_FILES, sjTlsFiles.toString());
        } else {
            writeSetting(main, profileProperties, TLS_FILES, () -> null);
        }
        if (sjJKubeFiles.length() > 0) {
            main.addInitialProperty(JKUBE_FILES, sjJKubeFiles.toString());
            writeSettings(JKUBE_FILES, sjJKubeFiles.toString());
        } else {
            writeSetting(main, profileProperties, JKUBE_FILES, () -> null);
        }
        if (request.resourceDirs != null && !request.resourceDirs.isEmpty()) {
            String dirs = String.join(",", request.resourceDirs);
            main.addInitialProperty(RESOURCE_DIRS, dirs);
            writeSettings(RESOURCE_DIRS, dirs);
        } else {
            writeSetting(main, profileProperties, RESOURCE_DIRS, () -> null);
        }

        if (sjKamelets.length() > 0) {
            String loc = main.getInitialProperties().getProperty("camel.component.kamelet.location");
            if (loc != null) {
                loc = loc + "," + sjKamelets;
            } else {
                loc = sjKamelets.toString();
            }
            main.addInitialProperty("camel.component.kamelet.location", loc);
            writeSettings("camel.component.kamelet.location", loc);
        } else {
            writeSetting(main, profileProperties, "camel.component.kamelet.location", () -> null);
        }

        // we can only reload if file based
        setupReload(main, sjReload);

        if (request.propertiesFiles != null) {
            String[] filesLocation = request.propertiesFiles.split(",");
            // sort so general application.properties comes first (we should load profile first)
            List<String> names = new ArrayList<>(List.of(filesLocation));
            names.sort((o1, o2) -> {
                // make sure application.properties is last
                if (o1.endsWith("application.properties")) {
                    return 1;
                } else if (o2.endsWith("application.properties")) {
                    return -1;
                }
                return 0;
            });
            StringBuilder locations = new StringBuilder();
            for (String file : names) {
                if (!file.startsWith("file:") && !file.startsWith("github:") && !file.startsWith("gist:")) {
                    if (!file.startsWith("/")) {
                        file = Paths.get(FileSystems.getDefault().getPath("").toAbsolutePath().toString(), file).toString();
                    }
                    file = "file://" + file;
                }
                if (!locations.isEmpty()) {
                    locations.append(",");
                }
                locations.append(file);
            }
            // there may be existing properties
            String loc = main.getInitialProperties().getProperty("camel.component.properties.location");
            if (loc != null) {
                loc = loc + "," + locations;
            } else {
                loc = locations.toString();
            }
            main.addInitialProperty("camel.component.properties.location", loc);
            writeSettings("camel.component.properties.location", loc);
            main.setPropertyPlaceholderLocations(loc);
        }

        // merge existing dependencies with --deps
        addDependencies(RuntimeUtil.getDependenciesAsArray(profileProperties));

        // Add runtime-specific dependencies
        addRuntimeSpecificDependenciesFromProperties(profileProperties);

        // and the dependencies of the properties files given on the command line, such as camel/application.properties
        addDependenciesFromPropertiesFiles(request.propertiesFiles);

        // Add plugin dependencies
        if (plugins != null) {
            // Let plugins customize the run environment (e.g., set config directories)
            // before plugin exporter dependencies are added, so exporters can scan the right locations
            Collection<PluginExporter> exporters
                    = plugins.beforeRun(main, Collections.unmodifiableList(request.files));

            for (PluginExporter exporter : exporters) {
                if (exporter.contributeRuntimeDependencies()) {
                    addDependencies(exporter.getDependencies(request.runtime).toArray(String[]::new));
                }
            }
        }

        if (request.observe) {
            request.dependencies.add("camel:observability-services");
            main.addOverrideProperty("camel.metrics.logMetricsOnShutdown", "false");
        }
        GenAiDependencyHelper.addAiObservabilityIfNeeded(request.dependencies, profileProperties, request.observe);
        if (request.chaos) {
            // camel-chaos switches itself on with profile dev (the default of camel run), never with profile prod
            request.dependencies.add("camel:chaos");
        }
        if (request.openapiUi) {
            request.dependencies.add("camel:platform-http-main");
            request.dependencies.add("camel:openapi-java");
            applyOpenApiUiRuntimeOptions(main);
        }
        if (isMcpStdioEnabled(profileProperties)) {
            request.dependencies.add("camel:mcp-server");
            applyMcpStdioRuntimeOptions(main, profileProperties);
        } else if (isAiToolMcpServerEnabled(profileProperties)) {
            request.dependencies.add("camel:platform-http-main");
            request.dependencies.add("camel:mcp-server");
            applyMcpTagsOverride(main);
        } else if (isMcpEnabled(profileProperties)) {
            request.dependencies.add("camel:platform-http-main");
            request.dependencies.add("camel:mcp-server");
        }
        if (request.openTelemetryAgent) {
            request.dependencies.add("camel:opentelemetry2");
            boolean externalExport = "otlp".equals(request.openTelemetryAgentExport)
                    || "jaeger".equals(request.openTelemetryAgentExport)
                    || "observability".equals(request.openTelemetryAgentExport);
            if (!externalExport) {
                request.dependencies.add("camel:platform-http-main");
                if (request.port == -1) {
                    request.port = 8080;
                }
                writeSetting(main, profileProperties, "camel.server.enabled", "true");
            }
        }
        if (request.jfrEnabled()) {
            request.dependencies.add("camel:jfr");
        }
        if (!request.dependencies.isEmpty()) {
            var joined = String.join(",", request.dependencies);
            main.addInitialProperty(DEPENDENCIES, joined);
            writeSettings(DEPENDENCIES, joined);
        }

        return 0;
    }

    private void writeSettings(String key, String value) {
        settings.write(key, value);
    }

    private void writeSetting(KameletMain main, Properties existing, String key, String value) {
        settings.write(main, existing, key, value);
    }

    private void writeSetting(
            KameletMain main, Properties existing, String key, Supplier<String> value) {
        settings.write(main, existing, key, value);
    }

    private boolean isMcpEnabled(Properties profileProperties) {
        String val = profileProperties != null
                ? profileProperties.getProperty(MCP, request.mcp ? "true" : "false")
                : (request.mcp ? "true" : "false");
        return "true".equals(val);
    }

    private boolean isMcpStdioEnabled(Properties profileProperties) {
        return isMcpStdioEnabled(request.mcpStdio, profileProperties);
    }

    /**
     * Whether the ai-tool routes are served over MCP stdio: with the option, or as configured in the profile
     * properties.
     */
    public static boolean isMcpStdioEnabled(boolean mcpStdio, Properties profileProperties) {
        if (mcpStdio) {
            return true;
        }
        return profileProperties != null
                && "stdio".equalsIgnoreCase(profileProperties.getProperty("camel.server.mcp-transport", "").trim());
    }

    boolean isAiToolMcpServerEnabled(Properties profileProperties) {
        if (isMcpStdioEnabled(profileProperties)) {
            return true;
        }
        return profileProperties != null
                && "true".equalsIgnoreCase(profileProperties.getProperty("camel.server.mcp-enabled", "").trim());
    }

    void applyMcpTagsOverride(KameletMain main) {
        if (request.mcpTags != null && !request.mcpTags.isBlank()) {
            main.addOverrideProperty("camel.server.mcp-tags", request.mcpTags);
        }
    }

    /**
     * Align HTTP and management ports when OpenAPI UI is enabled so Swagger UI and the REST OpenAPI document are
     * co-hosted (same Vert.x server when ports match).
     */
    void prepareOpenApiUiServerOptions() {
        if (!request.openapiUi) {
            return;
        }
        if (request.port == -1) {
            request.port = 8080;
        }
        if (request.managementPort == -1) {
            request.managementPort = request.port;
        }
    }

    void applyOpenApiUiRuntimeOptions(BaseMainSupport main) {
        if (!request.openapiUi) {
            return;
        }
        main.addOverrideProperty("camel.rest.apiContextPath", "/q/openapi.json");
        main.addOverrideProperty("camel.rest.component", "platform-http");
        main.addOverrideProperty("camel.management.openapiUiEnabled", "true");
        main.addOverrideProperty("camel.server.enabled", "true");
        main.addOverrideProperty("camel.management.enabled", "true");
    }

    void applyMcpStdioRuntimeOptions(KameletMain main, Properties profileProperties) {
        if (!isMcpStdioEnabled(profileProperties)) {
            return;
        }
        if (isMcpEnabled(profileProperties)) {
            throw new IllegalArgumentException(
                    "--mcp-stdio and --mcp cannot be used together: --mcp serves dev/diagnostics "
                                               + "tools over HTTP management, while --mcp-stdio serves ai-tool routes over stdin/stdout.");
        }
        if (request.mcpStdio) {
            main.addOverrideProperty("camel.server.mcp-enabled", "true");
            main.addOverrideProperty("camel.server.mcp-transport", "stdio");
            applyMcpTagsOverride(main);
        } else {
            writeSetting(main, profileProperties, "camel.server.mcp-enabled", "true");
            writeSetting(main, profileProperties, "camel.server.mcp-transport", "stdio");
        }
        main.addOverrideProperty("camel.main.startupSummaryLevel", "Off");
        main.setSilent(true);
    }

    private void addRuntimeSpecificDependenciesFromProperties(Properties profileProperties) {
        if (profileProperties == null)
            return;

        String runtimeSpecificDeps = null;

        switch (request.runtime) {
            case main, jbang -> runtimeSpecificDeps = profileProperties.getProperty(DEPENDENCIES_MAIN);
            case springBoot -> runtimeSpecificDeps = profileProperties.getProperty(DEPENDENCIES_SPRING_BOOT);
            case quarkus -> runtimeSpecificDeps = profileProperties.getProperty(DEPENDENCIES_QUARKUS);
        }

        if (runtimeSpecificDeps != null && !runtimeSpecificDeps.isEmpty()) {
            addDependencies(runtimeSpecificDeps.split(","));
        }
    }

    /**
     * Adds the dependencies ({@code camel.jbang.dependencies} and the runtime-specific variants) declared in the local
     * properties files given on the command line or with --properties, such as {@code camel/application.properties} run
     * from the parent directory. The profile properties files (application.properties in the current directory or the
     * source dir) are skipped, as their dependencies are already added.
     */
    void addDependenciesFromPropertiesFiles(String locations) throws Exception {
        if (ObjectHelper.isEmpty(locations)) {
            return;
        }
        for (String location : locations.split(",")) {
            location = location.trim();
            if (location.startsWith("file:")) {
                location = location.substring(5);
                if (location.startsWith("//")) {
                    location = location.substring(2);
                }
            } else if (ResourceHelper.hasScheme(location) || location.startsWith("github:")
                    || location.startsWith("gist:")) {
                // only local files
                continue;
            }
            if (location.isEmpty()) {
                continue;
            }
            Path path = Paths.get(location).toAbsolutePath().normalize();
            if (request.profilePropertiesFiles.contains(path) || !Files.isRegularFile(path)) {
                continue;
            }
            Properties prop = new CamelCaseOrderedProperties();
            RuntimeUtil.loadProperties(prop, path);
            addDependencies(RuntimeUtil.getDependenciesAsArray(prop));
            addRuntimeSpecificDependenciesFromProperties(prop);
        }
    }

    private void addDependencies(String... deps) {
        var depsArray = Optional.ofNullable(deps).orElse(new String[0]);
        var depsList = Arrays.stream(depsArray).filter(tok -> !tok.isEmpty()).toList();
        request.dependencies.addAll(depsList);
    }

    private void setupReload(KameletMain main, StringJoiner sjReload) {
        if (request.dev && (request.sourceDir != null || sjReload.length() > 0)) {
            main.addInitialProperty("camel.main.routesReloadEnabled", "true");
            if (request.sourceDir != null) {
                main.addInitialProperty(SOURCE_DIR, request.sourceDir);
                main.addInitialProperty("camel.main.routesReloadDirectory", request.sourceDir);
                main.addInitialProperty("camel.main.routesReloadPattern", sourceDirReloadPattern(request.dataDir));
                main.addInitialProperty("camel.main.routesReloadDirectoryRecursive", "true");
            } else {
                dataDirWithoutSourceDir.run();
                String pattern = sjReload.toString();
                String reloadDir = ".";
                // use current dir, however if we run a file that are in another folder, then we should track that folder instead
                for (String r : sjReload.toString().split(",")) {
                    String path = FileUtil.onlyPath(r);
                    if (path != null && !path.equals(".camel-jbang")) {
                        reloadDir = path;
                        break;
                    }
                }
                main.addInitialProperty("camel.main.routesReloadDirectory", reloadDir);
                main.addInitialProperty("camel.main.routesReloadPattern", pattern);
                main.addInitialProperty("camel.main.routesReloadDirectoryRecursive",
                        isReloadRecursive(pattern) ? "true" : "false");
            }
            if (request.maxMessages <= 0 && request.maxSeconds <= 0 && request.maxIdleSeconds <= 0) {
                // do not shutdown the JVM but stop routes when max duration is triggered, so a reload can start them
                // again; with --max-seconds, --max-messages or --max-idle-seconds the run ends at the limit
                main.addInitialProperty("camel.main.durationMaxAction", "stop");
            }
        }
    }

    private static boolean isReloadRecursive(String reload) {
        for (String part : reload.split(",")) {
            String dir = FileUtil.onlyPath(part);
            if (dir != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * The reload pattern of dev mode with a source directory: every file under it, except the data folders
     * (--data-dir), whose files the routes read and write: a change there is not a change of the sources, and reloading
     * the routes for it restarts them, so they write again and reload again (CAMEL-25670).
     */
    public static String sourceDirReloadPattern(String dataDir) {
        StringJoiner sj = new StringJoiner(",");
        sj.add("*");
        if (dataDir != null) {
            for (String dir : dataDir.split(",")) {
                dir = dir.trim().replace('\\', '/');
                while (dir.startsWith("./")) {
                    dir = dir.substring(2);
                }
                while (dir.endsWith("/")) {
                    dir = dir.substring(0, dir.length() - 1);
                }
                if (!dir.isEmpty()) {
                    sj.add("!" + dir + "/**");
                }
            }
        }
        return sj.toString();
    }

    /**
     * The name of an app that is a whole directory: the name of the directory, or of the first parent with a name that
     * is not one of the folders of a project layout ({@code src/main/resources/camel} is named after the project), and
     * {@code .} is the working directory.
     *
     * @return the name, or <tt>null</tt> when the directory and its parents have no such name
     */
    public static String directoryName(String dir) {
        Path path = Paths.get(dir).toAbsolutePath().normalize();
        while (path != null && path.getFileName() != null) {
            String answer = path.getFileName().toString();
            if (!LAYOUT_FOLDERS.contains(answer.toLowerCase(Locale.ROOT))) {
                return answer;
            }
            path = path.getParent();
        }
        return null;
    }

    private String evalGistSource(KameletMain main, String file) throws Exception {
        StringJoiner routes = new StringJoiner(",");
        StringJoiner kamelets = new StringJoiner(",");
        StringJoiner properties = new StringJoiner(",");
        fetchGistUrls(file, routes, kamelets, properties);

        if (properties.length() > 0) {
            main.addInitialProperty("camel.component.properties.location", properties.toString());
        }
        if (kamelets.length() > 0) {
            String loc = main.getInitialProperties().getProperty("camel.component.kamelet.location");
            if (loc != null) {
                // local kamelets first
                loc = kamelets + "," + loc;
            } else {
                loc = kamelets.toString();
            }
            main.addInitialProperty("camel.component.kamelet.location", loc);
        }
        if (routes.length() > 0) {
            return routes.toString();
        }
        return null;
    }

    private String evalGithubSource(KameletMain main, String file) throws Exception {
        String ext = FileUtil.onlyExt(file);
        boolean wildcard = FileUtil.onlyName(file, false).contains("*");
        if (ext != null && !wildcard) {
            // it is a single file so map to
            return asGithubSingleUrl(file);
        } else {
            StringJoiner routes = new StringJoiner(",");
            StringJoiner kamelets = new StringJoiner(",");
            StringJoiner properties = new StringJoiner(",");
            fetchGithubUrls(file, routes, kamelets, properties);

            if (properties.length() > 0) {
                main.addInitialProperty("camel.component.properties.location", properties.toString());
            }
            if (kamelets.length() > 0) {
                String loc = main.getInitialProperties().getProperty("camel.component.kamelet.location");
                if (loc != null) {
                    // local kamelets first
                    loc = kamelets + "," + loc;
                } else {
                    loc = kamelets.toString();
                }
                main.addInitialProperty("camel.component.kamelet.location", loc);
            }
            if (routes.length() > 0) {
                return routes.toString();
            }
            return null;
        }
    }
}
