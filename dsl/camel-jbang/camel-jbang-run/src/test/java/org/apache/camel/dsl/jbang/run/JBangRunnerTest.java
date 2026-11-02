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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.dsl.jbang.run.common.ExportHelper;
import org.apache.camel.dsl.jbang.run.common.StringPrinter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.event.CamelContextReloadFailureEvent;
import org.apache.camel.main.KameletMain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A run with the jbang runtime: the files run in this JVM.
 */
class JBangRunnerTest {

    @TempDir
    Path dir;

    private final StringPrinter printer = new StringPrinter();
    private RunContext context;
    private Path route;

    @BeforeEach
    void writeRoute() throws Exception {
        context = new RunContext().withPrinter(printer);
        route = dir.resolve("hello.camel.yaml");
        Files.writeString(route, """
                - route:
                    from:
                      uri: timer:hello
                      steps:
                        - log:
                            message: Hello
                """);
    }

    private RunRequest request() {
        RunRequest request = new RunRequest();
        request.workDir = dir.resolve("work");
        request.baseDir = dir;
        request.files.add(route.toString());
        return request;
    }

    @Test
    void theRouteRunsInThisJvmUntilItEnds() throws Exception {
        RunRequest request = request();
        request.maxMessages = 1;

        RunningIntegration integration = new ProjectRunner(context).start(request);

        assertThat(integration.pid()).isEqualTo(ProcessHandle.current().pid());
        assertThat(integration.waitFor()).as(printer.getOutput()).isZero();
        // the run completes the request, as camel run does
        assertThat(request.name).isEqualTo("hello");
    }

