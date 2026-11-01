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
package org.apache.camel.test.dst;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Without the agent every {@code @DstInstrument} class fails with the message that names the setup, not only the first
 * class of a package (two classes of a module often instrument the same package and run in one fork). Runs a JVM
 * started without {@code -javaagent}; the JVM of this test has the agent.
 */
class DstMissingAgentTest {

    private static final String MESSAGE = "The byte code agent of camel-test-dst is not loaded";

    @Test
    void everyClassFailsWhileTheAgentIsMissing(@TempDir Path dir) throws Exception {
        File out = dir.resolve("probe.out").toFile();
        File err = dir.resolve("probe.err").toFile();
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Xmx64m",
                "-cp", System.getProperty("java.class.path"), Probe.class.getName())
                .redirectOutput(out).redirectError(err);
        // the JVM options of the environment could load an agent, and the JVM prints that it picked them up
        builder.environment().keySet().removeAll(List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"));
        Process probe = builder.start();
        boolean ended = probe.waitFor(60, TimeUnit.SECONDS);
        if (!ended) {
            probe.destroyForcibly().waitFor();
        }
        String output = Files.readString(out.toPath(), StandardCharsets.UTF_8) + "\nstderr:\n"
                        + Files.readString(err.toPath(), StandardCharsets.UTF_8);
        assertTrue(ended, output);
        assertEquals(0, probe.exitValue(), output);
        assertEquals(List.of(
                "install sample (first class): " + MESSAGE,
                "install sample (second class): " + MESSAGE,
                "alsoRewrite ResetableClock (first class): " + MESSAGE,
                "alsoRewrite ResetableClock (second class): " + MESSAGE,
                "packages: []",
                "alsoRewritten: []"),
                Files.readAllLines(out.toPath(), StandardCharsets.UTF_8), output);
    }

    /** What the extension does for two {@code @DstInstrument} classes of the same package, in a JVM without agent. */
    static final class Probe {

        public static void main(String[] args) {
            for (String c : new String[] { "first class", "second class" }) {
                call("install sample (" + c + ")", () -> DstInstrumentation.install("org.apache.camel.test.dst.sample"));
            }
            for (String c : new String[] { "first class", "second class" }) {
                call("alsoRewrite ResetableClock (" + c + ")",
                        () -> DstInstrumentation.alsoRewrite("org.apache.camel.support.ResetableClock"));
            }
            System.out.println("packages: " + DstInstrumentation.packages());
            System.out.println("alsoRewritten: " + DstInstrumentation.alsoRewritten());
        }

        private static void call(String what, Runnable call) {
            try {
                call.run();
                System.out.println(what + ": no error");
            } catch (IllegalStateException e) {
                String message = e.getMessage();
                System.out.println(what + ": " + (message.startsWith(MESSAGE) ? MESSAGE : message));
            }
        }
    }
}
