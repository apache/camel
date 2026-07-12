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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

/**
 * Test helper that materializes fake {@code java} executables and a throwaway launcher home so the shared
 * Java-discovery contract in camel.sh / camel.bat can be exercised without a real JDK.
 */
final class FakeJava {

    static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    private FakeJava() {
    }

    record Result(int exitCode, String stdout, String stderr) {
    }

    static Path writeFakeJava(Path dir, String name, String versionBanner, int normalExit, String marker)
            throws IOException {
        Files.createDirectories(dir);
        if (WINDOWS) {
            Path exe = dir.resolve(name + ".bat");
            String body = "@echo off\r\n"
                          + "if \"%~1\"==\"-version\" (\r\n"
                          + "  echo " + versionBanner + " 1>&2\r\n"
                          + "  exit /b 0\r\n"
                          + ")\r\n"
                          + "echo RAN=" + marker + "\r\n"
                          + "echo ARGS=%*\r\n"
                          + "exit /b " + normalExit + "\r\n";
            Files.writeString(exe, body, StandardCharsets.UTF_8);
            return exe;
        }
        Path exe = dir.resolve(name);
        String body = "#!/bin/sh\n"
                      + "if [ \"$1\" = \"-version\" ]; then\n"
                      + "  echo '" + versionBanner + "' 1>&2\n"
                      + "  exit 0\n"
                      + "fi\n"
                      + "echo \"RAN=" + marker + "\"\n"
                      + "printf 'ARGS='; for a in \"$@\"; do printf '%s|' \"$a\"; done; echo\n"
                      + "exit " + normalExit + "\n";
        Files.writeString(exe, body, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(exe, Set.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE));
        return exe;
    }

    static Path newLauncherHome(Path base, Path scriptResource) throws IOException {
        Path home = Files.createTempDirectory(base, "launcher-home");
        Path script = home.resolve(scriptResource.getFileName().toString());
        Files.copy(scriptResource, script);
        if (!WINDOWS) {
            Files.setPosixFilePermissions(script, Set.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE));
        }
        // A stub jar so the launcher's "camel-launcher-*.jar" glob resolves; fake java never reads it.
        Files.writeString(home.resolve("camel-launcher-9.9.9.jar"), "stub");
        return home;
    }

    static Result run(Path script, Map<String, String> env, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        if (WINDOWS) {
            cmd.add("cmd.exe");
            cmd.add("/c");
            cmd.add(script.toString());
        } else {
            cmd.add("/bin/sh");
            cmd.add(script.toString());
        }
        for (String a : args) {
            cmd.add(a);
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().clear();
        // Keep a minimal PATH so /bin/sh and cmd.exe internals resolve; tests override as needed.
        pb.environment().put("PATH", WINDOWS ? System.getenv("PATH") : "/usr/bin:/bin");
        if (WINDOWS) {
            pb.environment().put("SystemRoot", System.getenv("SystemRoot"));
            pb.environment().put("PATHEXT", ".COM;.EXE;.BAT;.CMD");
        }
        pb.environment().putAll(env);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(60, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IllegalStateException("launcher did not exit in time");
        }
        return new Result(p.exitValue(), out, err);
    }
}
