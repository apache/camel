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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Windows-only behavioral test for the native camel.exe bootstrap. Requires the build-windows-exe profile to have
 * produced target/camel.exe; the test fails loudly if it is missing so a broken native build does not pass silently.
 */
@EnabledOnOs(OS.WINDOWS)
class CamelExeBootstrapTest {

    private static final Path BUILT_EXE = Paths.get("target/camel.exe");

    /** Writes a fake camel.bat that echoes each argument on its own line and returns exitCode. */
    private Path fakeBat(Path dir, int exitCode) throws Exception {
        Path bat = dir.resolve("camel.bat");
        String body = "@echo off\r\n"
                      + ":loop\r\n"
                      + "if \"%~1\"==\"\" goto done\r\n"
                      + "echo ARG=%~1\r\n"
                      + "shift\r\n"
                      + "goto loop\r\n"
                      + ":done\r\n"
                      + "exit /b " + exitCode + "\r\n";
        Files.writeString(bat, body, StandardCharsets.UTF_8);
        return bat;
    }

    private Path stagedExe(Path dir) throws Exception {
        assertTrue(Files.exists(BUILT_EXE),
                "target/camel.exe must be built by the build-windows-exe profile before this test");
        Path exe = dir.resolve("camel.exe");
        Files.copy(BUILT_EXE, exe);
        return exe;
    }

    private static final class Result {
        int exit;
        String stdout;
    }

    private Result run(Path exe, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(exe.toString());
        for (String a : args) {
            cmd.add(a);
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "camel.exe did not exit in time");
        Result r = new Result();
        r.exit = p.exitValue();
        r.stdout = out;
        return r;
    }

    @Test
    void forwardsArgumentsToAdjacentCamelBat(@TempDir Path dir) throws Exception {
        fakeBat(dir, 0);
        Path exe = stagedExe(dir);

        Result r = run(exe, "version");

        assertEquals(0, r.exit);
        assertTrue(r.stdout.contains("ARG=version"), r.stdout);
    }

    @Test
    void preservesArgumentWithSpaces(@TempDir Path dir) throws Exception {
        fakeBat(dir, 0);
        Path exe = stagedExe(dir);

        Result r = run(exe, "run", "my route.yaml");

        assertTrue(r.stdout.contains("ARG=run"), r.stdout);
        assertTrue(r.stdout.contains("ARG=my route.yaml"), "spaced argument must survive as one token: " + r.stdout);
    }

    @Test
    void preservesUnicodeArgument(@TempDir Path dir) throws Exception {
        fakeBat(dir, 0);
        Path exe = stagedExe(dir);

        Result r = run(exe, "run", "rüte-über.yaml");

        assertTrue(r.stdout.contains("rüte-über.yaml"), "Unicode argument must survive: " + r.stdout);
    }

    @Test
    void propagatesChildExitCode(@TempDir Path dir) throws Exception {
        fakeBat(dir, 42);
        Path exe = stagedExe(dir);

        Result r = run(exe, "version");

        assertEquals(42, r.exit, "camel.exe must return camel.bat's exit code");
    }

    @Test
    void worksWhenExeDirectoryHasSpaces(@TempDir Path base) throws Exception {
        Path dir = base.resolve("Program Files X");
        Files.createDirectories(dir);
        fakeBat(dir, 0);
        Path exe = stagedExe(dir);

        Result r = run(exe, "version");

        assertEquals(0, r.exit, r.stdout);
        assertTrue(r.stdout.contains("ARG=version"), r.stdout);
    }
}
