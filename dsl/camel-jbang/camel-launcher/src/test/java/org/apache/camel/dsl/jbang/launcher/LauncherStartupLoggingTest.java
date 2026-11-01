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
package org.apache.camel.dsl.jbang.launcher;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.InstallDetector;
import org.apache.camel.dsl.jbang.core.common.LauncherHelper;
import org.apache.camel.dsl.jbang.launcher.selfupdate.UpdateChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A command of the launcher that logs nothing does not configure log4j, which takes about 140 ms: it creates no logger
 * (creating one configures log4j) and turns the logging off without configuring log4j. Each case runs in a new JVM and
 * lists the classes it loads.
 */
class LauncherStartupLoggingTest {

    @TempDir
    Path dir;

    @Test
    void camelVersionDoesNotConfigureLog4j() throws Exception {
        List<String> classes = run(CamelLauncher.class.getName(), "version");

        assertThat(classes).contains(UpdateChecker.class.getName(), InstallDetector.class.getName());
        assertNoLogging(classes);
    }

    @Test
    void theUpdateCheckOfTheWebInstallerDoesNotConfigureLog4j() throws Exception {
        List<String> classes = run(WebInstallerUpdateCheck.class.getName());

        // the catalog tells the running version, after the check found the web installer
        assertThat(classes).contains(DefaultCamelCatalog.class.getName());
        assertNoLogging(classes);
    }

    private static void assertNoLogging(List<String> classes) {
        assertThat(classes.stream().filter(c -> c.startsWith("org.slf4j."))).as("no logger").isEmpty();
        assertThat(classes.stream().filter(c -> c.startsWith("org.apache.logging.log4j.core.appender.")))
                .as("log4j is not configured").isEmpty();
    }

    // the classes the JVM loads
    private List<String> run(String mainClass, String... args) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>(
                List.of(java, "-Xlog:class+load=info:file=classes.log:none", "-cp", System.getProperty("java.class.path"),
                        "-Duser.home=" + dir, mainClass));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(dir.resolve("out.txt").toFile());
        Map<String, String> env = builder.environment();
        env.remove("CAMEL_SELF_UPDATE_CHECK");
        // where the web installer installs (POSIX and Windows)
        env.put("XDG_DATA_HOME", dir.toString());
        env.put("LOCALAPPDATA", dir.toString());
        Process process = builder.start();
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as("exit code, output: " + Files.readString(dir.resolve("out.txt"))).isZero();
        List<String> classes = new ArrayList<>();
        for (String line : Files.readAllLines(dir.resolve("classes.log"), StandardCharsets.UTF_8)) {
            classes.add(line.split(" ")[0]);
        }
        return classes;
    }

    /**
     * The update check of a launcher installed by the web installer.
     */
    public static final class WebInstallerUpdateCheck {

        public static void main(String... args) throws Exception {
            Path root = InstallDetector.webInstallerVersionsRoot();
            // a cache that is up to date, so that the check does not go to the network
            Path cache = root.getParent().resolve("update-check.properties");
            Files.createDirectories(cache.getParent());
            Files.writeString(cache, "last_checked=" + System.currentTimeMillis() + "\nlatest_version=4.0.0\n");
            Path jar = root.resolve("4.0.0").resolve("lib").resolve("camel-launcher.jar");
            System.setProperty(LauncherHelper.CAMEL_LAUNCHER_JAR_PROPERTY, jar.toString());
            UpdateChecker.maybeNotify(new String[] { "version" });
        }
    }
}
