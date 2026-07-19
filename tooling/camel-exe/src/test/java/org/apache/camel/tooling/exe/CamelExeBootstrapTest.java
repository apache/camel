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
package org.apache.camel.tooling.exe;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
 * Windows-only behavioral test for the native camel.exe bootstrap. Requires the build-native-exe profile to have
 * produced the platform-appropriate exe (target/camel-x64.exe on x86_64, target/camel-arm64.exe on aarch64); the test
 * fails loudly if it is missing so a broken native build does not pass silently.
 */
@EnabledOnOs(OS.WINDOWS)
class CamelExeBootstrapTest {

    private static final String EXE_ARCH = "aarch64".equals(System.getProperty("os.arch")) ? "arm64" : "x64";
    private static final Path BUILT_EXE = Paths.get("target/camel-" + EXE_ARCH + ".exe");
    private static final String CAPTURED_ARGS_FILE = "camel-args.txt";
    private static final String CAPTURE_ARGS_SCRIPT = "capture-arg.ps1";

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

    /**
     * Writes a fake camel.bat that appends each forwarded argument to {@link #CAPTURED_ARGS_FILE} as UTF-8.
     * <p>
     * Non-ASCII argument checks cannot rely on parsing process stdout: cmd.exe {@code echo} uses the console OEM code
     * page on Windows CI hosts, so Unicode forwarded correctly by camel.exe is still mangled (for example {@code ü}
     * becomes {@code ?}) before Java reads the pipe. We therefore capture arguments in a UTF-8 file via a helper
     * PowerShell script. The batch file passes all arguments at once using {@code %*} (the raw command-line tail)
     * rather than iterating with {@code %~1}, because per-argument expansion through cmd.exe's batch variable
     * substitution can corrupt non-ASCII characters on certain OEM code pages.
     */
    private void fakeBatCapturingArgsToUtf8File(Path dir, int exitCode) throws Exception {
        Path captureScript = dir.resolve(CAPTURE_ARGS_SCRIPT);
        String ps1 = "$outFile = $args[0]\r\n"
                     + "for ($i = 1; $i -lt $args.Count; $i++) {\r\n"
                     + "    [System.IO.File]::AppendAllText($outFile,\r\n"
                     + "        $args[$i] + [Environment]::NewLine,\r\n"
                     + "        [System.Text.UTF8Encoding]::new($false))\r\n"
                     + "}\r\n"
                     + "exit " + exitCode + "\r\n";
        Files.writeString(captureScript, ps1, StandardCharsets.UTF_8);

        Path bat = dir.resolve("camel.bat");
        String body = "@powershell -NoProfile -ExecutionPolicy Bypass -File \"%~dp0" + CAPTURE_ARGS_SCRIPT + "\" "
                      + "\"%~dp0" + CAPTURED_ARGS_FILE + "\" %*\r\n"
                      + "@exit /b %ERRORLEVEL%\r\n";
        Files.writeString(bat, body, StandardCharsets.UTF_8);
    }

    private List<String> readCapturedArgs(Path dir) throws Exception {
        Path file = dir.resolve(CAPTURED_ARGS_FILE);
        if (!Files.exists(file)) {
            return List.of();
        }
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    private Path stagedExe(Path dir) throws Exception {
        assertTrue(Files.exists(BUILT_EXE),
                BUILT_EXE + " must be built by the build-native-exe profile before this test");
        Path exe = dir.resolve(BUILT_EXE.getFileName().toString());
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
        pb.redirectErrorStream(true);
        Process p = pb.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Drain stdout on a background thread: reading it inline would block until the pipe closes
        // at process exit, making the waitFor timeout below unreachable if camel.exe hangs.
        Thread drain = new Thread(() -> {
            try {
                p.getInputStream().transferTo(out);
            } catch (IOException e) {
                // stream closes abruptly when the process is destroyed on timeout below
            }
        });
        drain.start();
        try {
            boolean exited = p.waitFor(60, TimeUnit.SECONDS);
            if (!exited) {
                p.destroyForcibly();
            }
            assertTrue(exited, "camel.exe did not exit in time");
            drain.join(TimeUnit.SECONDS.toMillis(5));
            Result r = new Result();
            r.exit = p.exitValue();
            r.stdout = out.toString(StandardCharsets.UTF_8);
            return r;
        } finally {
            if (p.isAlive()) {
                p.destroyForcibly();
            }
        }
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
        fakeBatCapturingArgsToUtf8File(dir, 0);
        Path exe = stagedExe(dir);

        Result r = run(exe, "run", "rüte-über.yaml");

        assertEquals(0, r.exit);
        List<String> captured = readCapturedArgs(dir);
        assertTrue(captured.contains("run"), captured.toString());
        assertTrue(captured.contains("rüte-über.yaml"), "Unicode argument must survive: " + captured);
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

    @Test
    void failsGracefullyWhenCamelBatIsMissing(@TempDir Path dir) throws Exception {
        Path exe = stagedExe(dir);

        Result r = run(exe, "version");

        assertTrue(r.exit != 0, "exit code must be non-zero when camel.bat is missing");
        assertTrue(r.stdout.length() > 0, "error message must be present in output");
    }
}
