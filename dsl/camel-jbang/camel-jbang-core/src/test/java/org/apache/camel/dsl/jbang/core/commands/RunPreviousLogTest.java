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

import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PreviousRun;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.main.KameletMain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CAMEL-25784: camel run keeps its log as the previous log of the integration when it ends.
 */
class RunPreviousLogTest extends CamelCommandBaseTestSupport {

    private Path log;

    @BeforeEach
    void logOfTheRun() throws Exception {
        useTestHomeDir();
        Files.createDirectories(CommandLineHelper.getCamelDir());
        log = Files.write(CommandLineHelper.getCamelDir().resolve("4242.log"), List.of("Starting", "Failed"));
    }

    @Test
    void theLogOfARunThatFailedToStartIsKept() throws Exception {
        KameletMain main = mock(KameletMain.class);
        doThrow(new FailedToStartRouteException("route1", "boom")).when(main).start();
        Run run = commandLineRun();

        assertThatThrownBy(() -> run.runKameletMain(main)).isInstanceOf(FailedToStartRouteException.class);

        // Camel did not get as far as a CamelContext: the name of the run
        PreviousRun.Info info = only(PreviousRun.find("orders"));
        assertThat(info.pid()).isEqualTo(ProcessHandle.current().pid());
        // the exit code the CLI exits with
        assertThat(info.exitCode()).isEqualTo(CliExitCode.INTERNAL);
        assertThat(info.failure()).isEqualTo("Failed to start route: route1 because: boom");
        assertThat(Files.readAllLines(info.log())).containsExactly("Starting", "Failed");
        assertThat(log).doesNotExist();
    }

    @Test
    void theLogOfARunWithoutRoutesIsKeptByTheNameOfItsCamelContext() throws Exception {
        KameletMain main = mock(KameletMain.class);
        DefaultCamelContext context = new DefaultCamelContext();
        context.getCamelContextExtension().setName("orders-eu");
        when(main.getCamelContext()).thenReturn(context);
        Run run = commandLineRun();

        assertThat(run.runKameletMain(main)).isEqualTo(CliExitCode.FAILURE);

        PreviousRun.Info info = only(PreviousRun.find("orders-eu"));
        assertThat(info.exitCode()).isEqualTo(CliExitCode.FAILURE);
        assertThat(info.failure()).isEqualTo("No routes found");
    }

    @Test
    void theLogOfARunThatWasStoppedIsKept() throws Exception {
        KameletMain main = mock(KameletMain.class);
        // no exit code: stopped from outside, such as by camel stop
        when(main.getExitCode()).thenReturn(Integer.MIN_VALUE);
        Run run = commandLineRun();

        assertThat(run.runKameletMain(main)).isZero();

        PreviousRun.Info info = only(PreviousRun.find("orders"));
        assertThat(info.exitCode()).isZero();
        assertThat(info.failure()).isNull();
    }

    @Test
    void aCamelThatAnotherCommandRunsRemovesItsLog() throws Exception {
        KameletMain main = mock(KameletMain.class);
        // such as camel cmd send --local, which runs a Camel of its own
        Run run = new Run(new CamelJBangMain().withPrinter(printer));
        run.logFile = log;

        assertThat(run.runKameletMain(main)).isZero();

        assertThat(log).doesNotExist();
        assertThat(PreviousRun.findAll()).isEmpty();
    }

    // camel run on the command line
    private Run commandLineRun() {
        Run run = new Run(new CamelJBangMain().withPrinter(printer));
        new CommandLine(run);
        run.name = "orders";
        run.logFile = log;
        return run;
    }

    private static PreviousRun.Info only(List<PreviousRun.Info> found) {
        assertThat(found).hasSize(1);
        return found.get(0);
    }
}
