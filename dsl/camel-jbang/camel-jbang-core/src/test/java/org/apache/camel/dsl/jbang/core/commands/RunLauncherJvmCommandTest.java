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

import org.apache.camel.dsl.jbang.core.common.CommandLineTestHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Under the launcher, --jvm-debug and --jvm-args run the integration in a JVM of the launcher, with those JVM options.
 * Before, they went through jbang, which the launcher does not need, and which ran another Camel build on its own JDK
 * (CAMEL-26930).
 */
class RunLauncherJvmCommandTest {

    private static final List<String> LAUNCHER = List.of("/jdk/bin/java", "-Xmx512m", "-jar", "/opt/camel/lib/launcher.jar");

    @Test
    void jvmDebugOpensADebugSocketInTheLauncherJvm() {
        List<String> cmd = command("route.camel.yaml", "--jvm-debug=5005");

        // the arguments of the command line follow, without --jvm-debug (here without run, which a test does not give)
        assertThat(cmd).containsExactly("/jdk/bin/java", "-Xmx512m",
                "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=localhost:5005",
                "-jar", "/opt/camel/lib/launcher.jar", "route.camel.yaml");
        assertThat(cmd).doesNotContain("jbang");
    }

    @Test
    void jvmArgsGoToTheLauncherJvm() {
        List<String> cmd = command("route.camel.yaml", "--jvm-args=-Xmx1g -Dfoo=bar");

        assertThat(cmd).containsSequence("-Xmx1g", "-Dfoo=bar", "-jar");
        assertThat(cmd).noneMatch(arg -> arg.startsWith("--jvm-args"));
        assertThat(cmd).endsWith("/opt/camel/lib/launcher.jar", "route.camel.yaml");
    }

    @Test
    void inTheBackgroundTheJvmDoesNotGoToTheBackgroundAgain() {
        List<String> cmd = command("route.camel.yaml", "--jvm-debug=5005", "--background");

        assertThat(cmd).doesNotContain("--background");
    }

    private static List<String> command(String... args) {
        Run run = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(run, args);
        return run.createLauncherJvmCommand(LAUNCHER);
    }
}
