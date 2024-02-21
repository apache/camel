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

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.StringJoiner;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.models.openapi.OpenApiDocument;
import org.apache.camel.CamelContext;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.LoggingLevelCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.generator.openapi.RestDslGenerator;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.main.KameletMain;
import org.apache.camel.main.download.DownloadListener;
import org.apache.camel.spi.BacklogDebugger;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.AntPathMatcher;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.xml.io.util.XmlStreamDetector;
import org.apache.camel.xml.io.util.XmlStreamInfo;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static org.apache.camel.dsl.jbang.core.common.GistHelper.asGistSingleUrl;
import static org.apache.camel.dsl.jbang.core.common.GistHelper.fetchGistUrls;
import static org.apache.camel.dsl.jbang.core.common.GitHubHelper.asGithubSingleUrl;
import static org.apache.camel.dsl.jbang.core.common.GitHubHelper.fetchGithubUrls;

@Command(name = "run", description = "Run as local Camel integration")
public class Run extends CamelCommand {

    public static final String RUN_SETTINGS_FILE = "camel-jbang-run.properties";

    private static final String[] ACCEPTED_FILE_EXT
            = new String[] { "java", "groovy", "js", "jsh", "kts", "xml", "yaml" };

    private static final String[] ACCEPTED_XML_ROOT_ELEMENT_NAMES = new String[] {
            "route", "routes",
            "routeTemplate", "routeTemplates",
            "templatedRoute", "templatedRoutes",
            "rest", "rests",
            "routeConfiguration",
            "beans", "blueprint", "camel"
    };

    private static final Set<String> ACCEPTED_XML_ROOT_ELEMENTS
            = new HashSet<>(Arrays.asList(ACCEPTED_XML_ROOT_ELEMENT_NAMES));

