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

import java.util.List;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25497: --jvm-debug, --jvm-args and --open-telemetry-agent run the integration in a new JVM; that JVM must run
 * the Camel version of the CLI that starts it, not the default version of the camel@apache/camel catalog script.
 */
class RunCamelVersionCommandTest {

    private static final String RUNNING_VERSION = new DefaultCamelCatalog().getCatalogVersion();

    @Test
    void jvmDebugRunsTheRunningCamelVersion() {
        List<String> cmd = command("route.camel.yaml", "--jvm-debug=5005");

        assertThat(cmd).contains("-Dcamel.jbang.version=" + RUNNING_VERSION, "--debug=5005");
    }

    @Test
    void jvmArgsRunTheRunningCamelVersion() {
        List<String> cmd = command("route.camel.yaml", "--jvm-args=-Dfoo=bar");

        assertThat(cmd).contains("-Dcamel.jbang.version=" + RUNNING_VERSION, "--java-options=-Dfoo=bar");
    }

    @Test
    void theRequestedCamelVersionWins() {
        List<String> cmd = command("route.camel.yaml", "--jvm-debug=5005", "--camel-version=4.21.0");

        assertThat(cmd).contains("-Dcamel.jbang.version=4.21.0")
                .doesNotContain("-Dcamel.jbang.version=" + RUNNING_VERSION, "--camel-version=4.21.0");
    }

    private static List<String> command(String... args) {
        Run run = new Run(new CamelJBangMain());
        new CommandLine(run).parseArgs(args);
        return run.createCamelVersionCommand();
    }
}
