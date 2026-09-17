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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies how {@code camel run --runtime=quarkus|spring-boot} runs the exported project: the execution limits are
 * passed to the child JVM like for Camel Main, and the log file is named after the name the application reports.
 */
class RunExportedProjectTest extends CamelCommandBaseTestSupport {

    @TempDir
    Path dir;

    private static Run run(String... args) {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, args);
        return command;
    }

    @Test
    void durationLimitsArePassedToExportedRun() {
        String args = run("--max-seconds=30", "--max-messages=5", "--max-idle-seconds=10", "route.yaml")
                .buildExportedRunJvmArgs();

        assertThat(args).isEqualTo("-Dcamel.main.durationMaxSeconds=30 -Dcamel.main.durationMaxMessages=5"
                                   + " -Dcamel.main.durationMaxIdleSeconds=10");
    }

    @Test
    void durationLimitsAreMergedWithJvmArgs() {
        String args = run("--jvm-args=-Xmx512m", "--max-seconds=30", "route.yaml").buildExportedRunJvmArgs();

        assertThat(args).isEqualTo("-Xmx512m -Dcamel.main.durationMaxSeconds=30");
    }

    @Test
    void noJvmArgsWhenNothingToPass() {
        assertThat(run("route.yaml").buildExportedRunJvmArgs()).isNull();
    }

    @Test
    void jfrIsPassedAsJvmArgument() {
        String args = run("--jfr", "route.yaml").buildExportedRunJvmArgs();

        assertThat(args).contains("-XX:StartFlightRecording");
    }

    @Test
    void durationLimitsAreTheSameForAllRuntimes() {
        Run run = run("--max-seconds=30", "route.yaml");

        List<String> limits = run.buildDurationLimitArgs();
        assertThat(limits).containsExactly("-Dcamel.main.durationMaxSeconds=30");
        // exported project (camel run foo.yaml --runtime=quarkus|spring-boot)
        assertThat(run.buildExportedRunJvmArgs()).isEqualTo(String.join(" ", limits));
        // existing project (camel run pom.xml), per runtime
        assertThat(run.buildExistingQuarkusJvmArgs("my-app")).containsAll(limits);
        assertThat(run.buildExistingSpringBootJvmArgs()).containsAll(limits);
        assertThat(run.buildExistingCamelMainSystemProperties("my-app", dir.resolve("log4j2.properties")))
                .containsAll(limits);
    }

    @Test
    void appNameIsTheExportedCamelMainName() throws Exception {
        Path resources = dir.resolve("src/main/resources");
        Files.createDirectories(resources);
        Files.writeString(resources.resolve("application.properties"), "camel.main.name=my-route\n");

        assertThat(Run.resolveExportedAppName(dir, "CamelJBang")).isEqualTo("my-route");
    }

    @Test
    void appNameFallsBackWhenNotExported() {
        assertThat(Run.resolveExportedAppName(dir, "CamelJBang")).isEqualTo("CamelJBang");
    }

}