    @Test
    void stoppingTheIntegrationEndsTheRun() throws Exception {
        RunningIntegration integration = new ProjectRunner(context).start(request());
        CompletableFuture<Integer> exit = CompletableFuture.supplyAsync(() -> {
            try {
                return integration.waitFor();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        integration.stop();

        assertThat(exit.get(20, TimeUnit.SECONDS)).isZero();
    }

    @Test
    void aFileWithoutRoutesEndsTheRun() throws Exception {
        Path beans = dir.resolve("beans.camel.yaml");
        Files.writeString(beans, """
                - beans:
                    - name: greeting
                      type: java.lang.StringBuilder
                """);
        RunRequest request = request();
        request.files.clear();
        request.files.add(beans.toString());

        RunningIntegration integration = new ProjectRunner(context).start(request);

        assertThat(integration.pid()).isZero();
        assertThat(integration.waitFor()).isEqualTo(KameletMainIntegration.FAILURE);
        assertThat(printer.getOutput()).contains("No routes found in: " + beans);
    }

    @Test
    void aDirectoryRunsAsTheSourceDirectory() throws Exception {
        RunRequest request = request();
        request.baseDir = null;
        request.files.clear();
        request.files.add(dir.toString());
        request.maxMessages = 1;

        RunningIntegration integration = new ProjectRunner(context).start(request);

        assertThat(request.sourceDir).isEqualTo(dir.toString());
        assertThat(request.baseDir).isEqualTo(dir);
        assertThat(integration.waitFor()).as(printer.getOutput()).isZero();
    }

    @Test
    void nothingToRunEndsTheRun() throws Exception {
        RunRequest request = request();
        request.files.clear();
        request.transformRun = true;

        RunningIntegration integration = new ProjectRunner(context).start(request);

        assertThat(integration.waitFor()).isEqualTo(1);
        assertThat(printer.getOutput()).contains("Cannot transform because application.properties file does not exist");
    }

    @Test
    void theRunIsConfiguredBeforeItStarts() throws Exception {
        RunRequest request = request();
        request.maxMessages = 1;

        ConfiguredRun configured = new ProjectRunner(context).configure(request);

        // camel run decides only now whether it starts Camel in this JVM, or runs in another JVM
        assertThat(configured.getExitCode()).isZero();
        assertThat(configured.getMain()).isNotNull();
        assertThat(configured.getRequest()).isSameAs(request);
        assertThat(request.name).isEqualTo("hello");
        assertThat(request.workDir.resolve(ExportHelper.RUN_SETTINGS_FILE)).content()
                .contains("camel.main.routesIncludePattern=file\\:" + route);
        assertThat(configured.start().waitFor()).as(printer.getOutput()).isZero();
    }

    @Test
    void theCallerIsToldWhenTheRunIsPrepared() throws Exception {
        Files.writeString(dir.resolve("application.properties"), """
                loggingLevel=debug
                camel.jbang.gav=com.acme:orders:1.0
                """);
        List<Properties> prepared = new ArrayList<>();
        context.withPreparedListener((request, properties, mcpStdio) -> {
            // the application properties configured the request already
            assertThat(request.gav).isEqualTo("com.acme:orders:1.0");
            assertThat(mcpStdio).isFalse();
            prepared.add(properties);
            // such as camel run --code, which runs a file of its own first
            Path code = Files.writeString(request.workDir.resolve("Code.camel.yaml"), """
                    - route:
                        from:
                          uri: timer:code
                          steps:
                            - log:
                                message: Code
                    """);
            request.files.add(0, code.toString());
        });
        RunRequest request = request();

        assertThat(new ProjectRunner(context).configure(request).getExitCode()).isZero();

        assertThat(prepared).singleElement().satisfies(p -> assertThat(p.getProperty("loggingLevel")).isEqualTo("debug"));
        // the file of the caller runs first
        assertThat(request.files.get(0)).endsWith("Code.camel.yaml");
        assertThat(request.files).contains(route.toString());
    }

    @Test
    void anEmptyRunHasTheApplicationPropertiesAsCamelRun() throws Exception {
        Files.writeString(dir.resolve("application.properties"), """
                loggingLevel=debug
                camel.jbang.gav=com.acme:orders:1.0
                """);
        List<Properties> prepared = new ArrayList<>();
        context.withPreparedListener((request, properties, mcpStdio) -> prepared.add(properties));
        RunRequest request = request();
        request.files.clear();
        request.empty = true;

        assertThat(new ProjectRunner(context).configure(request).getExitCode()).isZero();

        assertThat(prepared).singleElement().satisfies(p -> assertThat(p.getProperty("loggingLevel")).isEqualTo("debug"));
        // an empty run has no files, but the application properties configure it as any run
        assertThat(request.gav).isEqualTo("com.acme:orders:1.0");
        assertThat(request.kameletsVersion).isNotNull();
    }

    @Test
    void aRouteFileThatFailsToReloadIsReported() {
        List<String> reported = new ArrayList<>();
        context.withReloadFailureListener((failure, files) -> reported.add(failure.getMessage() + " " + files));
        ReloadFailureNotifier notifier = new ReloadFailureNotifier(context);
        CamelContextReloadFailureEvent event
                = new CamelContextReloadFailureEvent(
                        new DefaultCamelContext(), "hello.camel.yaml",
                        new IllegalArgumentException("boom"));

        assertThat(notifier.isEnabled(event)).isTrue();
        notifier.notify(event);

        assertThat(reported).containsExactly("boom [hello.camel.yaml]");
    }

    @Test
    void routesAreExpectedUnlessFilesCanBeAddedOrNoneAreWanted() {
        KameletMain main = mock(KameletMain.class);
        when(main.getCamelContext()).thenReturn(new DefaultCamelContext());

        assertThat(integration(main, new RunRequest()).hasNoRoutes()).isTrue();
        // files can be added or changed later
        RunRequest dev = new RunRequest();
        dev.dev = true;
        assertThat(integration(main, dev).hasNoRoutes()).isFalse();
        RunRequest sourceDir = new RunRequest();
        sourceDir.sourceDir = "src";
        assertThat(integration(main, sourceDir).hasNoRoutes()).isFalse();
        // or no files are wanted
        RunRequest empty = new RunRequest();
        empty.empty = true;
        assertThat(integration(main, empty).hasNoRoutes()).isFalse();
    }

    @Test
    void anUnknownJfrProfileFailsFastWithTheSupportedProfiles() {
        // running without a recording after the user asked for one would look like JFR simply produced nothing
        RunRequest request = new RunRequest();
        request.jfrProfile = "does-not-exist";

        assertThatThrownBy(() -> integration(mock(KameletMain.class), request).startJfrRecording())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does-not-exist")
                .hasMessageContaining("default");
    }

    @Test
    void noJfrRecordingWhenJfrIsOff() {
        assertThat(integration(mock(KameletMain.class), new RunRequest()).startJfrRecording()).isNull();
    }

    private KameletMainIntegration integration(KameletMain main, RunRequest request) {
        return new KameletMainIntegration(main, request, context);
    }
}
