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

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25820: the logging configurations of the CLI write their files into the Camel directory of the CLI, and the
 * configuration that log4j finds on the classpath logs to the console only. Each configuration is loaded into a logger
 * context of its own, so the logging of the tests stays as it is, and no file is created (the files are created on
 * demand).
 */
class LogConfigurationTest {

    private static final String PID = Long.toString(ProcessHandle.current().pid());

    @TempDir
    Path dir;

    @BeforeEach
    void pid() {
        // as the CLI sets it
        System.setProperty("pid", PID);
    }

    @AfterEach
    void clear() {
        System.clearProperty(RuntimeUtil.LOG_DIR);
    }

    @ParameterizedTest
    @CsvSource({
            "log4j2-color.properties, PID.log",
            "log4j2-no-color.properties, PID.log",
            "log4j2-background.properties, PID.log",
            "log4j2-export.properties, camel-export.log",
            "log4j2-script.properties, camel-script.log" })
    void theLogFileIsInTheCamelDirectoryOfTheCli(String config, String file) throws Exception {
        System.setProperty(RuntimeUtil.LOG_DIR, dir.toString());

        assertThat(fileName(config)).isEqualTo(dir.resolve(file.replace("PID", PID)).toString());
    }

    @Test
    void withoutTheDirectoryOfTheCliTheLogFileIsInTheCamelDirectoryOfTheUser() throws Exception {
        assertThat(fileName("log4j2-color.properties"))
                .isEqualTo(Path.of(System.getProperty("user.home"), ".camel", PID + ".log").toString());
    }

    @Test
    void theConfigurationOnTheClasspathLogsToTheConsoleOnly() throws Exception {
        // the one of camel-jbang-core, not the one of these tests, which comes first on the classpath of the tests
        Path file = Path.of("src/main/resources/log4j2.properties");
        Configuration config = load(new ConfigurationSource(Files.newInputStream(file), file.toFile()));
        try {
            assertThat(config.getAppenders().values()).isNotEmpty()
                    .allSatisfy(a -> assertThat(a).isInstanceOf(ConsoleAppender.class));
        } finally {
            config.stop();
        }
    }

    // the name of the log file of the configuration, as log4j resolves it
    private static String fileName(String resource) throws Exception {
        Configuration config = load(ConfigurationSource.fromResource(resource, LogConfigurationTest.class.getClassLoader()));
        try {
            Appender file = config.getAppender("file");
            assertThat(file).isInstanceOf(FileAppender.class);
            return ((FileAppender) file).getFileName();
        } finally {
            config.stop();
        }
    }

    private static Configuration load(ConfigurationSource source) {
        LoggerContext context = new LoggerContext("LogConfigurationTest");
        Configuration config = ConfigurationFactory.getInstance().getConfiguration(context, source);
        config.initialize();
        return config;
    }
}