    private static final String OPENAPI_GENERATED_FILE = CommandLineHelper.CAMEL_JBANG_WORK_DIR + "/generated-openapi.yaml";
    private static final String CLIPBOARD_GENERATED_FILE = CommandLineHelper.CAMEL_JBANG_WORK_DIR + "/generated-clipboard";

    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "^\\s*package\\s+([a-zA-Z][.\\w]*)\\s*;.*$", Pattern.MULTILINE);

    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "^\\s*public class\\s+([a-zA-Z0-9]*)[\\s+|;].*$", Pattern.MULTILINE);

    public boolean silentRun;
    boolean scriptRun;
    boolean transformRun;
    boolean transformMessageRun;
    boolean debugRun;

    private File logFile;
    public long spawnPid;

    @Parameters(description = "The Camel file(s) to run. If no files specified then application.properties is used as source for which files to run.",
                arity = "0..9", paramLabel = "<files>", parameterConsumer = FilesConsumer.class)
    Path[] filePaths; // Defined only for file path completion; the field never used

    public List<String> files = new ArrayList<>();

    @Option(names = { "--source-dir" },
            description = "Source directory for dynamically loading Camel file(s) to run. When using this, then files cannot be specified at the same time.")
    String sourceDir;

    @Option(names = { "--background" }, defaultValue = "false", description = "Run in the background")
    public boolean background;

    @Option(names = { "--empty" }, defaultValue = "false", description = "Run an empty Camel without loading source files")
    public boolean empty;

    @Option(names = { "--camel-version" }, description = "To run using a different Camel version than the default version.")
    String camelVersion;

    @Option(names = { "--kamelets-version" }, description = "Apache Camel Kamelets version")
    String kameletsVersion;

    @Option(names = { "--profile" }, scope = CommandLine.ScopeType.INHERIT, defaultValue = "application",
            description = "Profile to use, which refers to loading properties file with the given profile name. By default application.properties is loaded.")
    String profile = "application";

    @Option(names = {
            "--dep", "--deps" }, description = "Add additional dependencies (Use commas to separate multiple dependencies)")
    String dependencies;

    @Option(names = { "--repos" },
            description = "Additional maven repositories for download on-demand (Use commas to separate multiple repositories)")
    String repos;

    @Option(names = { "--gav" }, description = "The Maven group:artifact:version (used during exporting)")
    String gav;

    @Option(names = { "--maven-settings" },
            description = "Optional location of Maven settings.xml file to configure servers, repositories, mirrors and proxies."
                          +
                          " If set to \"false\", not even the default ~/.m2/settings.xml will be used.")
    String mavenSettings;

    @Option(names = { "--maven-settings-security" },
            description = "Optional location of Maven settings-security.xml file to decrypt settings.xml")
    String mavenSettingsSecurity;

    @Option(names = { "--fresh" }, description = "Make sure we use fresh (i.e. non-cached) resources")
    boolean fresh;

    @Option(names = { "--download" }, defaultValue = "true",
            description = "Whether to allow automatic downloading JAR dependencies (over the internet)")
    boolean download = true;

    @Option(names = { "--jvm-debug" }, parameterConsumer = DebugConsumer.class, paramLabel = "<true|false|port>",
            description = "To enable JVM remote debugging on port 4004 by default. The supported values are true to " +
                          "enable the remote debugging, false to disable the remote debugging or a number to use a custom port")
    int jvmDebugPort;

    @Option(names = { "--name" }, defaultValue = "CamelJBang", description = "The name of the Camel application")
    String name;

    @Option(names = { "--exclude" },
            description = "Exclude files by name or pattern. Multiple names can be separated by comma.")
    String exclude;

    @Option(names = { "--logging" }, defaultValue = "true", description = "Can be used to turn off logging")
    boolean logging = true;

    @Option(names = { "--logging-level" }, completionCandidates = LoggingLevelCompletionCandidates.class,
            defaultValue = "info", description = "Logging level")
    String loggingLevel;

    @Option(names = { "--logging-color" }, defaultValue = "true", description = "Use colored logging")
    boolean loggingColor = true;

    @Option(names = { "--logging-json" }, description = "Use JSON logging (ECS Layout)")
    boolean loggingJson;

    @Option(names = { "--logging-config-path" }, description = "Path to file with custom logging configuration")
    String loggingConfigPath;

    @Option(names = { "--max-messages" }, defaultValue = "0", description = "Max number of messages to process before stopping")
    int maxMessages;

    @Option(names = { "--max-seconds" }, defaultValue = "0", description = "Max seconds to run before stopping")
    int maxSeconds;

    @Option(names = { "--max-idle-seconds" }, defaultValue = "0",
            description = "For how long time in seconds Camel can be idle before stopping")
    int maxIdleSeconds;

    @Option(names = { "--reload", "--dev" },
            description = "Enables dev mode (live reload when source files are updated and saved)")
    boolean dev;

    @Option(names = { "--trace" }, description = "Enables trace logging of the routed messages")
    boolean trace;

    @Option(names = { "--properties" },
            description = "comma separated list of properties file" +
                          " (ex. /path/to/file.properties,/path/to/other.properties")
    String propertiesFiles;

    @Option(names = { "-p", "--prop", "--property" }, description = "Additional properties (override existing)", arity = "0")
    String[] property;

    @Option(names = { "--stub" }, description = "Stubs all the matching endpoint with the given component name or pattern."
                                                + " Multiple names can be separated by comma. (all = everything).")
    String stub;

    @Option(names = { "--jfr" },
            description = "Enables Java Flight Recorder saving recording to disk on exit")
    boolean jfr;

    @Option(names = { "--jfr-profile" },
            description = "Java Flight Recorder profile to use (such as default or profile)")
    String jfrProfile;

    @Option(names = { "--local-kamelet-dir" },
            description = "Local directory (or github link) for loading Kamelets (takes precedence). Multiple directories can be specified separated by comma.")
    String localKameletDir;

    @Option(names = { "--port" }, description = "Embeds a local HTTP server on this port")
    int port;

    @Option(names = { "--console" }, description = "Developer console at /q/dev on local HTTP server (port 8080 by default)")
    boolean console;

    @Option(names = { "--health" }, description = "Health check at /q/health on local HTTP server (port 8080 by default)")
    boolean health;

    @Option(names = { "--metrics" },
            description = "Metrics (Micrometer and Prometheus) at /q/metrics on local HTTP server (port 8080 by default)")
    boolean metrics;

    @Option(names = { "--modeline" }, defaultValue = "true", description = "Enables Camel-K style modeline")
    boolean modeline = true;

    @Option(names = { "--open-api" }, description = "Adds an OpenAPI spec from the given file (json or yaml file)")
    String openapi;

    @Option(names = { "--code" }, description = "Run the given string as Java DSL route")
    String code;

    @Option(names = { "--verbose" }, description = "Verbose output of startup activity (dependency resolution and downloading")
    boolean verbose;

    @Option(names = { "--ignore-loading-error" },
            description = "Whether to ignore route loading and compilation errors (use this with care!)")
    protected boolean ignoreLoadingError;

    @Option(names = { "--prompt" },
            description = "Allow user to type in required parameters in prompt if not present in application")
    boolean prompt;

    public Run(CamelJBangMain main) {
        super(main);
    }

    public String getProfile() {
        return profile;
    }

    @Override
    public boolean disarrangeLogging() {
        return false;
    }

    @Override
    public Integer doCall() throws Exception {
        if (!silentRun) {
            printConfigurationValues("Running integration with the following configuration:");
        }
        // run
        return run();
    }

    public Integer runSilent() throws Exception {
        return runSilent(false);
    }

    protected Integer runSilent(boolean ignoreLoadingError) throws Exception {
        // just boot silently and exit
        this.silentRun = true;
        return run();
    }

    protected Integer runTransform(boolean ignoreLoadingError) throws Exception {
        // just boot silently and exit
        this.transformRun = true;
        this.ignoreLoadingError = ignoreLoadingError;
        this.name = "transform";
        return run();
    }

    public Integer runTransformMessage(String camelVersion) throws Exception {
        // just boot silently an empty camel in the background and exit
        this.transformMessageRun = true;
        this.background = true;
        this.camelVersion = camelVersion;
        this.empty = true;
        this.ignoreLoadingError = true;
        this.name = "transform";
        return run();
    }

    protected Integer runScript(String file) throws Exception {
        this.files.add(file);
        this.scriptRun = true;
        return run();
    }

    protected Integer runDebug() throws Exception {
        this.debugRun = true;
        return run();
    }

    private boolean isDebugMode() {
        return jvmDebugPort > 0;
    }

    private void writeSetting(KameletMain main, Properties existing, String key, String value) {
        String val = existing != null ? existing.getProperty(key, value) : value;
        if (val != null) {
            main.addInitialProperty(key, val);
            writeSettings(key, val);
        }
    }

    private void writeSetting(KameletMain main, Properties existing, String key, Supplier<String> value) {
        String val = existing != null ? existing.getProperty(key, value.get()) : value.get();
        if (val != null) {
            main.addInitialProperty(key, val);
            writeSettings(key, val);
        }
    }

    private Properties loadProfileProperties(File source) throws Exception {
        Properties prop = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(prop, source);

        // special for routes include pattern that we need to "fix" after reading from properties
        // to make this work in run command
        String value = prop.getProperty("camel.main.routesIncludePattern");
        if (value != null) {
            // if not scheme then must use file: as this is what run command expects
            StringJoiner sj = new StringJoiner(",");
            for (String part : value.split(",")) {
                if (!part.contains(":")) {
                    part = "file:" + part;
                }
                sj.add(part);
            }
            value = sj.toString();
            prop.setProperty("camel.main.routesIncludePattern", value);
        }

        return prop;
    }

    private int run() throws Exception {
        if (!empty && !files.isEmpty() && sourceDir != null) {
            // cannot have both files and source dir at the same time
            System.err.println("Cannot specify both file(s) and source-dir at the same time.");
            return 1;
        }

        File work = CommandLineHelper.getWorkDir();
        removeDir(work);
        work.mkdirs();

        Properties profileProperties = !empty ? loadProfileProperties() : null;
        configureLogging();
        if (openapi != null) {
            generateOpenApi();
        }

        // route code as option
        if (!empty && code != null) {
            // store code in temporary file
            String codeFile = loadFromCode(code);
            // use code as first file
            files.add(0, codeFile);
        }

        // if no specific file to run then try to auto-detect
        if (!empty && files.isEmpty() && sourceDir == null) {
            String routes = profileProperties != null ? profileProperties.getProperty("camel.main.routesIncludePattern") : null;
            if (routes == null) {
                if (!silentRun) {
                    String run = "run";
                    if (transformRun) {
                        run = "transform";
                    } else if (debugRun) {
                        run = "debug";
                    }
                    System.err
                            .println("Cannot " + run + " because " + getProfile()
                                     + ".properties file does not exist or camel.main.routesIncludePattern is not configured");
                    return 1;
                } else {
                    // silent-run then auto-detect all files (except properties as they are loaded explicit or via profile)
                    String[] allFiles = new File(".").list((dir, name) -> !name.endsWith(".properties"));
                    if (allFiles != null) {
                        files.addAll(Arrays.asList(allFiles));
                    }
                }
            }
        }
        // filter out duplicate files
        if (!files.isEmpty()) {
            files = files.stream().distinct().collect(Collectors.toList());
        }

        final KameletMain main = createMainInstance();
        main.setRepos(repos);
        main.setDownload(download);
        main.setFresh(fresh);
        main.setMavenSettings(mavenSettings);
        main.setMavenSettingsSecurity(mavenSettingsSecurity);
        main.setDownloadListener(new RunDownloadListener());
        main.setAppName("Apache Camel (JBang)");

        if (stub != null) {
            if ("all".equals(stub)) {
                stub = "*";
            }
            // we need to match by wildcard, to make it easier
            StringJoiner sj = new StringJoiner(",");
            for (String n : stub.split(",")) {
                // you can either refer to a name or a specific endpoint
                // if there is a colon then we assume its a specific endpoint then we should not add wildcard
                boolean colon = n.contains(":");
                if (!colon && !n.endsWith("*")) {
                    n = n + "*";
                }
                sj.add(n);
            }
            stub = sj.toString();
            writeSetting(main, profileProperties, "camel.jbang.stub", stub);
            main.setStubPattern(stub);
        }

        writeSetting(main, profileProperties, "camel.main.sourceLocationEnabled", "true");
        if (dev) {
            writeSetting(main, profileProperties, "camel.main.routesReloadEnabled", "true");
            // allow quick shutdown during development
            writeSetting(main, profileProperties, "camel.main.shutdownTimeout", "5");
        }
        if (sourceDir != null) {
            writeSetting(main, profileProperties, "camel.jbang.sourceDir", sourceDir);
        }
        if (trace) {
            writeSetting(main, profileProperties, "camel.main.tracing", "true");
        }
        if (modeline) {
            writeSetting(main, profileProperties, "camel.main.modeline", "true");
        }
        if (ignoreLoadingError) {
            writeSetting(main, profileProperties, "camel.jbang.ignoreLoadingError", "true");
        }
        if (prompt) {
            writeSetting(main, profileProperties, "camel.jbang.prompt", "true");
        }
        writeSetting(main, profileProperties, "camel.jbang.compileWorkDir",
                CommandLineHelper.CAMEL_JBANG_WORK_DIR + File.separator + "compile");

        if (gav != null) {
            writeSetting(main, profileProperties, "camel.jbang.gav", gav);
        }
        writeSetting(main, profileProperties, "camel.jbang.open-api", openapi);
        writeSetting(main, profileProperties, "camel.jbang.repos", repos);
        writeSetting(main, profileProperties, "camel.jbang.health", health ? "true" : "false");
        writeSetting(main, profileProperties, "camel.jbang.metrics", metrics ? "true" : "false");
        writeSetting(main, profileProperties, "camel.jbang.console", console ? "true" : "false");
        writeSetting(main, profileProperties, "camel.jbang.verbose", verbose ? "true" : "false");
        writeSetting(main, profileProperties, "camel.jbang.backlogTracing", "true");
        // the runtime version of Camel is what is loaded via the catalog
        writeSetting(main, profileProperties, "camel.jbang.camel-version", new DefaultCamelCatalog().getCatalogVersion());

        // command line arguments
        if (property != null) {
            for (String p : property) {
                String k = StringHelper.before(p, "=");
                String v = StringHelper.after(p, "=");
                if (k != null && v != null) {
                    main.addArgumentProperty(k, v);
                    writeSettings(k, v);
                }
            }
        }

        if (silentRun) {
            main.setSilent(true);
            // enable stub in silent mode so we do not use real components
            main.setStubPattern("*");
            // do not run for very long in silent run
            main.addInitialProperty("camel.main.autoStartup", "false");
            main.addInitialProperty("camel.main.durationMaxSeconds", "1");
        } else if (debugRun) {
            main.addInitialProperty("camel.jbang.debug", "true");
        } else if (transformRun) {
            main.setSilent(true);
            // enable stub in silent mode so we do not use real components
            main.setStubPattern("*");
            // do not run for very long in silent run
            main.addInitialProperty("camel.main.autoStartup", "false");
            main.addInitialProperty("camel.main.durationMaxSeconds", "1");
        } else if (transformMessageRun) {
            // do not start any routes
            main.addInitialProperty("camel.main.autoStartup", "false");
        } else if (scriptRun) {
            // auto terminate if being idle
            main.addInitialProperty("camel.main.durationMaxIdleSeconds", "1");
        }
        // any custom initial property
        doAddInitialProperty(main);

        writeSetting(main, profileProperties, "camel.main.durationMaxMessages",
                () -> maxMessages > 0 ? String.valueOf(maxMessages) : null);
        writeSetting(main, profileProperties, "camel.main.durationMaxSeconds",
                () -> maxSeconds > 0 ? String.valueOf(maxSeconds) : null);
        writeSetting(main, profileProperties, "camel.main.durationMaxIdleSeconds",
                () -> maxIdleSeconds > 0 ? String.valueOf(maxIdleSeconds) : null);
        writeSetting(main, profileProperties, "camel.jbang.platform-http.port",
                () -> port > 0 ? String.valueOf(port) : null);
        writeSetting(main, profileProperties, "camel.jbang.jfr", jfr || jfrProfile != null ? "jfr" : null); // TODO: "true" instead of "jfr" ?
        writeSetting(main, profileProperties, "camel.jbang.jfr-profile", jfrProfile != null ? jfrProfile : null);

        writeSetting(main, profileProperties, "camel.jbang.kameletsVersion", kameletsVersion);

        StringJoiner js = new StringJoiner(",");
        StringJoiner sjReload = new StringJoiner(",");
        StringJoiner sjClasspathFiles = new StringJoiner(",");
        StringJoiner sjKamelets = new StringJoiner(",");
        StringJoiner sjJKubeFiles = new StringJoiner(",");

        // include generated openapi to files to run
        if (openapi != null) {
            files.add(OPENAPI_GENERATED_FILE);
        }

        // if we only run pom.xml/build.gradle then auto discover from the Maven/Gradle based project
        if (files.size() == 1 && ("pom.xml".equals(files.get(0)) || "build.gradle".equals(files.get(0)))) {
            // use a better name when running
            if (name == null || "CamelJBang".equals(name)) {
                name = RunHelper.mavenArtifactId();
            }
            // find source files
            files = RunHelper.scanMavenOrGradleProject();
            // include extra dependencies from pom.xml
            List<String> deps = RunHelper.scanMavenDependenciesFromPom();
            for (String d : deps) {
                if (dependencies == null) {
                    dependencies = "";
                }
                if (dependencies.isBlank()) {
                    dependencies = d;
                } else {
                    dependencies += "," + d;
                }
            }
        }

        for (String file : files) {
            if (file.startsWith("clipboard") && !(new File(file).exists())) {
                file = loadFromClipboard(file);
            } else if (skipFile(file)) {
                continue;
            } else if (jkubeFile(file)) {
                // jkube
                sjJKubeFiles.add(file);
                continue;
            } else if (!knownFile(file) && !file.endsWith(".properties")) {
                // non known files to be added on classpath
                sjClasspathFiles.add(file);
                continue;
            }

            // process known files as its likely DSLs or configuration files

            // check for properties files
            if (file.endsWith(".properties")) {
                if (!ResourceHelper.hasScheme(file) && !file.startsWith("github:")) {
                    file = "file:" + file;
                }
                if (ObjectHelper.isEmpty(propertiesFiles)) {
                    propertiesFiles = file;
                } else {
                    propertiesFiles = propertiesFiles + "," + file;
                }
                if (dev && file.startsWith("file:")) {
                    // we can only reload if file based
                    sjReload.add(file.substring(5));
                }
                continue;
            }

            // Camel DSL files
            if (!ResourceHelper.hasScheme(file) && !file.startsWith("github:")) {
                file = "file:" + file;
            }
            if (file.startsWith("file:")) {
                // check if file exist
                File inputFile = new File(file.substring(5));
                if (!inputFile.exists() && !inputFile.isFile()) {
                    System.err.println("File does not exist: " + file);
                    return 1;
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

            if ("CamelJBang".equals(name)) {
                // no specific name was given so lets use the name from the first integration file
                // remove scheme and keep only the name (no path or ext)
                String s = StringHelper.after(file, ":");
                if (s.contains(":")) {
                    // its maybe a gist/github url so we need only the last part which has the name
                    s = StringHelper.afterLast(s, ":");
                }
                name = FileUtil.onlyName(s);
            }

            js.add(file);
            if (dev && file.startsWith("file:")) {
                // we can only reload if file based
                sjReload.add(file.substring(5));
            }
        }
        writeSetting(main, profileProperties, "camel.main.name", name);

        if (sourceDir != null) {
            // must be an existing directory
            File dir = new File(sourceDir);
            if (!dir.exists() && !dir.isDirectory()) {
                System.err.println("Directory does not exist: " + sourceDir);
                return 1;
            }
            // make it a pattern as we load all files from this directory
            // (optional=true as there may be non Camel routes files as well)
            js.add("file:" + sourceDir + "/**?optional=true");
        }

        if (js.length() > 0) {
            main.addInitialProperty("camel.main.routesIncludePattern", js.toString());
            writeSettings("camel.main.routesIncludePattern", js.toString());
        } else {
            writeSetting(main, profileProperties, "camel.main.routesIncludePattern", () -> null);
        }
        if (sjClasspathFiles.length() > 0) {
            main.addInitialProperty("camel.jbang.classpathFiles", sjClasspathFiles.toString());
            writeSettings("camel.jbang.classpathFiles", sjClasspathFiles.toString());
        } else {
            writeSetting(main, profileProperties, "camel.jbang.classpathFiles", () -> null);
        }
        if (sjJKubeFiles.length() > 0) {
            main.addInitialProperty("camel.jbang.jkubeFiles", sjJKubeFiles.toString());
            writeSettings("camel.jbang.jkubeFiles", sjJKubeFiles.toString());
        } else {
            writeSetting(main, profileProperties, "camel.jbang.jkubeFiles", () -> null);
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

        if (propertiesFiles != null) {
            String[] filesLocation = propertiesFiles.split(",");
            StringBuilder locations = new StringBuilder();
            for (String file : filesLocation) {
                if (!file.startsWith("file:")) {
                    if (!file.startsWith("/")) {
                        file = FileSystems.getDefault().getPath("").toAbsolutePath() + File.separator + file;
                    }
                    file = "file://" + file;
                }
                if (locations.length() > 0) {
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
            // TODO: remove duplicates in loc
            main.addInitialProperty("camel.component.properties.location", loc);
            writeSettings("camel.component.properties.location", loc);
        }

        // merge existing dependencies with --deps
        String deps = RuntimeUtil.getDependencies(profileProperties);
        if (deps.isBlank()) {
            deps = dependencies != null ? dependencies : "";
        } else if (dependencies != null && !dependencies.equals(deps)) {
            deps += "," + dependencies;
        }
        if (!deps.isBlank()) {
            main.addInitialProperty("camel.jbang.dependencies", deps);
            writeSettings("camel.jbang.dependencies", deps);
        }

        // if we have a specific camel version then make sure we really need to switch
        if (camelVersion != null) {
            CamelCatalog catalog = new DefaultCamelCatalog();
            String v = catalog.getCatalogVersion();
            if (camelVersion.equals(v)) {
                // same version, so we use current
                camelVersion = null;
            }
        }

        // okay we have validated all input and are ready to run
        if (camelVersion != null || isDebugMode()) {
            // TODO: debug camel specific version
            boolean custom = false;
            if (camelVersion != null) {
                // run in another JVM with different camel version (foreground or background)
                custom = camelVersion.contains("-") && !camelVersion.endsWith("-SNAPSHOT");
                if (custom) {
                    // regular camel versions can also be a milestone or release candidate
                    custom = !camelVersion.matches(".*-(RC|M)\\d$");
                }
            }
            if (custom) {
                // custom camel distribution
                return runCustomCamelVersion(main);
            } else {
                // apache camel distribution or remote debug enabled
                return runCamelVersion(main);
            }
        } else if (debugRun) {
            // spawn new JVM to debug in background
            return runDebug(main);
        } else if (background) {
            // spawn new JVM to run in background
            return runBackground(main);
        } else {
            // run default in current JVM with same camel version
            return runKameletMain(main);
        }
    }

    protected void doAddInitialProperty(KameletMain main) {
        // noop
    }

    private void setupReload(KameletMain main, StringJoiner sjReload) {
        if (dev && (sourceDir != null || sjReload.length() > 0)) {
            main.addInitialProperty("camel.main.routesReloadEnabled", "true");
            if (sourceDir != null) {
                main.addInitialProperty("camel.jbang.sourceDir", sourceDir);
                main.addInitialProperty("camel.main.routesReloadDirectory", sourceDir);
                main.addInitialProperty("camel.main.routesReloadPattern", "*");
                main.addInitialProperty("camel.main.routesReloadDirectoryRecursive", "true");
            } else {
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
            // do not shutdown the JVM but stop routes when max duration is triggered
            main.addInitialProperty("camel.main.durationMaxAction", "stop");
        }
    }

    private Properties loadProfileProperties() throws Exception {
        Properties answer = null;

        if (transformMessageRun) {
            // do not load profile in transform message run as it should be vanilla empty
            return answer;
        }

        File profilePropertiesFile;
        if (sourceDir != null) {
            profilePropertiesFile = new File(sourceDir, getProfile() + ".properties");
        } else {
            profilePropertiesFile = new File(getProfile() + ".properties");
        }
        if (profilePropertiesFile.exists()) {
            answer = loadProfileProperties(profilePropertiesFile);
            // logging level/color may be configured in the properties file
            loggingLevel = answer.getProperty("loggingLevel", loggingLevel);
            loggingColor
                    = "true".equals(answer.getProperty("loggingColor", loggingColor ? "true" : "false"));
            loggingJson
                    = "true".equals(answer.getProperty("loggingJson", loggingJson ? "true" : "false"));
            if (propertiesFiles == null) {
                propertiesFiles = "file:" + profilePropertiesFile.getPath();
            } else {
                propertiesFiles = propertiesFiles + ",file:" + profilePropertiesFile.getPath();
            }
            repos = answer.getProperty("camel.jbang.repos", repos);
            mavenSettings = answer.getProperty("camel.jbang.maven-settings", mavenSettings);
            mavenSettingsSecurity = answer.getProperty("camel.jbang.maven-settings-security", mavenSettingsSecurity);
            openapi = answer.getProperty("camel.jbang.open-api", openapi);
            download = "true".equals(answer.getProperty("camel.jbang.download", download ? "true" : "false"));
            background = "true".equals(answer.getProperty("camel.jbang.background", background ? "true" : "false"));
            jvmDebugPort = parseJvmDebugPort(answer.getProperty("camel.jbang.jvmDebug", Integer.toString(jvmDebugPort)));
            camelVersion = answer.getProperty("camel.jbang.camel-version", camelVersion);
            kameletsVersion = answer.getProperty("camel.jbang.kameletsVersion", kameletsVersion);
            gav = answer.getProperty("camel.jbang.gav", gav);
            stub = answer.getProperty("camel.jbang.stub", stub);
            exclude = answer.getProperty("camel.jbang.exclude", exclude);
        }

        if (kameletsVersion == null) {
            kameletsVersion = VersionHelper.extractKameletsVersion();
        }
        return answer;
    }

    /**
     * Parses the JVM debug port from the given value.
     * <p/>
     * The value can be {@code true} to indicate a default port which is {@code 4004}, {@code false} to indicate no
     * debug, or a number corresponding to a custom port.
     *
     * @param  value the value to parse.
     *
     * @return       the JVM debug port corresponding to the given value.
     */
    private static int parseJvmDebugPort(String value) {
        if (value == null) {
            return 0;
        } else if (value.equals("true")) {
            return 4004;
        } else if (value.equals("false")) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    protected int runCamelVersion(KameletMain main) throws Exception {
        List<String> cmds;
        if (spec != null) {
            cmds = new ArrayList<>(spec.commandLine().getParseResult().originalArgs());
        } else {
            cmds = new ArrayList<>();
            cmds.add("run");
            if (transformMessageRun) {
                cmds.add("--empty");
            }
        }

        if (background) {
            cmds.remove("--background=true");
            cmds.remove("--background");
        }
        if (camelVersion != null) {
            cmds.remove("--camel-version=" + camelVersion);
        }
        // need to use jbang command to specify camel version
        List<String> jbangArgs = new ArrayList<>();
        jbangArgs.add("jbang");
        jbangArgs.add("run");
        if (camelVersion != null) {
            jbangArgs.add("-Dcamel.jbang.version=" + camelVersion);
        }
        if (kameletsVersion != null) {
            jbangArgs.add("-Dcamel-kamelets.version=" + kameletsVersion);
        }
        // tooling may signal to run JMX debugger in suspended mode via JVM system property
        // which we must include in args as well
        String debugSuspend = System.getProperty(BacklogDebugger.SUSPEND_MODE_SYSTEM_PROP_NAME);
        if (debugSuspend != null) {
            jbangArgs.add("-D" + BacklogDebugger.SUSPEND_MODE_SYSTEM_PROP_NAME + "=" + debugSuspend);
        }
        if (isDebugMode()) {
            jbangArgs.add("--debug=" + jvmDebugPort); // jbang --debug=port
            cmds.removeIf(arg -> arg.startsWith("--jvm-debug"));
        }

        if (repos != null) {
            jbangArgs.add("--repos=" + repos);
        }
        jbangArgs.add("camel@apache/camel");
        jbangArgs.addAll(cmds);

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(jbangArgs);

        if (background) {
            Process p = pb.start();
            this.spawnPid = p.pid();
            if (!silentRun && !transformRun && !transformMessageRun) {
                printer().println("Running Camel integration: " + name + " (version: " + camelVersion
                                  + ") in background with PID: " + p.pid());
            }
            return 0;
        } else {
            pb.inheritIO(); // run in foreground (with IO so logs are visible)
            Process p = pb.start();
            this.spawnPid = p.pid();
            // wait for that process to exit as we run in foreground
            return p.waitFor();
        }
    }

    protected int runBackground(KameletMain main) throws Exception {
        List<String> cmds;
        if (spec != null) {
            cmds = new ArrayList<>(spec.commandLine().getParseResult().originalArgs());
        } else {
            cmds = new ArrayList<>();
            cmds.add("run");
            if (transformMessageRun) {
                cmds.add("--empty");
            }
        }

        cmds.remove("--background=true");
        cmds.remove("--background");

        cmds.add(0, "camel");

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(cmds);
        Process p = pb.start();
        this.spawnPid = p.pid();
        if (!silentRun && !transformRun && !transformMessageRun) {
            printer().println("Running Camel integration: " + name + " in background with PID: " + p.pid());
        }
        return 0;
    }

    protected int runDebug(KameletMain main) throws Exception {
        // to be implemented in Debug
        return 0;
    }

    protected int runCustomCamelVersion(KameletMain main) throws Exception {
        InputStream is = Run.class.getClassLoader().getResourceAsStream("templates/run-custom-camel-version.tmpl");
        String content = IOHelper.loadText(is);
        IOHelper.close(is);

        content = content.replaceFirst("\\{\\{ \\.JavaVersion }}", "17");
        if (repos != null) {
            content = content.replaceFirst("\\{\\{ \\.MavenRepositories }}", "//REPOS " + repos);
        } else {
            content = content.replaceFirst("\\{\\{ \\.MavenRepositories }}", "");
        }

        // use custom distribution of camel
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("//DEPS org.apache.camel:camel-bom:%s@pom%n", camelVersion));
        sb.append(String.format("//DEPS org.apache.camel:camel-core:%s%n", camelVersion));
        sb.append(String.format("//DEPS org.apache.camel:camel-core-engine:%s%n", camelVersion));
        sb.append(String.format("//DEPS org.apache.camel:camel-main:%s%n", camelVersion));
        sb.append(String.format("//DEPS org.apache.camel:camel-java-joor-dsl:%s%n", camelVersion));
        sb.append(String.format("//DEPS org.apache.camel:camel-kamelet:%s%n", camelVersion));
        content = content.replaceFirst("\\{\\{ \\.CamelDependencies }}", sb.toString());

        // use apache distribution of camel-jbang
        String v = camelVersion.substring(0, camelVersion.lastIndexOf('.'));
        sb = new StringBuilder();
        sb.append(String.format("//DEPS org.apache.camel:camel-jbang-core:%s%n", v));
        sb.append(String.format("//DEPS org.apache.camel:camel-kamelet-main:%s%n", v));
        sb.append(String.format("//DEPS org.apache.camel:camel-resourceresolver-github:%s%n", v));
        if (VersionHelper.isGE(v, "3.19.0")) {
            sb.append(String.format("//DEPS org.apache.camel:camel-cli-connector:%s%n", v));
        }
        content = content.replaceFirst("\\{\\{ \\.CamelJBangDependencies }}", sb.toString());

        sb = new StringBuilder();
        sb.append(String.format("//DEPS org.apache.camel.kamelets:camel-kamelets:%s%n", kameletsVersion));
        content = content.replaceFirst("\\{\\{ \\.CamelKameletsDependencies }}", sb.toString());

        String fn = CommandLineHelper.CAMEL_JBANG_WORK_DIR + "/CustomCamelJBang.java";
        Files.write(Paths.get(fn), content.getBytes(StandardCharsets.UTF_8));

        List<String> cmds = new ArrayList<>(spec.commandLine().getParseResult().originalArgs());

        if (background) {
            cmds.remove("--background=true");
            cmds.remove("--background");
        }
        if (repos != null) {
            if (!VersionHelper.isGE(v, "3.18.1")) {
                // --repos is not supported in 3.18.0 or older, so remove
                cmds.remove("--repos=" + repos);
            }
        }

        cmds.remove("--camel-version=" + camelVersion);
        // need to use jbang command to specify camel version
        List<String> jbangArgs = new ArrayList<>();
        jbangArgs.add("jbang");
        jbangArgs.add(CommandLineHelper.CAMEL_JBANG_WORK_DIR + "/CustomCamelJBang.java");

        jbangArgs.addAll(cmds);

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(jbangArgs);
        if (background) {
            Process p = pb.start();
            this.spawnPid = p.pid();
            if (!silentRun && !transformRun && !transformMessageRun) {
                printer().println("Running Camel integration: " + name + " (version: " + camelVersion
                                  + ") in background with PID: " + p.pid());
            }
            return 0;
        } else {
            pb.inheritIO(); // run in foreground (with IO so logs are visible)
            Process p = pb.start();
            this.spawnPid = p.pid();
            // wait for that process to exit as we run in foreground
            return p.waitFor();
        }
    }

    protected int runKameletMain(KameletMain main) throws Exception {
        main.start();
        main.run();

        // cleanup and delete log file
        if (logFile != null) {
            FileUtil.deleteFile(logFile);
        }

        return main.getExitCode();
    }

    private String loadFromCode(String code) throws IOException {
        String fn = CommandLineHelper.CAMEL_JBANG_WORK_DIR + "/CodeRoute.java";
        InputStream is = Run.class.getClassLoader().getResourceAsStream("templates/code-java.tmpl");
        String content = IOHelper.loadText(is);
        IOHelper.close(is);
        // need to replace single quote as double quotes and end with semicolon
        code = code.replace("'", "\"");
        code = code.trim();
        if (!code.endsWith(";")) {
            code = code + ";";
        }
        content = content.replaceFirst("\\{\\{ \\.Name }}", "CodeRoute");
        content = content.replaceFirst("\\{\\{ \\.Code }}", code);
        Files.write(Paths.get(fn), content.getBytes(StandardCharsets.UTF_8));
        return "file:" + fn;
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

    private String loadFromClipboard(String file) throws UnsupportedFlavorException, IOException {
        // run from clipboard (not real file exists)
        String ext = FileUtil.onlyExt(file, true);
        if (ext == null || ext.isEmpty()) {
            throw new IllegalArgumentException(
                    "When running from clipboard, an extension is required to let Camel know what kind of file to use");
        }
        Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
        Object t = c.getData(DataFlavor.stringFlavor);
        if (t != null) {
            String fn = CLIPBOARD_GENERATED_FILE + "." + ext;
            if ("java".equals(ext)) {
                String fqn = determineClassName(t.toString());
                if (fqn == null) {
                    throw new IllegalArgumentException(
                            "Cannot determine the Java class name from the source in the clipboard");
                }
                // drop package in file name
                String cn = fqn;
                if (fqn.contains(".")) {
                    cn = cn.substring(cn.lastIndexOf('.') + 1);
                }
                fn = cn + ".java";
            }
            Files.write(Paths.get(fn), t.toString().getBytes(StandardCharsets.UTF_8));
            file = "file:" + fn;
        }
        return file;
    }

    private KameletMain createMainInstance() {
        KameletMain main;
        if (localKameletDir == null || localKameletDir.isEmpty()) {
            main = new KameletMain();
        } else {
            StringJoiner sj = new StringJoiner(",");
            String[] parts = localKameletDir.split(",");
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
            main = new KameletMain(sj.toString());
            writeSettings("camel.jbang.localKameletDir", sj.toString());
        }
        return main;
    }

    private void configureLogging() {
        if (silentRun) {
            // do not configure logging
        } else if (logging) {
            RuntimeUtil.configureLog(loggingLevel, loggingColor, loggingJson, scriptRun, false, loggingConfigPath);
            writeSettings("loggingLevel", loggingLevel);
            writeSettings("loggingColor", loggingColor ? "true" : "false");
            writeSettings("loggingJson", loggingJson ? "true" : "false");
            if (!scriptRun) {
                // remember log file
                String name = RuntimeUtil.getPid() + ".log";
                logFile = new File(CommandLineHelper.getCamelDir(), name);
                logFile.deleteOnExit();
            }
        } else {
            RuntimeUtil.configureLog("off", false, false, false, false, null);
            writeSettings("loggingLevel", "off");
        }
    }

    private void generateOpenApi() throws Exception {
        File file = Paths.get(openapi).toFile();
        if (!file.exists() && !file.isFile()) {
            throw new FileNotFoundException("Cannot find file: " + file);
        }

        ObjectMapper mapper;
        boolean yaml = file.getName().endsWith(".yaml") || file.getName().endsWith(".yml");
        if (yaml) {
            mapper = new YAMLMapper();
        } else {
            mapper = new ObjectMapper();
        }
        ObjectNode node = (ObjectNode) mapper.readTree(file);
        OpenApiDocument document = (OpenApiDocument) Library.readDocument(node);
        RuntimeUtil.setRootLoggingLevel("off");
        try {
            try (CamelContext context = new DefaultCamelContext()) {
                String out = RestDslGenerator.toYaml(document).generate(context, false);
                Files.write(Paths.get(OPENAPI_GENERATED_FILE), out.getBytes());
            }
        } finally {
            RuntimeUtil.setRootLoggingLevel(loggingLevel);
        }
    }

    private boolean knownFile(String file) throws Exception {
        // always include kamelets
        String ext = FileUtil.onlyExt(file, false);
        if ("kamelet.yaml".equals(ext)) {
            return true;
        }

        String ext2 = FileUtil.onlyExt(file, true);
        if (ext2 != null) {
            boolean github = file.startsWith("github:") || file.startsWith("https://github.com/")
                    || file.startsWith("https://gist.github.com/");
            // special for yaml or xml, as we need to check if they have camel or not
            if (!github && ("xml".equals(ext2) || "yaml".equals(ext2))) {
                // load content into memory
                try (FileInputStream fis = new FileInputStream(file)) {
                    if ("xml".equals(ext2)) {
                        XmlStreamDetector detector = new XmlStreamDetector(fis);
                        XmlStreamInfo info = detector.information();
                        if (!info.isValid()) {
                            return false;
                        }
                        return ACCEPTED_XML_ROOT_ELEMENTS.contains(info.getRootElementName());
                    } else {
                        String data = IOHelper.loadText(fis);
                        // also support Camel K integrations and Pipes. And KameletBinding for backward compatibility
                        return data.contains("- from:") || data.contains("- route:") || data.contains("- route-configuration:")
                                || data.contains("- rest:") || data.contains("- beans:")
                                || data.contains("KameletBinding")
                                || data.contains("Pipe")
                                || data.contains("kind: Integration");
                    }
                }
            }
            // if the ext is an accepted file then we include it as a potential route
            // (java files need to be included as route to support pojos/processors with routes)
            return Arrays.stream(ACCEPTED_FILE_EXT).anyMatch(e -> e.equalsIgnoreCase(ext2));
        } else {
            // assume match as it can be wildcard or dir
            return true;
        }
    }

    private boolean skipFile(String name) {
        if (name.startsWith("github:") || name.startsWith("https://github.com/")
                || name.startsWith("https://gist.github.com/")) {
            return false;
        }

        // flatten file
        name = FileUtil.stripPath(name);

        if (OPENAPI_GENERATED_FILE.equals(name)) {
            return false;
        }
        if ("pom.xml".equalsIgnoreCase(name)) {
            return true;
        }
        if ("build.gradle".equalsIgnoreCase(name)) {
            return true;
        }
        if ("camel-runner.jar".equals(name)) {
            return true;
        }
        if ("docker-compose.yml".equals(name) || "docker-compose.yaml".equals(name) || "compose.yml".equals(name)
                || "compose.yaml".equals(name)) {
            return true;
        }
        if (name.equals("NOTICE.txt") || name.equals("LICENSE.txt")) {
            return true;
        }

        if (name.startsWith(".")) {
            // relative file is okay, otherwise we assume it's a hidden file
            boolean ok = name.startsWith("..") || name.startsWith("./");
            if (!ok) {
                return true;
            }
        }

        // is the file excluded?
        if (isExcluded(name, exclude)) {
            return true;
        }

        // skip dirs
        File f = new File(name);
        if (f.exists() && f.isDirectory()) {
            return true;
        }

        if (FileUtil.onlyExt(name) == null) {
            return true;
        }

        String on = FileUtil.onlyName(name, true);
        on = on.toLowerCase(Locale.ROOT);
        if (on.startsWith("readme")) {
            return true;
        }

        return false;
    }

    private static boolean isExcluded(String name, String exclude) {
        if (exclude != null) {
            for (String pattern : exclude.split(",")) {
                pattern = pattern.trim();
                if (AntPathMatcher.INSTANCE.match(pattern, name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean jkubeFile(String name) {
        return name.endsWith(".jkube.yaml") || name.endsWith(".jkube.yml");
    }

    private void writeSettings(String key, String value) {
        FileOutputStream fos = null;
        try {
            // use java.util.Properties to ensure the value is escaped correctly
            Properties prop = new Properties();
            prop.setProperty(key, value);
            StringWriter sw = new StringWriter();
            prop.store(sw, null);

            File runSettings = new File(CommandLineHelper.getWorkDir(), RUN_SETTINGS_FILE);
            fos = new FileOutputStream(runSettings, true);

            String[] lines = sw.toString().split(System.lineSeparator());
            for (String line : lines) {
                // properties store timestamp as comment which we want to skip
                if (!line.startsWith("#")) {
                    fos.write(line.getBytes(StandardCharsets.UTF_8));
                    fos.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (Exception e) {
            // ignore
        } finally {
            IOHelper.close(fos);
        }
    }

    private static void removeDir(File d) {
        String[] list = d.list();
        if (list == null) {
            list = new String[0];
        }
        for (String s : list) {
            File f = new File(d, s);
            if (f.isDirectory()) {
                removeDir(f);
            } else {
                delete(f);
            }
        }
        delete(d);
    }

    private static void delete(File f) {
        if (!f.delete()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            if (!f.delete()) {
                f.deleteOnExit();
            }
        }
    }

    private static String determineClassName(String content) {
        Matcher matcher = PACKAGE_PATTERN.matcher(content);
        String pn = matcher.find() ? matcher.group(1) : null;

        matcher = CLASS_PATTERN.matcher(content);
        String cn = matcher.find() ? matcher.group(1) : null;

        String fqn;
        if (pn != null) {
            fqn = pn + "." + cn;
        } else {
            fqn = cn;
        }
        return fqn;
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

    private class RunDownloadListener implements DownloadListener {
        final Set<String> downloaded = new HashSet<>();
        final Set<String> repos = new HashSet<>();
        final Set<String> kamelets = new HashSet<>();
        final Set<String> modelines = new HashSet<>();

        @Override
        public void onDownloadDependency(String groupId, String artifactId, String version) {
            String line = "mvn:" + groupId + ":" + artifactId;
            if (version != null) {
                line += ":" + version;
            }
            if (!downloaded.contains(line)) {
                writeSettings("dependency", line);
                downloaded.add(line);
            }
        }

        @Override
        public void onAlreadyDownloadedDependency(String groupId, String artifactId, String version) {
            // we want to register everything
            onDownloadDependency(groupId, artifactId, version);
        }

        @Override
        public void onExtraRepository(String repo) {
            if (!repos.contains(repo)) {
                writeSettings("repository", repo);
                repos.add(repo);
            }
        }

        @Override
        public void onLoadingKamelet(String name) {
            if (!kamelets.contains(name)) {
                writeSettings("kamelet", name);
                kamelets.add(name);
            }
        }

        @Override
        public void onLoadingModeline(String key, String value) {
            String line = key + "=" + value;
            if (!modelines.contains(line)) {
                writeSettings("modeline", line);
                modelines.add(line);
            }
        }
    }

    static class FilesConsumer extends ParameterConsumer<Run> {
        @Override
        protected void doConsumeParameters(Stack<String> args, Run cmd) {
            String arg = args.pop();
            cmd.files.add(arg);
        }
    }

    static class DebugConsumer extends ParameterConsumer<Run> {
        private static final Pattern DEBUG_ARG_VALUE_PATTERN = Pattern.compile("\\d+|true|false");

        @Override
        protected void doConsumeParameters(Stack<String> args, Run cmd) {
            String arg = args.isEmpty() ? "" : args.peek();
            if (DEBUG_ARG_VALUE_PATTERN.asPredicate().test(arg)) {
                // The value matches with the expected format so let's assume that it is a debug argument value
                args.pop();
            } else {
                // Here we assume that the value is not a debug argument value so let's simply enable the debug mode
                arg = "true";
            }
            cmd.jvmDebugPort = parseJvmDebugPort(arg);
        }

        @Override
        protected boolean failIfEmptyArgs() {
            return false;
        }
    }
}
