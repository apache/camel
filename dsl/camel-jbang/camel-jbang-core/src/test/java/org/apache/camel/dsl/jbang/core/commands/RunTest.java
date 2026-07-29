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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunTest extends CamelCommandBaseTestSupport {

    @Test
    public void shouldParseJavaVersionOption() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--java-version=17", "route.yaml");

        Assertions.assertEquals("17", command.javaVersion);
    }

    @Test
    public void shouldUseDefaultJavaVersion() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "route.yaml");

        Assertions.assertEquals("21", command.javaVersion);
    }

    @Test
    public void shouldParseJavaVersion11() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--java-version=11", "route.yaml");

        Assertions.assertEquals("11", command.javaVersion);
    }

    @Test
    public void shouldParseJavaVersion21() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--java-version=21", "route.yaml");

        Assertions.assertEquals("21", command.javaVersion);
    }

    @Test
    public void shouldListExamples() throws Exception {
        Run command = new Run(new CamelJBangMain().withPrinter(printer));
        command.example = "";
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        String output = printer.getOutput();
        Assertions.assertTrue(output.contains("Available examples:"));
        Assertions.assertTrue(output.contains("circuit-breaker"));
        Assertions.assertTrue(output.contains("groovy"));
        Assertions.assertTrue(output.contains("routes"));
    }

    @Test
    public void shouldRejectUnknownExample() throws Exception {
        Run command = new Run(new CamelJBangMain().withPrinter(printer));
        command.example = "nonexistent";
        int exit = command.doCall();

        Assertions.assertEquals(1, exit);
    }

    @Test
    public void shouldSuggestSimilarExample() throws Exception {
        Run command = new Run(new CamelJBangMain().withPrinter(printer));
        command.example = "eip/circuit-brake";
        int exit = command.doCall();

        Assertions.assertEquals(1, exit);
        String output = printer.getOutput();
        Assertions.assertTrue(output.contains("Did you mean"));
    }

    @Test
    public void shouldParseExampleOption() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--example=circuit-breaker");

        Assertions.assertEquals("circuit-breaker", command.example);
    }

    @Test
    public void shouldParseExampleListOption() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--example");

        Assertions.assertNotNull(command.example);
    }

    @Test
    void jfrDisabledByDefaultBuildsNoJvmArgs() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "route.yaml");

        assertThat(command.jfrEnabled()).isFalse();
        assertThat(command.buildJfrJvmArgs()).isNull();
    }

    @Test
    void jfrFlagBuildsStartFlightRecordingArg() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--jfr", "route.yaml");

        assertThat(command.jfrEnabled()).isTrue();
        assertThat(command.buildJfrJvmArgs())
                .isEqualTo("-XX:StartFlightRecording=filename=CamelJBang.jfr");
    }

    @Test
    void jfrProfileImpliesJfrAndIsAppendedAsSettings() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--jfr-profile=profile", "route.yaml");

        assertThat(command.jfrEnabled()).isTrue();
        assertThat(command.buildJfrJvmArgs())
                .isEqualTo("-XX:StartFlightRecording=filename=CamelJBang.jfr,settings=profile");
    }

    @Test
    void explicitStartFlightRecordingInJvmArgsWinsOverJfrFlag() throws Exception {
        // two recordings would otherwise compete for the same file, so --jfr must stand down
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--jfr",
                "--jvm-args=-XX:StartFlightRecording=filename=mine.jfr", "route.yaml");

        assertThat(command.jfrEnabled()).isTrue();
        assertThat(command.buildJfrJvmArgs()).isNull();
    }

    @Test
    void unknownJfrProfileFailsFastWithTheSupportedProfiles() throws Exception {
        // running without a recording after the user asked for one would look like JFR simply produced nothing
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--jfr-profile=does-not-exist", "route.yaml");

        assertThatThrownBy(command::startJfrRecording)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does-not-exist")
                .hasMessageContaining("default");
    }

    @Test
    void jfrRecordingIsNotStartedWhenJfrIsOff() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "route.yaml");

        assertThat(command.startJfrRecording()).isNull();
    }

    @Test
    void jfrFileNameUsesAppName() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--jfr", "--name=my-app", "route.yaml");

        assertThat(command.jfrFileName()).isEqualTo("my-app.jfr");
    }

    @Test
    void jfrFileNameFallsBackToCamelWhenNameIsNull() {
        Run command = new Run(new CamelJBangMain());
        command.name = null;

        assertThat(command.jfrFileName()).isEqualTo("camel.jfr");
    }

    @Test
    void mergeJvmArgsReturnsExistingWhenExtraIsNull() {
        assertThat(Run.mergeJvmArgs("-Xmx256m", null)).isEqualTo("-Xmx256m");
        assertThat(Run.mergeJvmArgs(null, null)).isNull();
    }

    @Test
    void mergeJvmArgsReturnsExtraWhenExistingIsBlank() {
        assertThat(Run.mergeJvmArgs(null, "-XX:StartFlightRecording=filename=camel.jfr"))
                .isEqualTo("-XX:StartFlightRecording=filename=camel.jfr");
        assertThat(Run.mergeJvmArgs("  ", "-XX:StartFlightRecording=filename=camel.jfr"))
                .isEqualTo("-XX:StartFlightRecording=filename=camel.jfr");
    }

    @Test
    void mergeJvmArgsConcatenatesBothWithSpace() {
        assertThat(Run.mergeJvmArgs("-Xmx256m", "-XX:StartFlightRecording=filename=camel.jfr"))
                .isEqualTo("-Xmx256m -XX:StartFlightRecording=filename=camel.jfr");
    }
}
