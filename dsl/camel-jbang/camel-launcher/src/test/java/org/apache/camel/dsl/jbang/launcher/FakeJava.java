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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

/**
 * Test helper that materializes fake {@code java} executables and a throwaway launcher home so the shared
 * Java-discovery contract in camel.sh / camel.bat can be exercised without a real JDK.
 */
final class FakeJava {

    private static final long PROCESS_TIMEOUT_SECONDS = 60;
    private static final long CLEANUP_TIMEOUT_SECONDS = 10;

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
        ExecutorService collectors = Executors.newFixedThreadPool(2);
        Throwable failure = null;
        try {
            Future<byte[]> stdout = collectors.submit(() -> p.getInputStream().readAllBytes());
            Future<byte[]> stderr = collectors.submit(() -> p.getErrorStream().readAllBytes());
            if (!p.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                terminate(p);
                throw new IllegalStateException("launcher did not exit in time");
            }
            String out = new String(await(stdout, "stdout"), StandardCharsets.UTF_8);
            String err = new String(await(stderr, "stderr"), StandardCharsets.UTF_8);
            return new Result(p.exitValue(), out, err);
        } catch (Exception | Error e) {
            failure = e;
            throw e;
        } finally {
            try {
                cleanup(p, collectors);
            } catch (Exception | Error cleanupFailure) {
                if (failure != null) {
                    failure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
    }

    private static void cleanup(Process process, ExecutorService collectors) throws Exception {
        Throwable failure = null;
        try {
            terminate(process);
        } catch (Exception | Error e) {
            failure = e;
        }
        collectors.shutdownNow();
        try {
            if (!collectors.awaitTermination(CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("launcher stream collectors did not terminate");
            }
        } catch (Exception | Error e) {
            if (failure != null) {
                failure.addSuppressed(e);
            } else {
                failure = e;
            }
        }
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    private static byte[] await(Future<byte[]> collector, String stream) throws Exception {
        try {
            return collector.get(CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("failed to collect launcher " + stream, cause);
        } catch (TimeoutException e) {
            throw new IllegalStateException("timed out collecting launcher " + stream, e);
        }
    }

    private static void terminate(Process process) throws InterruptedException {
        if (process.isAlive()) {
            process.destroyForcibly();
            if (!process.waitFor(CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("launcher could not be terminated");
            }
        }
    }
}
