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
import java.util.Set;

import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class CamelJBangMainArgumentFileTest extends CamelCommandBaseTestSupport {

    @TempDir
    Path dir;

    @BeforeEach
    public void setup() throws Exception {
        super.setup();

        useTestHomeDir();
    }

    @Test
    public void anArgumentFileIsReplacedByItsArguments() throws Exception {
        Path file = dir.resolve("args.txt");
        Files.writeString(file, """
                # the command and its options
                config "list"
                """);

        TestMain main = execute("@" + file);

        assertThat(main.exitCode).isEqualTo(CliExitCode.OK);
        // the command line is read before the commands are created, so only the command of the file is created
        assertThat(main.executed.getSubcommands().keySet()).isEqualTo(Set.of("config"));
        assertThat(main.executed.getArgs()).isEqualTo(List.of("config", "list"));
    }

    @Test
    public void anArgumentFileThatDoesNotExistIsAUsageError() {
        Path file = dir.resolve("nope.txt");

        TestMain main = execute("@" + file);

        assertThat(main.exitCode).isEqualTo(CliExitCode.USAGE);
        assertThat(main.executed).isNull();
        assertThat(printer.getStderr()).contains("ERROR: Argument file not found: '" + file + "'");
    }

    @Test
    public void theArgumentFileOfTheDocs() throws Exception {
        Path file = dir.resolve("run.txt");
        Files.writeString(file, """
                # the routes and the options to run them with
                run orders.yaml payments.yaml
                --dev
                --property=app.name="My Shop"
                """);
        CamelJBangMain main = new CamelJBangMain().withPrinter(printer);

        assertThat(main.expandArgumentFiles("@" + file))
                .containsExactly("run", "orders.yaml", "payments.yaml", "--dev", "--property=app.name=My Shop");
    }

    @Test
    public void aDoubleAtIsALiteralAt() {
        CamelJBangMain main = new CamelJBangMain().withPrinter(printer);

        assertThat(main.expandArgumentFiles("cmd", "send", "@@foo")).containsExactly("cmd", "send", "@foo");
    }

    private TestMain execute(String... args) {
        TestMain main = new TestMain();
        main.withPrinter(printer);
        main.setDiscoverPlugins(false);
        main.execute(args);
        return main;
    }

    private static class TestMain extends CamelJBangMain {
        CommandTree executed;
        int exitCode = -1;

        @Override
        public void postExecute(CommandTree commandLine, String[] args, int exitCode) {
            executed = commandLine;
        }

        @Override
        public void quit(int exitCode) {
            this.exitCode = exitCode;
        }
    }
}
