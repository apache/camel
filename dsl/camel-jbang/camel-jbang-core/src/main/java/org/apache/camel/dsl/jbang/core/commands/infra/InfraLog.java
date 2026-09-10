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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListener;
import picocli.CommandLine;

@CommandLine.Command(name = "log", description = "Displays external service logs", sortOptions = false,
                     showDefaultValues = true,
                     footer = {
                             "%nExamples:",
                             "  camel infra log",
                             "  camel infra log kafka" })
public class InfraLog extends InfraBaseCommand {

    @CommandLine.Parameters(description = "Service name", arity = "0..2")
    List<String> serviceName;

    @CommandLine.Option(names = { "--lines" }, defaultValue = "50",
                        description = "The number of lines from the end of the log to use as starting offset")
    private int logLines = 50;

    private ExecutorService executorService;

    public InfraLog(CamelJBangMain main) {
        super(main);

        executorService = Executors.newFixedThreadPool(10);
    }

    /**
     * Tails the log of every running instance, rather than the first log file that happens to match the alias. Two
     * instances of the same service each have their own log, so both are followed and their lines are prefixed with the
     * pid to tell them apart.
     */
    @Override
    public Integer doCall() throws Exception {
        String name = serviceName == null || serviceName.isEmpty() ? null : serviceName.get(0);

        List<RunningService> instances = findRunningServices(name);

        // only carry the pid in the prefix when there is more than one instance of that alias to disambiguate
        Map<String, Long> instancesPerAlias = instances.stream()
                .collect(Collectors.groupingBy(RunningService::alias, Collectors.counting()));

        List<Future<?>> futures = new ArrayList<>();
        for (RunningService instance : instances) {
            Path logFile = CommandLineHelper.getCamelDir().resolve(getLogFileName(instance.alias(), instance.pid()));
            if (!Files.isRegularFile(logFile)) {
                // the service has not written any log yet
                continue;
            }
            String prefix = instancesPerAlias.get(instance.alias()) > 1
                    ? instance.alias() + "-" + instance.pid() : instance.alias();
            createTailer(logFile.toFile(), prefix, futures);
        }

        if (futures.isEmpty()) {
            if (name != null) {
                printer().printErr("Log not found for service " + name);
            } else {
                printer().println("There are no running services");
            }
            return -1;
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
                .setTailFromEnd(true)
                .setTailerListener(new StdoutTailerListener(alias))
                .get();

        Thread thread = new Thread(tailer);
        thread.setDaemon(true);
        futures.add(executorService.submit(thread));
    }

    class StdoutTailerListener implements TailerListener {

        private final String suffix;
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
            try (ReversedLinesFileReader fileReader = ReversedLinesFileReader.builder().setFile(tailer.getFile()).get()) {
                List<String> lines = new ArrayList<>(logLines);
                for (int i = 0; i < logLines; i++) {
                    String line = fileReader.readLine();
                    if (line == null) {
                        break;
                    }
                    lines.add(0, line);
                }
                lines.forEach(this::handle);
            } catch (IOException e) {
                printer().printErr("Error initializing logs", e);
            }
        }
    }
}
