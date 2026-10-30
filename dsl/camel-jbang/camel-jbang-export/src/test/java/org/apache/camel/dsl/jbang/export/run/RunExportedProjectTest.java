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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.dsl.jbang.export.common.StringPrinter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies how {@code camel run --runtime=quarkus|spring-boot} runs the exported project: the execution limits are
 * passed to the child JVM like for Camel Main, and the log file is named after the name the application reports.
 */
class RunExportedProjectTest {

    @TempDir
    Path dir;

    private final StringPrinter printer = new StringPrinter();

    private String exportedRunJvmArgs(RunRequest request) {
        return MavenRunSupport.exportedRunJvmArgs(request, printer);
    }

    private static RunRequest limits(int maxSeconds, int maxMessages, int maxIdleSeconds) {
        RunRequest request = new RunRequest();
        request.maxSeconds = maxSeconds;
        request.maxMessages = maxMessages;
        request.maxIdleSeconds = maxIdleSeconds;
        return request;
    }

    @Test
    void durationLimitsArePassedToExportedRun() {
        String args = exportedRunJvmArgs(limits(30, 5, 10));

        assertThat(args).isEqualTo("-Dcamel.main.durationMaxSeconds=30 -Dcamel.main.durationMaxMessages=5"
                                   + " -Dcamel.main.durationMaxIdleSeconds=10");
    }

    @Test
    void durationLimitsAreMergedWithJvmArgs() {
        RunRequest request = limits(30, 0, 0);
        request.jvmArgs = "-Xmx512m";
        String args = exportedRunJvmArgs(request);

        assertThat(args).isEqualTo("-Xmx512m -Dcamel.main.durationMaxSeconds=30");
    }

    @Test
    void noJvmArgsWhenNothingToPass() {
        assertThat(exportedRunJvmArgs(new RunRequest())).isNull();
    }

    @Test
    void jfrIsPassedAsJvmArgument() {
        RunRequest request = new RunRequest();
        request.jfr = true;
        String args = exportedRunJvmArgs(request);

        assertThat(args).contains("-XX:StartFlightRecording");
    }

    @Test
    void durationLimitsAreTheSameForAllRuntimes() {
        RunRequest request = limits(30, 0, 0);

        List<String> limits = MavenRunSupport.durationLimitArgs(request);
        assertThat(limits).containsExactly("-Dcamel.main.durationMaxSeconds=30");
        // exported project (camel run foo.yaml --runtime=quarkus|spring-boot)
        assertThat(exportedRunJvmArgs(request)).isEqualTo(String.join(" ", limits));
        // existing project (camel run pom.xml), per runtime
        assertThat(MavenRunSupport.existingQuarkusJvmArgs(request, "my-app", printer)).containsAll(limits);
        assertThat(MavenRunSupport.existingSpringBootJvmArgs(request, "my-app", null, printer)).containsAll(limits);
        assertThat(MavenRunSupport.existingCamelMainSystemProperties(request, "my-app", dir.resolve("log4j2.properties")))
                .containsAll(limits);
    }

    @Test
    void appNameIsTheExportedCamelMainName() throws Exception {
        Path resources = dir.resolve("src/main/resources");
        Files.createDirectories(resources);
        Files.writeString(resources.resolve("application.properties"), "camel.main.name=my-route\n");

        assertThat(MavenRunSupport.resolveExportedAppName(dir, "CamelJBang")).isEqualTo("my-route");
    }

    @Test
    void appNameFallsBackWhenNotExported() {
        assertThat(MavenRunSupport.resolveExportedAppName(dir, "CamelJBang")).isEqualTo("CamelJBang");
    }

    @Test
    void noFlightRecordingWithoutJfr() {
        RunRequest request = new RunRequest();

        assertThat(request.jfrEnabled()).isFalse();
        assertThat(MavenRunSupport.jfrJvmArgs(request, printer)).isNull();
    }

    @Test
    void jfrStartsAFlightRecording() {
        RunRequest request = new RunRequest();
        request.jfr = true;

        assertThat(request.jfrEnabled()).isTrue();
        assertThat(MavenRunSupport.jfrJvmArgs(request, printer))
                .isEqualTo("-XX:StartFlightRecording=filename=CamelJBang.jfr "
                           + "-Dcamel.main.startupRecorderRuntimeEnabled=true");
    }

    @Test
    void aJfrProfileImpliesJfrAndIsTheSettingsOfTheRecording() {
        RunRequest request = new RunRequest();
        request.jfrProfile = "profile";

        assertThat(request.jfrEnabled()).isTrue();
        assertThat(MavenRunSupport.jfrJvmArgs(request, printer))
                .isEqualTo("-XX:StartFlightRecording=filename=CamelJBang.jfr,settings=profile "
                           + "-Dcamel.main.startupRecorderRuntimeEnabled=true");
    }

    @Test
    void aRecordingInTheJvmArgsWinsAndKeepsTheRuntimeRecorderEnabled() {
        // the explicit recording wins, but --jfr must still enable Camel runtime events in forked projects
        RunRequest request = new RunRequest();
        request.jfr = true;
        request.jvmArgs = "-XX:StartFlightRecording=filename=mine.jfr";

        assertThat(MavenRunSupport.jfrJvmArgs(request, printer))
                .isEqualTo("-Dcamel.main.startupRecorderRuntimeEnabled=true");
        assertThat(printer.getOutput()).contains("--jvm-args already starts a flight recording");
    }

    @Test
    void theRecordingIsNamedAfterTheIntegration() {
        RunRequest request = new RunRequest();
        request.name = "my-app";
        assertThat(request.jfrFileName()).isEqualTo("my-app.jfr");

        request.name = null;
        assertThat(request.jfrFileName()).isEqualTo("camel.jfr");
    }

    @Test
    void mergeJvmArgs() {
        assertThat(MavenRunSupport.mergeJvmArgs("-Xmx256m", null)).isEqualTo("-Xmx256m");
        assertThat(MavenRunSupport.mergeJvmArgs(null, null)).isNull();
        assertThat(MavenRunSupport.mergeJvmArgs(null, "-XX:StartFlightRecording=filename=camel.jfr"))
                .isEqualTo("-XX:StartFlightRecording=filename=camel.jfr");
        assertThat(MavenRunSupport.mergeJvmArgs("  ", "-XX:StartFlightRecording=filename=camel.jfr"))
                .isEqualTo("-XX:StartFlightRecording=filename=camel.jfr");
        assertThat(MavenRunSupport.mergeJvmArgs("-Xmx256m", "-XX:StartFlightRecording=filename=camel.jfr"))
                .isEqualTo("-Xmx256m -XX:StartFlightRecording=filename=camel.jfr");
    }
}
