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
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystems;
import java.time.Duration;
import java.util.Arrays;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.main.KameletMain;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.AntPathMatcher;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "run", description = "Run Camel")
class Run implements Callable<Integer> {

    private static final String[] ACCEPTED_FILE_EXT
            = new String[] { "properties", "java", "groovy", "js", "jsh", "kts", "xml", "yaml" };

    private CamelContext context;
    private File lockFile;
    private ScheduledExecutorService executor;

    @Parameters(description = "The Camel file(s) to run", arity = "1")
    private String[] files;

    //CHECKSTYLE:OFF
    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested;
    //CHECKSTYLE:ON

    @Option(names = { "--dep", "--dependency" }, description = "Additional dependencies to add to the classpath", arity = "0")
    private String[] dependencies;

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

    @Option(names = { "--file-lock" }, defaultValue = "true",
            description = "Whether to create a temporary file lock, which upon deleting triggers this process to terminate")
    private boolean fileLock = true;

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
        // configure logging first
        if (logging) {
            RuntimeUtil.configureLog(loggingLevel);
        } else {
            RuntimeUtil.configureLog("off");
        }

        KameletMain main;

        if (localKameletDir == null) {
            main = new KameletMain();
        } else {
            main = new KameletMain("file://" + localKameletDir);
        }
        main.setAppName("Apache Camel (JBang)");

        main.addInitialProperty("camel.main.name", name);
        // shutdown quickly
        main.addInitialProperty("camel.main.shutdownTimeout", "5");
        // turn off lightweight if we have routes reload enabled
        main.addInitialProperty("camel.main.routesReloadEnabled", reload ? "true" : "false");
        main.addInitialProperty("camel.main.sourceLocationEnabled", "true");
        main.addInitialProperty("camel.main.tracing", trace ? "true" : "false");
        main.addInitialProperty("camel.main.modeline", modeline ? "true" : "false");

        if (maxMessages > 0) {
            main.addInitialProperty("camel.main.durationMaxMessages", String.valueOf(maxMessages));
        }
        if (maxSeconds > 0) {
            main.addInitialProperty("camel.main.durationMaxSeconds", String.valueOf(maxSeconds));
        }
        if (maxIdleSeconds > 0) {
            main.addInitialProperty("camel.main.durationMaxIdleSeconds", String.valueOf(maxIdleSeconds));
        }
        if (port > 0) {
            main.addInitialProperty("camel.jbang.platform-http.port", String.valueOf(port));
        }
        if (console) {
            main.addInitialProperty("camel.jbang.console", "true");
        }
        if (health) {
            main.addInitialProperty("camel.jbang.health", "true");
        }

