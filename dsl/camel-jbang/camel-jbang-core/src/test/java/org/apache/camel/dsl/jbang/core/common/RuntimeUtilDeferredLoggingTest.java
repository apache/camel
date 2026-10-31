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
package org.apache.camel.dsl.jbang.core.common;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.apache.camel.dsl.jbang.run.common.RuntimeUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A command that turns the logging off does not configure log4j, which takes about 200 ms, as most commands log
 * nothing: log4j configures itself, with the logging off, when something first logs. Each case runs in a new JVM, as
 * log4j is configured once in a JVM.
 */
class RuntimeUtilDeferredLoggingTest {

    @TempDir
    Path dir;

    @Test
    void theLoggingOffDoesNotConfigureLog4j() throws Exception {
        assertThat(run("off")).containsExactly("configured=false");
    }

    @Test
    void log4jConfiguresItselfWithTheLoggingOffWhenSomethingLogs() throws Exception {
        assertThat(run("off-then-log"))
                .containsExactly("configured=true", "name=CamelJBangLoggingOff", "root=OFF", "appenders=[file, out]");
    }

    @Test
    void theCommandThatConfiguresTheLoggingAgainLogsWithTheConfigurationItStartedWith() throws Exception {
        // as when log4j was configured at first: the appenders of the configuration the command started with
        assertThat(run("off-then-info")).containsExactly("root=INFO", "appenders=[file, out]");
        assertThat(run("off-then-log-then-info")).containsExactly("root=INFO", "appenders=[file, out]");
    }

    @Test
    void theNextCommandOfACliThatRunsSeveralCommandsLogsAsOnItsOwn() throws Exception {
        assertThat(run("off-then-new-command-off")).containsExactly("configured=false");
        assertThat(run("off-then-new-command-export")).containsExactly("root=INFO", "appenders=[file]");
    }

    @Test
    void whenSomethingLoggedBeforeTheLoggingIsOffAtOnce() throws Exception {
        assertThat(run("log-then-off")).containsExactly("configured=true", "root=OFF");
    }

    // the lines the scenario prints: nothing that is logged reaches the console
    private List<String> run(String scenario) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Path out = dir.resolve(scenario + ".out");
        Process process = new ProcessBuilder(
                java, "-cp", System.getProperty("java.class.path"), "-Duser.home=" + dir, Scenario.class.getName(),
                scenario)
                .redirectErrorStream(true)
                .redirectOutput(out.toFile())
                .start();
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        List<String> lines = Files.readAllLines(out, StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("exit code, output: " + lines).isZero();
        return lines;
    }

    /**
     * Configures the logging as the commands of the CLI do, and prints what log4j is configured with.
     */
    public static final class Scenario {

        public static void main(String... args) throws Exception {
            switch (args[0]) {
                case "off" -> {
                    off();
                    printConfigured();
                }
                case "off-then-log" -> {
                    off();
                    LogManager.getLogger("test").error("not printed");
                    printConfigured();
                    LoggerContext context = (LoggerContext) LogManager.getContext(false);
                    System.out.println("name=" + context.getConfiguration().getName());
                    printRootAndAppenders();
                }
                case "off-then-info" -> {
                    off();
                    RuntimeUtil.configureLog("info", false, false, false, true, null, null);
                    printRootAndAppenders();
                }
                case "off-then-log-then-info" -> {
                    off();
                    LogManager.getLogger("test").error("not printed");
                    RuntimeUtil.configureLog("info", false, false, false, true, null, null);
                    printRootAndAppenders();
                }
                case "off-then-new-command-off" -> {
                    off();
                    RuntimeUtil.newCommand();
                    off();
                    printConfigured();
                }
                case "off-then-new-command-export" -> {
                    off();
                    RuntimeUtil.newCommand();
                    RuntimeUtil.configureLog("info", false, false, false, true, null, null);
                    printRootAndAppenders();
                }
                case "log-then-off" -> {
                    LogManager.getLogger("test").debug("before");
                    off();
                    printConfigured();
                    System.out.println("root=" + ((LoggerContext) LogManager.getContext(false)).getRootLogger().getLevel());
                }
                default -> throw new IllegalArgumentException(args[0]);
            }
        }

        private static void off() throws Exception {
            RuntimeUtil.configureLog("off", false, false, false, false, null, null);
        }

        private static void printConfigured() {
            System.out.println("configured=" + LogManager.getFactory()
                    .hasContext(RuntimeUtil.class.getName(), RuntimeUtil.class.getClassLoader(), false));
        }

        private static void printRootAndAppenders() {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            System.out.println("root=" + context.getRootLogger().getLevel());
            System.out.println("appenders=" + new TreeSet<>(context.getConfiguration().getAppenders().keySet()));
        }
    }
}
