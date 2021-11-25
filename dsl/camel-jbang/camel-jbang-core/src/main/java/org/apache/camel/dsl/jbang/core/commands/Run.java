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
import java.nio.file.FileSystems;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.main.KameletMain;
import org.apache.camel.support.ResourceHelper;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "run", description = "Run a Kamelet")
class Run implements Callable<Integer> {
    private CamelContext context;
    private File lockFile;
    private ScheduledExecutorService executor;

    @Parameters(description = "The path to the kamelet binding", arity = "0..1")
    private String binding;

    //CHECKSTYLE:OFF
    @Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested;
    //CHECKSTYLE:ON

    @Option(names = { "--name" }, defaultValue = "CamelJBang", description = "The name of the Camel application")
    private String name;

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

    @Option(names = { "--properties" },
            description = "Load properties file for route placeholders (ex. /path/to/file.properties")
    private String propertiesFiles;

    @Option(names = { "--file-lock" }, defaultValue = "true",
            description = "Whether to create a temporary file lock, which upon deleting triggers this process to terminate")
    private boolean fileLock = true;

    @Option(names = { "--local-kamelet-dir" },
            description = "Load Kamelets from a local directory instead of resolving them from the classpath or GitHub")
    private String localKameletDir;

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
            System.out.println("Removing file " + lockFile);
            if (!lockFile.delete()) {
                System.err.println("Failed to remove lock file " + lockFile);
            }
        }

        return 0;
    }

    private int run() throws Exception {
        // configure logging first
        RuntimeUtil.configureLog(loggingLevel);

        KameletMain main;

        if (localKameletDir == null) {
            main = new KameletMain();
        } else {
            main = new KameletMain("file://" + localKameletDir);
        }

        main.addInitialProperty("camel.main.name", name);
        // shutdown quickly
        main.addInitialProperty("camel.main.shutdownTimeout", "5");
        // turn off lightweight if we have routes reload enabled
        main.addInitialProperty("camel.main.routesReloadEnabled", reload ? "true" : "false");

        // durations
        if (maxMessages > 0) {
            main.addInitialProperty("camel.main.durationMaxMessages", String.valueOf(maxMessages));
        }
        if (maxSeconds > 0) {
            main.addInitialProperty("camel.main.durationMaxSeconds", String.valueOf(maxSeconds));
        }
        if (maxIdleSeconds > 0) {
            main.addInitialProperty("camel.main.durationMaxIdleSeconds", String.valueOf(maxIdleSeconds));
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

        if (!ResourceHelper.hasScheme(binding) && !binding.startsWith("github:")) {
            binding = "file:" + binding;
        }
        main.addInitialProperty("camel.main.routesIncludePattern", binding);

        if (binding.startsWith("file:")) {
            // check if file exist
            File bindingFile = new File(binding.substring(5));
            if (!bindingFile.exists() && !bindingFile.isFile()) {
                System.err.println("The binding file does not exist");
                return 1;
            }

            // we can only reload if file based
            if (reload) {
                main.addInitialProperty("camel.main.routesReloadEnabled", "true");
                main.addInitialProperty("camel.main.routesReloadDirectory", ".");
                // skip file: as prefix
                main.addInitialProperty("camel.main.routesReloadPattern", binding.substring(5));
                // do not shutdown the JVM but stop routes when max duration is triggered
                main.addInitialProperty("camel.main.durationMaxAction", "stop");
            }
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
            main.addInitialProperty("camel.component.properties.location", locations.toString());
        }

        System.out.println("Starting CamelJBang");
        main.start();

        context = main.getCamelContext();

        main.run();

        int code = main.getExitCode();
        return code;
    }

    public File createLockFile() throws IOException {
        File lockFile = File.createTempFile(".run", ".camel.lock", new File("."));

        System.out.printf("A new lock file was created, delete the file to stop running:%n%s%n",
                lockFile.getAbsolutePath());
        lockFile.deleteOnExit();

        return lockFile;
    }
}
