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

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.openapi.models.OasDocument;
import org.apache.camel.CamelContext;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.generator.openapi.RestDslGenerator;
import org.apache.camel.impl.lw.LightweightCamelContext;
import org.apache.camel.main.KameletMain;
import org.apache.camel.main.download.DownloadListener;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static org.apache.camel.dsl.jbang.core.common.GistHelper.asGistSingleUrl;
import static org.apache.camel.dsl.jbang.core.common.GistHelper.fetchGistUrls;
import static org.apache.camel.dsl.jbang.core.common.GitHubHelper.asGithubSingleUrl;
import static org.apache.camel.dsl.jbang.core.common.GitHubHelper.fetchGithubUrls;

@Command(name = "run", description = "Run as local Camel integration")
class Run extends CamelCommand {

    public static final String WORK_DIR = ".camel-jbang";
    public static final String RUN_SETTINGS_FILE = "camel-jbang-run.properties";

    private static final String[] ACCEPTED_FILE_EXT
            = new String[] { "java", "groovy", "js", "jsh", "kts", "xml", "yaml" };

    private static final String OPENAPI_GENERATED_FILE = ".camel-jbang/generated-openapi.yaml";
    private static final String CLIPBOARD_GENERATED_FILE = ".camel-jbang/generated-clipboard";

    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "^\\s*package\\s+([a-zA-Z][.\\w]*)\\s*;.*$", Pattern.MULTILINE);

    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "^\\s*public class\\s+([a-zA-Z0-9]*)[\\s+|;].*$", Pattern.MULTILINE);

    private boolean silentRun;
    private boolean pipeRun;

    //CHECKSTYLE:OFF
    @Parameters(description = "The Camel file(s) to run. If no files specified then application.properties is used as source for which files to run.",
            arity = "0..9", paramLabel = "<files>", parameterConsumer = FilesConsumer.class)
    Path[] filePaths; // Defined only for file path completion; the field never used

    List<String> files = new ArrayList<>();

    @Option(names = { "--profile" }, scope = CommandLine.ScopeType.INHERIT, defaultValue = "application",
            description = "Profile to use, which refers to loading properties file with the given profile name. By default application.properties is loaded.")
    String profile;

    @Option(names = {
            "--dep", "--deps" }, description = "Add additional dependencies (Use commas to separate multiple dependencies)")
    String dependencies;

    @Option(names = { "--repos" }, description = "Additional maven repositories for download on-demand (Use commas to separate multiple repositories)")
    String repos;

    @Option(names = { "--maven-settings" }, description = "Optional location of maven setting.xml file to configure servers, repositories, mirrors and proxies." +
            " If set to \"false\", not even the default ~/.m2/settings.xml will be used.")
    String mavenSettings;

    @Option(names = { "--maven-settings-security" }, description = "Optional location of maven settings-security.xml file to decrypt settings.xml")
    String mavenSettingsSecurity;

    @Option(names = { "--fresh" }, description = "Make sure we use fresh (i.e. non-cached) resources")
    boolean fresh;

    @Option(names = { "--download" }, defaultValue = "true", description = "Whether to allow automatic downloading JAR dependencies (over the internet)")
    boolean download = true;

    @Option(names = { "--name" }, defaultValue = "CamelJBang", description = "The name of the Camel application")
    String name;

    @Option(names = { "--logging" }, defaultValue = "true", description = "Can be used to turn off logging")
    boolean logging = true;

    @Option(names = { "--logging-level" }, defaultValue = "info", description = "Logging level")
    String loggingLevel;

    @Option(names = { "--logging-color" }, defaultValue = "true", description = "Use colored logging")
    boolean loggingColor = true;

    @Option(names = { "--logging-json" }, description = "Use JSON logging (ECS Layout)")
    boolean loggingJson;

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
            description = "Load properties file for route placeholders (ex. /path/to/file.properties")
    String propertiesFiles;

    @Option(names = { "-p", "--prop", "--property" }, description = "Additional properties (override existing)", arity = "0")
    String[] property;

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

    @Option(names = { "--modeline" }, defaultValue = "true", description = "Enables Camel-K style modeline")
    boolean modeline = true;

    @Option(names = { "--open-api" }, description = "Adds an OpenAPI spec from the given file")
    String openapi;

    @Option(names = { "--code" }, description = "Run the given string as Java DSL route")
    String code;

    public Run(CamelJBangMain main) {
        super(main);
    }

    //CHECKSTYLE:ON

    public String getProfile() {
        return profile;
    }

    @Override
    public Integer call() throws Exception {
        return run();
    }

    protected Integer runSilent() throws Exception {
        // just boot silently and exit
        silentRun = true;
        return run();
    }

    protected Integer runPipe(String file) throws Exception {
        this.files.add(file);
        pipeRun = true;
        return run();
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
        File work = new File(WORK_DIR);
        removeDir(work);
        work.mkdirs();

        Properties profileProperties = null;
        File profilePropertiesFile = new File(getProfile() + ".properties");
        if (profilePropertiesFile.exists()) {
            profileProperties = loadProfileProperties(profilePropertiesFile);
            // logging level/color may be configured in the properties file
            loggingLevel = profileProperties.getProperty("loggingLevel", loggingLevel);
            loggingColor
                    = "true".equals(profileProperties.getProperty("loggingColor", loggingColor ? "true" : "false"));
            loggingJson
                    = "true".equals(profileProperties.getProperty("loggingJson", loggingJson ? "true" : "false"));
            if (propertiesFiles == null) {
                propertiesFiles = "file:" + profilePropertiesFile.getName();
            } else {
                propertiesFiles = propertiesFiles + ",file:" + profilePropertiesFile.getName();
            }
            repos = profileProperties.getProperty("camel.jbang.repos", repos);
            mavenSettings = profileProperties.getProperty("camel.jbang.maven-settings", mavenSettings);
            mavenSettingsSecurity = profileProperties.getProperty("camel.jbang.maven-settings-security", mavenSettingsSecurity);
            openapi = profileProperties.getProperty("camel.jbang.openApi", openapi);
            download = "true".equals(profileProperties.getProperty("camel.jbang.download", download ? "true" : "false"));
        }

        // generate open-api early
        if (openapi != null) {
            generateOpenApi();
        }
        // route code as option
        if (code != null) {
            // store code in temporary file
            String codeFile = loadFromCode(code);
            // use code as first file
            files.add(0, codeFile);
        }

        // if no specific file to run then try to auto-detect
        if (files.isEmpty()) {
            String routes = profileProperties != null ? profileProperties.getProperty("camel.main.routesIncludePattern") : null;
            if (routes == null) {
                if (!silentRun) {
                    System.out.println("Cannot run because " + getProfile() + ".properties file does not exist");
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

        // configure logging first
        configureLogging();

        final KameletMain main = createMainInstance();

        main.setRepos(repos);
        main.setDownload(download);
        main.setFresh(fresh);
        main.setMavenSettings(mavenSettings);
        main.setMavenSettingsSecurity(mavenSettingsSecurity);
        main.setDownloadListener(new RunDownloadListener());
        main.setAppName("Apache Camel (JBang)");

        writeSetting(main, profileProperties, "camel.main.sourceLocationEnabled", "true");
        if (dev) {
            writeSetting(main, profileProperties, "camel.main.routesReloadEnabled", "true");
            // allow quick shutdown during development
            writeSetting(main, profileProperties, "camel.main.shutdownTimeout", "5");
        }
        if (trace) {
            writeSetting(main, profileProperties, "camel.main.tracing", "true");
        }
        if (modeline) {
            writeSetting(main, profileProperties, "camel.main.modeline", "true");
        }
        writeSetting(main, profileProperties, "camel.jbang.openApi", openapi);
        writeSetting(main, profileProperties, "camel.jbang.repos", repos);
        writeSetting(main, profileProperties, "camel.jbang.health", health ? "true" : "false");
        writeSetting(main, profileProperties, "camel.jbang.console", console ? "true" : "false");
        writeSetting(main, profileProperties, "camel.main.routesCompileDirectory", WORK_DIR);
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
            // enable stub in silent mode so we do not use real components
            main.setStub(true);
            // do not run for very long in silent run
            main.addInitialProperty("camel.main.autoStartup", "false");
            main.addInitialProperty("camel.main.durationMaxSeconds", "1");
        } else if (pipeRun) {
            // auto terminate if being idle
            main.addInitialProperty("camel.main.durationMaxIdleSeconds", "1");
        }
        writeSetting(main, profileProperties, "camel.main.durationMaxMessages",
                () -> maxMessages > 0 ? String.valueOf(maxMessages) : null);
        writeSetting(main, profileProperties, "camel.main.durationMaxSeconds",
                () -> maxSeconds > 0 ? String.valueOf(maxSeconds) : null);
        writeSetting(main, profileProperties, "camel.main.durationMaxIdleSeconds",
                () -> maxIdleSeconds > 0 ? String.valueOf(maxIdleSeconds) : null);
        writeSetting(main, profileProperties, "camel.jbang.platform-http.port",
                () -> port > 0 ? String.valueOf(port) : null);
        writeSetting(main, profileProperties, "camel.jbang.jfr", jfr || jfrProfile != null ? "jfr" : null);
        writeSetting(main, profileProperties, "camel.jbang.jfr-profile", jfrProfile != null ? jfrProfile : null);

        StringJoiner js = new StringJoiner(",");
        StringJoiner sjReload = new StringJoiner(",");
        StringJoiner sjClasspathFiles = new StringJoiner(",");
        StringJoiner sjKamelets = new StringJoiner(",");

        // include generated openapi to files to run
        if (openapi != null) {
            files.add(OPENAPI_GENERATED_FILE);
        }

        for (String file : files) {
            if (file.startsWith("clipboard") && !(new File(file).exists())) {
                file = loadFromClipboard(file);
            } else if (skipFile(file)) {
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
        if (dev && sjReload.length() > 0) {
            String reload = sjReload.toString();
            main.addInitialProperty("camel.main.routesReloadEnabled", "true");
            // use current dir, however if we run a file that are in another folder, then we should track that folder instead
            String reloadDir = ".";
            for (String r : reload.split(",")) {
                String path = FileUtil.onlyPath(r);
                if (path != null) {
                    reloadDir = path;
                    break;
                }
            }
            main.addInitialProperty("camel.main.routesReloadDirectory", reloadDir);
            main.addInitialProperty("camel.main.routesReloadPattern", reload);
            main.addInitialProperty("camel.main.routesReloadDirectoryRecursive", isReloadRecursive(reload) ? "true" : "false");
            // do not shutdown the JVM but stop routes when max duration is triggered
            main.addInitialProperty("camel.main.durationMaxAction", "stop");
        }

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

        main.start();
        main.run();

        return main.getExitCode();
    }

    private String loadFromCode(String code) throws IOException {
        String fn = WORK_DIR + "/CodeRoute.java";
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

    private KameletMain createMainInstance() throws Exception {
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
            RuntimeUtil.configureLog(loggingLevel, loggingColor, loggingJson, pipeRun, false);
            writeSettings("loggingLevel", loggingLevel);
            writeSettings("loggingColor", loggingColor ? "true" : "false");
            writeSettings("loggingJson", loggingJson ? "true" : "false");
        } else {
            RuntimeUtil.configureLog("off", false, false, false, false);
            writeSettings("loggingLevel", "off");
        }
    }

    private void generateOpenApi() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode node = mapper.readTree(Paths.get(openapi).toFile());
        OasDocument document = (OasDocument) Library.readDocument(node);
        Configurator.setRootLevel(Level.OFF);
        try (CamelContext context = new LightweightCamelContext()) {
            String out = RestDslGenerator.toYaml(document).generate(context, false);
            Files.write(Paths.get(OPENAPI_GENERATED_FILE), out.getBytes());
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
                    String data = IOHelper.loadText(fis);
                    if ("xml".equals(ext2)) {
                        return data.contains("<routes") || data.contains("<routeConfiguration") || data.contains("<rests");
                    } else {
                        // also support Camel K integrations and Kamelet bindings
                        return data.contains("- from:") || data.contains("- route:") || data.contains("- route-configuration:")
                                || data.contains("- rest:") || data.contains("KameletBinding")
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
        if (OPENAPI_GENERATED_FILE.equals(name)) {
            return false;
        }
        if (name.startsWith(".")) {
            return true;
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

        // skip dirs
        File f = new File(name);
        if (f.exists() && f.isDirectory()) {
            return true;
        }

        String on = FileUtil.onlyName(name, true);
        on = on.toLowerCase(Locale.ROOT);
        if (on.startsWith("readme")) {
            return true;
        }

        return false;
    }

    private void writeSettings(String key, String value) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(WORK_DIR + "/" + RUN_SETTINGS_FILE, true);
            String line = key + "=" + value;
            fos.write(line.getBytes(StandardCharsets.UTF_8));
            fos.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
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
                // Ignore Exception
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

}
