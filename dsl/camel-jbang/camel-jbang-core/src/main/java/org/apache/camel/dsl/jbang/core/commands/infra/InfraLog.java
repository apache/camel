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
package org.apache.camel.dsl.jbang.core.commands.infra;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListener;
import picocli.CommandLine;

@CommandLine.Command(name = "log", description = "Displays external service logs", sortOptions = false,
                     showDefaultValues = true)
public class InfraLog extends InfraBaseCommand {

    @CommandLine.Parameters(description = "Service name", arity = "0..2")
    private List<String> serviceName;

    private ExecutorService executorService;

    public InfraLog(CamelJBangMain main) {
        super(main);

        executorService = Executors.newFixedThreadPool(10);
    }

    @Override
    public Integer doCall() throws Exception {
        List<Future<?>> futures = new ArrayList<>();
        if (serviceName == null || serviceName.isEmpty()) {
            // Log everything
            for (File logFile : CommandLineHelper.getCamelDir().listFiles(
                    (dir, name) -> name.startsWith("infra-") && name.endsWith(".log"))) {

                String alias = logFile.getName().split("-")[1];

                createTailer(logFile, alias, futures);
            }

            if (futures.isEmpty()) {
                printer().println("There are no running services");

                return -1;
            }
        } else {
            String alias = serviceName.get(0);

            File logFile = Arrays.stream(CommandLineHelper.getCamelDir().listFiles(
                    (dir, name) -> name.startsWith("infra-" + alias + "-") && name.endsWith(".log")))
                    .findFirst()
                    .orElse(null);

            if (logFile == null) {
                printer().println("Log not found for service " + alias);

                return -1;
            }

            createTailer(logFile, alias, futures);
        }

        for (Future<?> future : futures) {
            // The future is done when the service is stopped/log file in .camel deleted
            while (!future.isDone()) {
                Thread.sleep(100);
            }
        }

        return 0;
    }

    private void createTailer(File logFile, String alias, List<Future<?>> futures) {
        Tailer tailer = Tailer.builder()
                .setFile(logFile)
                .setTailerListener(new StdoutTailerListener(alias))
                .get();

        Thread thread = new Thread(tailer);
        thread.setDaemon(true);
        futures.add(executorService.submit(thread));
    }

    class StdoutTailerListener implements TailerListener {

        private String suffix;
        private Tailer self;

        public StdoutTailerListener(String suffix) {
            this.suffix = suffix;
        }

        @Override
        public void fileNotFound() {
        }

        @Override
        public void fileRotated() {
        }

        @Override
        public void handle(Exception ex) {
            printer().println("The service " + suffix + " was stopped");
            this.self.close();
            Thread.currentThread().interrupt();
        }

        @Override
        public void handle(String line) {
            printer().println("[" + suffix + "] " + line);
        }

        @Override
        public void init(Tailer tailer) {
            this.self = tailer;
            try {
                StringBuilder sb = new StringBuilder();
                int linesToRead = 50;
                ReversedLinesFileReader fileReader = new ReversedLinesFileReader(tailer.getFile());
                for (int i = 0; i < linesToRead; i++) {
                    String line = fileReader.readLine();
                    if (line == null) {
                        break;
                    }

                    // prepend
                    sb.insert(0, "[" + suffix + "] " + line);
                }

                printer().println(sb.toString());
            } catch (IOException e) {
                printer().println("Error collecting logs");
                printer().printErr(e);
            }
        }
    }
}
