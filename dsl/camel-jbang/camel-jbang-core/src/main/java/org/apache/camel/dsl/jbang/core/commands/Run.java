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
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.main.KameletMain;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static org.apache.camel.dsl.jbang.core.commands.GitHubHelper.asGithubSingleUrl;
import static org.apache.camel.dsl.jbang.core.commands.GitHubHelper.fetchGithubUrls;

@Command(name = "run", description = "Run Camel")
class Run implements Callable<Integer> {

    public static final String WORK_DIR = ".camel-jbang";
    public static final String RUN_SETTINGS_FILE = "camel-jbang-run.properties";

    private static final String[] ACCEPTED_FILE_EXT
            = new String[] { "properties", "java", "groovy", "js", "jsh", "kts", "xml", "yaml" };

    private FileOutputStream settings;
    private CamelContext context;
    private File lockFile;
    private ScheduledExecutorService executor;

    @Parameters(description = "The Camel file(s) to run", arity = "1")
    private String[] files;

    //CHECKSTYLE:OFF
    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested;
    //CHECKSTYLE:ON

    @Option(names = {
            "--deps" }, description = "Add additional dependencies (Use commas to separate them).")
    private String dependencies;

    @Option(names = { "--name" }, defaultValue = "CamelJBang", description = "The name of the Camel application")
    private String name;

    @Option(names = { "--logging" }, description = "Can be used to turn off logging")
    private boolean logging = true;

    @Option(names = { "--logging-level" }, defaultValue = "info", description = "Logging level")
    private String loggingLevel;

    @Option(names = { "--stop" }, description = "Stop all running instances of Camel JBang")
    private boolean stopRequested;

    @Option(names = { "--max-messages" }, defaultValue = "0", description = "Max number of messages to process before stopping")
    private int maxMessages;

    @Option(names = { "--max-seconds" }, defaultValue = "0", description = "Max seconds to run before stopping")
    private int maxSeconds;

    @Option(names = { "--max-idle-seconds" }, defaultValue = "0",
            description = "For how long time in seconds Camel can be idle before stopping")
    private int maxIdleSeconds;

    @Option(names = { "--reload" }, description = "Enables live reload when source file is changed (saved)")
    private boolean reload;

    @Option(names = { "--trace" }, description = "Enables trace logging of the routed messages")
    private boolean trace;

    @Option(names = { "--properties" },
            description = "Load properties file for route placeholders (ex. /path/to/file.properties")
    private String propertiesFiles;

    @Option(names = { "-p", "--prop", "--property" }, description = "Additional properties (override existing)", arity = "0")
    private String[] property;

    @Option(names = { "--file-lock" },
            description = "Whether to create a temporary file lock, which upon deleting triggers this process to terminate")
    private boolean fileLock;

    @Option(names = { "--jfr" },
            description = "Enables Java Flight Recorder saving recording to disk on exit")
    private boolean jfr;

    @Option(names = { "--jfr-profile" },
            description = "Java Flight Recorder profile to use (such as default or profile)")
    private String jfrProfile;

    @Option(names = { "--local-kamelet-dir" },
            description = "Local directory for loading Kamelets (takes precedence)")
    private String localKameletDir;

    @Option(names = { "--port" }, description = "Embeds a local HTTP server on this port")
    private int port;

    @Option(names = { "--console" }, description = "Developer console at /q/dev on local HTTP server (port 8080 by default)")
    private boolean console;

    @Option(names = { "--health" }, description = "Health check at /q/health on local HTTP server (port 8080 by default)")
    private boolean health;

    @Option(names = { "--modeline" }, description = "Enables Camel-K style modeline")
    private boolean modeline = true;

    @Override
    public Integer call() throws Exception {
        if (stopRequested) {
            stop();
            return 0;
        } else {
            return run();
        }
    }