        if (jfr) {
            main.addInitialProperty("camel.jbang.jfr", "jfr");
        }
        if (jfrProfile != null) {
            // turn on jfr if a profile was specified
            main.addInitialProperty("camel.jbang.jfr", "jfr");
            main.addInitialProperty("camel.jbang.jfr-profile", jfrProfile);
        }
        if (dependencies != null) {
            main.addInitialProperty("camel.jbang.dependencies", String.join(",", dependencies));
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

        for (String file : files) {

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

            // automatic map github https urls to github resolver
            if (file.startsWith("https://github.com/")) {
                String ext = FileUtil.onlyExt(file);
                boolean wildcard = FileUtil.onlyName(file, false).contains("*");
                if (ext != null && !wildcard) {
                    // it is a single file so map to
                    file = asGithubSingleUrl(file);
                } else {
                    StringJoiner files = new StringJoiner(",");
                    StringJoiner kamelets = new StringJoiner(",");
                    StringJoiner properties = new StringJoiner(",");
                    fetchGithubUrls(file, files, kamelets, properties);

                    if (files.length() > 0) {
                        file = files.toString();
                    }
                    if (properties.length() > 0) {
                        main.addInitialProperty("camel.component.properties.location", properties.toString());
                    }
                    if (kamelets.length() > 0) {
                        String loc = main.getInitialProperties().getProperty("camel.component.kamelet.location");
                        if (loc != null) {
                            loc = loc + "," + kamelets;
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
        main.addInitialProperty("camel.main.routesIncludePattern", js.toString());
        if (sjClasspathFiles.length() > 0) {
            main.addInitialProperty("camel.jbang.classpathFiles", sjClasspathFiles.toString());
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
        }

        main.start();

        context = main.getCamelContext();

        main.run();

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

    private static String asGithubSingleUrl(String url) throws Exception {
        // strip https://github.com/
        url = url.substring(19);
        // https://github.com/apache/camel-k/blob/main/examples/languages/routes.kts
        // https://github.com/apache/camel-k/blob/v1.7.0/examples/languages/routes.kts
        url = url.replaceFirst("/", ":");
        url = url.replaceFirst("/", ":");
        url = url.replaceFirst("blob/", "");
        url = url.replaceFirst("/", ":");
        return "github:" + url;
    }

    private static void fetchGithubUrls(String url, StringJoiner files, StringJoiner kamelets, StringJoiner properties)
            throws Exception {
        // this is a directory, so we need to query github which files are there and filter them

        // URL: https://api.github.com/repos/apache/camel-k/contents/examples/kamelets/kameletbindings
        // URL: https://api.github.com/repos/apache/camel-k/contents/examples/kamelets/kameletbindings?ref=v1.7.0
        // https://github.com/apache/camel-k/tree/main/examples/kamelets/kameletbindings
        // https://github.com/apache/camel-k/tree/v1.7.0/examples/kamelets/kameletbindings

        // strip https://github.com/
        url = url.substring(19);

        String[] parts = url.split("/");
        if (parts.length < 5) {
            return;
        }

        String org = parts[0];
        String repo = parts[1];
        String action = parts[2];
        String branch = parts[3];
        String path;
        String wildcard = null;
        StringJoiner sj = new StringJoiner("/");
        for (int i = 4; i < parts.length; i++) {
            if (i == parts.length - 1) {
                // last element uses wildcard to filter which files to include
                if (parts[i].contains("*")) {
                    wildcard = parts[i];
                    break;
                }
            }
            sj.add(parts[i]);
        }
        path = sj.toString();

        if ("tree".equals(action)) {
            // https://api.github.com/repos/apache/camel-k/contents/examples/kamelets/kameletbindings?ref=v1.7.0
            url = "https://api.github.com/repos/" + org + "/" + repo + "/contents/" + path;
            if (!"main".equals(branch) && !"master".equals(branch)) {
                url = url + "?ref=" + branch;
            }
        }

        downloadGithubFiles(url, wildcard, files, kamelets, properties);
    }

    private static void downloadGithubFiles(
            String url, String wildcard, StringJoiner files, StringJoiner kamelets, StringJoiner properties)
            throws Exception {

        // use JDK http client to call github api
        HttpClient hc = HttpClient.newHttpClient();
        HttpResponse<String> res = hc.send(HttpRequest.newBuilder(new URI(url)).timeout(Duration.ofSeconds(20)).build(),
                HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() == 200) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(res.body());
            for (JsonNode c : root) {
                String name = c.get("name").asText();
                String ext = FileUtil.onlyExt(name, false);
                boolean match = wildcard == null || AntPathMatcher.INSTANCE.match(wildcard, name, false);
                if (match) {
                    if ("kamelet.yaml".equalsIgnoreCase(ext)) {
                        String htmlUrl = c.get("html_url").asText();
                        String u = asGithubSingleUrl(htmlUrl);
                        kamelets.add(u);
                    } else if ("properties".equalsIgnoreCase(ext)) {
                        String htmlUrl = c.get("html_url").asText();
                        String u = asGithubSingleUrl(htmlUrl);
                        properties.add(u);
                    } else if ("java".equalsIgnoreCase(ext) || "xml".equalsIgnoreCase(ext) || "yaml".equalsIgnoreCase(ext)
                            || "groovy".equalsIgnoreCase(ext) || "js".equalsIgnoreCase(ext) || "jsh".equalsIgnoreCase(ext)
                            || "kts".equalsIgnoreCase(ext)) {
                        String htmlUrl = c.get("html_url").asText();
                        String u = asGithubSingleUrl(htmlUrl);
                        files.add(u);
                    }
                }
            }
        }
    }

    private boolean knownFile(String file) {
        String ext = FileUtil.onlyExt(file, true);
        return Arrays.stream(ACCEPTED_FILE_EXT).anyMatch(e -> e.equalsIgnoreCase(ext));
    }

}
