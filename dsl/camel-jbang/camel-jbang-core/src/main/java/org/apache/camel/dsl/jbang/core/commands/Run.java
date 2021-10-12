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
import java.util.concurrent.Callable;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.main.KameletMain;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "run", description = "Run a Kamelet")
class Run implements Callable<Integer> {
    private CamelContext context;

    @Parameters(description = "The path to the kamelet binding", arity = "0..1")
    private String binding;

    //CHECKSTYLE:OFF
    @Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested = false;
    //CHECKSTYLE:ON

    @Option(names = { "--debug-level" }, defaultValue = "info", description = "Default debug level")
    private String debugLevel;

    @Option(names = { "--stop" }, description = "Stop all running instances of Camel JBang")
    private boolean stopRequested;

    @Option(names = { "--max-messages" }, defaultValue = "0", description = "Max number of messages to process before stopping")
    private int maxMessages;

    class ShutdownRoute extends RouteBuilder {
        private File lockFile;

        public ShutdownRoute(File lockFile) {
            this.lockFile = lockFile;
        }

        public void configure() {
            fromF("file-watch://%s?events=DELETE&antInclude=%s", lockFile.getParent(), lockFile.getName())
                    .process(p -> context.shutdown());
        }
    }

    @Override
    public Integer call() throws Exception {
        System.setProperty("camel.main.name", "CamelJBang");

        if (stopRequested) {
            stop();
        } else {
            run();
        }

        return 0;
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
        if (maxMessages > 0) {
            System.setProperty("camel.main.durationMaxMessages", String.valueOf(maxMessages));
        }

        System.setProperty("camel.main.routes-include-pattern", "file:" + binding);

        RuntimeUtil.configureLog(debugLevel);

        File bindingFile = new File(binding);
        if (!bindingFile.exists()) {
            System.err.println("The binding file does not exist");

            return 1;
        }

        System.out.println("Starting Camel JBang!");
        KameletMain main = new KameletMain();

        main.configure().addRoutesBuilder(new ShutdownRoute(createLockFile()));
        main.start();
        context = main.getCamelContext();

        main.run();
        return 0;
    }

    public File createLockFile() throws IOException {
        File lockFile = File.createTempFile(".run", ".camel.lock", new File("."));

        System.out.printf("A new lock file was created on %s. Delete this file to stop running%n",
                lockFile.getAbsolutePath());
        lockFile.deleteOnExit();

        return lockFile;
    }
}