    private int stop() {
        File currentDir = new File(".");

        File[] lockFiles = currentDir.listFiles(f -> f.getName().endsWith(".camel.lock"));

        for (File lockFile : lockFiles) {
            if (logging) {
                System.out.println("Removing file " + lockFile);
            }
            if (!lockFile.delete()) {
                if (logging) {
                    System.err.println("Failed to remove lock file " + lockFile);
                }
            }
        }

        return 0;
    }

    private int run() throws Exception {
        File work = new File(WORK_DIR);
        FileUtil.removeDir(work);
        work.mkdirs();

        settings = new FileOutputStream(WORK_DIR + "/" + RUN_SETTINGS_FILE, false);

        // configure logging first
        if (logging) {
            RuntimeUtil.configureLog(loggingLevel);
            writeSettings("loggingLevel", loggingLevel);
        } else {
            RuntimeUtil.configureLog("off");
            writeSettings("loggingLevel", "off");
        }

        KameletMain main;

        if (localKameletDir == null) {
            main = new KameletMain();
        } else {
            main = new KameletMain("file://" + localKameletDir);
            writeSettings("localKameletDir", localKameletDir);
        }

        final Set<String> downloaded = new HashSet<>();
        main.setDownloadListener((groupId, artifactId, version) -> {
            String line = "mvn:" + groupId + ":" + artifactId + ":" + version;
            if (!downloaded.contains(line)) {
                writeSettings("dependency", line);
                downloaded.add(line);
            }
        });
        main.setAppName("Apache Camel (JBang)");

        main.addInitialProperty("camel.main.name", name);
        writeSettings("camel.main.name", name);

        // shutdown quickly
        main.addInitialProperty("camel.main.shutdownTimeout", "5");
        writeSettings("camel.main.shutdownTimeout", "5");

        main.addInitialProperty("camel.main.routesReloadEnabled", reload ? "true" : "false");
        writeSettings("camel.main.routesReloadEnabled", reload ? "true" : "false");
        main.addInitialProperty("camel.main.sourceLocationEnabled", "true");
        writeSettings("camel.main.sourceLocationEnabled", "true");
        main.addInitialProperty("camel.main.tracing", trace ? "true" : "false");
        writeSettings("camel.main.tracing", trace ? "true" : "false");
        main.addInitialProperty("camel.main.modeline", modeline ? "true" : "false");
        writeSettings("camel.main.modeline", modeline ? "true" : "false");
        main.addInitialProperty("camel.jbang.work-directory", WORK_DIR);
        writeSettings("camel.jbang.work-directory", WORK_DIR);

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

        if (maxMessages > 0) {
            main.addInitialProperty("camel.main.durationMaxMessages", String.valueOf(maxMessages));
            writeSettings("camel.main.durationMaxMessages", String.valueOf(maxMessages));
        }
        if (maxSeconds > 0) {
            main.addInitialProperty("camel.main.durationMaxSeconds", String.valueOf(maxSeconds));
            writeSettings("camel.main.durationMaxSeconds", String.valueOf(maxSeconds));
        }
        if (maxIdleSeconds > 0) {
            main.addInitialProperty("camel.main.durationMaxIdleSeconds", String.valueOf(maxIdleSeconds));
            writeSettings("camel.main.durationMaxIdleSeconds", String.valueOf(maxIdleSeconds));
        }
        if (port > 0) {
            main.addInitialProperty("camel.jbang.platform-http.port", String.valueOf(port));
            writeSettings("camel.jbang.platform-http.port", String.valueOf(port));
        }
        if (console) {
            main.addInitialProperty("camel.jbang.console", "true");
            writeSettings("camel.jbang.console", "true");
        }
        if (health) {
            main.addInitialProperty("camel.jbang.health", "true");
            writeSettings("camel.jbang.health", "true");
        }

        if (jfr) {
            main.addInitialProperty("camel.jbang.jfr", "jfr");
            writeSettings("camel.jbang.jfr", "jfr");
        }
        if (jfrProfile != null) {
            // turn on jfr if a profile was specified
            main.addInitialProperty("camel.jbang.jfr", "jfr");
            writeSettings("camel.jbang.jfr", "jfr");
            main.addInitialProperty("camel.jbang.jfr-profile", jfrProfile);
            writeSettings("camel.jbang.jfr-profile", jfrProfile);
        }
        if (dependencies != null) {
            main.addInitialProperty("camel.jbang.dependencies", dependencies);
            writeSettings("camel.jbang.dependencies", dependencies);
        }

        if (fileLock) {
            lockFile = createLockFile();
            if (!lockFile.exists()) {
                throw new IllegalStateException("Lock file does not exists: " + lockFile);
            }

            // to trigger shutdown on file lock deletion
            executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleWithFixedDelay(() -> {
                // if the lock file is deleted then stop
                if (!lockFile.exists()) {
                    context.stop();
                }
            }, 1000, 1000, TimeUnit.MILLISECONDS);
        }

        StringJoiner js = new StringJoiner(",");
        StringJoiner sjReload = new StringJoiner(",");
        StringJoiner sjClasspathFiles = new StringJoiner(",");
        StringJoiner sjKamelets = new StringJoiner(",");

        for (String file : files) {

            if (skipFile(file)) {
                continue;
            }
            if (!knownFile(file)) {
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
                if (reload && file.startsWith("file:")) {
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
                String ext = FileUtil.onlyExt(file);
                boolean wildcard = FileUtil.onlyName(file, false).contains("*");
                if (ext != null && !wildcard) {
                    // it is a single file so map to
                    file = asGithubSingleUrl(file);
                } else {
                    StringJoiner routes = new StringJoiner(",");
                    StringJoiner kamelets = new StringJoiner(",");
                    StringJoiner properties = new StringJoiner(",");
                    fetchGithubUrls(file, routes, kamelets, properties);

                    if (routes.length() > 0) {
                        file = routes.toString();
                    }
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
                }
            }

            js.add(file);
            if (reload && file.startsWith("file:")) {
                // we can only reload if file based
                sjReload.add(file.substring(5));
            }
        }

        if (js.length() > 0) {
            main.addInitialProperty("camel.main.routesIncludePattern", js.toString());
            writeSettings("camel.main.routesIncludePattern", js.toString());
        }
        if (sjClasspathFiles.length() > 0) {
            main.addInitialProperty("camel.jbang.classpathFiles", sjClasspathFiles.toString());
            writeSettings("camel.jbang.classpathFiles", sjClasspathFiles.toString());
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
        }

        // we can only reload if file based
        if (reload && sjReload.length() > 0) {
            main.addInitialProperty("camel.main.routesReloadEnabled", "true");
            main.addInitialProperty("camel.main.routesReloadDirectory", ".");
            // skip file: as prefix
            main.addInitialProperty("camel.main.routesReloadPattern", sjReload.toString());
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
                locations.append(file).append(",");
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
        }

        main.start();

        context = main.getCamelContext();

        main.run();

        IOHelper.close(settings);

        int code = main.getExitCode();
        return code;
    }

    public File createLockFile() throws IOException {
        File lockFile = File.createTempFile(".run", ".camel.lock", new File("."));

        if (logging) {
            System.out.printf("A new lock file was created, delete the file to stop running:%n%s%n",
                    lockFile.getAbsolutePath());
        }
        lockFile.deleteOnExit();

        return lockFile;
    }

    private boolean knownFile(String file) {
        String ext = FileUtil.onlyExt(file, true);
        if (ext != null) {
            return Arrays.stream(ACCEPTED_FILE_EXT).anyMatch(e -> e.equalsIgnoreCase(ext));
        } else {
            // assume match as it can be wildcard or dir
            return true;
        }
    }

    private boolean skipFile(String name) {
        if (name.startsWith(".")) {
            return true;
        }
        if ("pom.xml".equalsIgnoreCase(name)) {
            return true;
        }
        if ("build.gradle".equalsIgnoreCase(name)) {
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
        String line = key + "=" + value;
        try {
            settings.write(line.getBytes(StandardCharsets.UTF_8));
            settings.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // ignore
        }
    }

}
