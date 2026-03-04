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
package org.apache.camel.component.docling;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demonstrates the ProcessBuilder deadlock pattern and verifies the fix.
 *
 * <p>
 * Before the fix, stdout and stderr were read sequentially in {@code executeDoclingCommand()}. If the child process
 * wrote more than ~64KB to stderr while the Java side was blocked reading stdout, both processes would deadlock: the
 * child blocked writing to the full stderr pipe, and Java blocked reading from stdout (waiting for EOF that never comes
 * because the child is stuck).
 *
 * <p>
 * The fix reads stderr in a separate daemon thread, allowing both streams to drain concurrently.
 */
@DisabledOnOs(OS.WINDOWS)
class DoclingCliDeadlockTest {

    /**
     * Verifies that reading stdout and stderr concurrently (the fixed approach) completes even when the process writes
     * large amounts to both streams.
     */
    @Test
    void concurrentStreamReadingDoesNotDeadlock() throws Exception {
        // Spawn a process that writes >64KB to both stdout and stderr simultaneously.
        // With sequential reading, this would deadlock. With concurrent reading, it completes.
        ProcessBuilder pb = new ProcessBuilder(
                List.of(
                        "bash", "-c",
                        // Write 100KB to stderr and 100KB to stdout
                        "for i in $(seq 1 2000); do echo \"stderr line $i: padding to fill buffer\" >&2; "
                                      + "echo \"stdout line $i: padding to fill buffer\"; done"));

        Process process = pb.start();

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        // Read stderr in a separate thread (the fix)
        Thread stderrReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderr.append(line).append("\n");
                }
            } catch (IOException e) {
                // ignore
            }
        }, "test-stderr-reader");
        stderrReader.setDaemon(true);
        stderrReader.start();

        // Read stdout in the main thread
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stdout.append(line).append("\n");
            }
        }

        // Should complete within a reasonable time — no deadlock
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        stderrReader.join(5000);

        assertTrue(finished, "Process should complete without deadlock");
        assertEquals(0, process.exitValue());

        // Verify both streams were fully read
        assertTrue(stdout.length() > 64 * 1024,
                "Should have read >64KB from stdout but got " + stdout.length());
        assertTrue(stderr.length() > 64 * 1024,
                "Should have read >64KB from stderr but got " + stderr.length());
    }
}
