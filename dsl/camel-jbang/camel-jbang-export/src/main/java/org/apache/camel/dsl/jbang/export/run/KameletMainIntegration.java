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
package org.apache.camel.dsl.jbang.export.run;

import java.io.IOException;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.stream.Collectors;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import org.apache.camel.CamelContext;
import org.apache.camel.dsl.jbang.export.common.PreviousRun;
import org.apache.camel.dsl.jbang.export.common.Printer;
import org.apache.camel.main.KameletMain;
import org.apache.camel.util.FileUtil;

/**
 * An integration that runs in this JVM: the Camel Main of a run with the jbang runtime. It runs in the thread that
 * waits for it ({@link #waitFor()}), as camel run does.
 */
final class KameletMainIntegration implements RunningIntegration {

    /**
     * The exit code of a run that failed, such as one without routes: CliExitCode.FAILURE of camel-jbang-core.
     */
    static final int FAILURE = 1;

    private final KameletMain main;
    private final RunRequest request;
    private final RunContext context;
    private final Printer printer;
    private Recording recording;
    private PreviousRun.Keeper previousLog;
    private boolean ended;
    private volatile boolean stopped;

    KameletMainIntegration(KameletMain main, RunRequest request, RunContext context) {
        this.main = main;
        this.request = request;
        this.context = context;
        this.printer = context.getPrinter();
    }

    /**
     * Starts Camel.
     *
     * @return this integration, or a run that ended (no routes, or a route that did not load with
     *         {@link RunRequest#ignoreLoadingError})
     */
    RunningIntegration start() throws Exception {
        recording = startJfrRecording();
        if (request.logFile != null && request.keepPreviousLog) {
            // CAMEL-25784: the log is kept as the previous log of the integration when the run ends, and so are the
            // logs that runs which were killed left behind
            PreviousRun.keepOrphans();
            previousLog = new PreviousRun.Keeper(request.logFile, this::runName);
        }
        try {
            main.start();
        } catch (Exception e) {
            // such as a route that failed to start: noted as why the run ended
            ended(context.failureExitCode(e), failureOf(e));
            if (request.ignoreLoadingError) {
                printer.printErr(e);
                return new EndedIntegration(0);
            }
            throw e;
        } catch (Error e) {
            // such as a NoClassDefFoundError of a missing dependency
            ended(context.failureExitCode(e), failureOf(e));
            throw e;
        }
        if (hasNoRoutes()) {
            // it would run doing nothing: a file without routes, or a mistake in it
            String failure = request.files.isEmpty()
                    ? "No routes found" : "No routes found in: " + String.join(", ", request.files);
            printer.printErr(failure);
            try {
                main.stop();
            } finally {
                ended(FAILURE, failure);
            }
            return new EndedIntegration(FAILURE);
        }
        return this;
    }

    @Override
    public long pid() {
        return ProcessHandle.current().pid();
    }

    @Override
    public int waitFor() throws Exception {
        int exitCode;
        try {
            if (!stopped) {
                main.run();
            }
            int code = main.getExitCode();
            // no exit code is set when Camel is stopped from outside (such as camel stop)
            exitCode = code == Integer.MIN_VALUE ? 0 : code;
        } catch (Exception e) {
            ended(context.failureExitCode(e), failureOf(e));
            if (request.ignoreLoadingError) {
                printer.printErr(e);
                return 0;
            }
            throw e;
        } catch (Error e) {
            ended(context.failureExitCode(e), failureOf(e));
            throw e;
        }
        ended(exitCode, null);
        return exitCode;
    }

    @Override
    public void stop() {
        stopped = true;
        try {
            main.stop();
        } catch (Exception e) {
            // best effort
        }
    }

    /**
     * Whether Camel started without routes where routes are expected: not with --empty, --source-dir or in dev mode
     * (where files can be added or changed later), and not when Camel runs for another command (such as transform).
     */
    boolean hasNoRoutes() {
        if (request.empty || request.sourceDir != null || request.dev || request.ignoreLoadingError
                || request.transformRun || request.transformMessageRun || request.scriptRun) {
            return false;
        }
        CamelContext camelContext = main.getCamelContext();
        return camelContext != null && camelContext.getRoutes().isEmpty();
    }

    /**
     * The run ended: the flight recording is written, and the log is kept as the previous log of the integration, or
     * deleted.
     */
    private void ended(Integer exitCode, String failure) {
        if (ended) {
            return;
        }
        ended = true;
        stopJfrRecording();
        if (previousLog != null) {
            previousLog.ended(exitCode, failure);
        } else if (request.logFile != null) {
            // cleanup and delete log file
            FileUtil.deleteFile(request.logFile);
        }
    }

    private static String failureOf(Throwable e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getName();
    }

    /**
     * The name of the integration, as camel ps and camel log find it: the name of its CamelContext, which is
     * camel.main.name, else the name of the run when Camel did not get that far.
     */
    private String runName() {
        CamelContext camelContext = main.getCamelContext();
        String answer = camelContext != null ? camelContext.getName() : null;
        return answer != null && !answer.isBlank() ? answer : request.name;
    }

    /**
     * Starts a JDK Flight Recorder recording for the run in this JVM (it does not fork, so the
     * {@code -XX:StartFlightRecording} JVM flag of the runtimes that run with Maven cannot apply here).
     */
    Recording startJfrRecording() {
        if (!request.jfrEnabled()) {
            return null;
        }
        String profile = request.jfrProfile != null ? request.jfrProfile : "default";
        Configuration config;
        try {
            config = Configuration.getConfiguration(profile);
        } catch (IOException | ParseException e) {
            // an unknown profile is a user error, so fail fast instead of silently running without a recording
            String known = Configuration.getConfigurations().stream()
                    .map(Configuration::getName)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "Unknown Java Flight Recorder profile: " + profile + ". Supported profiles: " + known, e);
        }
        try {
            Recording answer = new Recording(config);
            answer.setDestination(Paths.get(request.jfrFileName()));
            // also dump if the JVM exits without us getting a chance to stop the recording
            answer.setDumpOnExit(true);
            answer.start();
            printer.println("Java Flight Recorder recording to " + request.jfrFileName());
            return answer;
        } catch (Exception e) {
            printer.printWarn("Failed to start Java Flight Recorder: " + e.getMessage());
            return null;
        }
    }

    private void stopJfrRecording() {
        if (recording == null) {
            return;
        }
        try {
            recording.stop();
            recording.close();
        } catch (Exception e) {
            printer.printWarn("Failed to stop Java Flight Recorder: " + e.getMessage());
        }
    }
}
