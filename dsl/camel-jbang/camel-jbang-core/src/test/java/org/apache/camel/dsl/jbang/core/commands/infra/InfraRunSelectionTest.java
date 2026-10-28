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

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel infra run: a service or implementation that is not in the catalog of the test infra is not found.
 */
class InfraRunSelectionTest extends CamelCommandBaseTestSupport {

    @Test
    void unknownServiceIsNotFound() {
        assertThatThrownBy(() -> command("nope").doCall())
                .isInstanceOf(CliException.class)
                .hasMessage("service nope not found, use the list command for the available services")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.NOT_FOUND);
    }

    @Test
    void unknownImplementationIsNotFound() {
        assertThatThrownBy(() -> command("ftp", "nope").doCall())
                .isInstanceOf(CliException.class)
                .hasMessage("service ftp with implementation nope not found, use the list command for the available services")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.NOT_FOUND);
    }

    @Test
    void malformedPropertyIsAUsageError() {
        assertThatThrownBy(() -> command("ftp", "--property=novalue").doCall())
                .isInstanceOf(CliException.class)
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.USAGE);
    }

    private InfraRun command(String... args) {
        InfraRun command = new InfraRun(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, args);
        return command;
    }
}
