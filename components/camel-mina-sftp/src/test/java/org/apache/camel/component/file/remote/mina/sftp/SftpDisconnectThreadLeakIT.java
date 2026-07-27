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
package org.apache.camel.component.file.remote.mina.sftp;

import java.io.File;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test that verifies SshClient daemon threads are cleaned up after disconnect (CAMEL-24273).
 * <p/>
 * Before the fix, each SFTP connect/disconnect cycle left behind NIO2 and timer daemon threads from the SshClient,
 * causing the thread count to grow continuously until the system's thread limit was exhausted.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/sftp/hostkey.pem')")
class SftpDisconnectThreadLeakIT extends SftpServerTestSupport {

    @Test
    void testDisconnectCleansUpSshClientThreads() throws Exception {
        // Capture the set of SshClient thread names before SFTP operations
        Set<String> threadsBefore = getSshClientThreadNames();

        // Perform multiple SFTP transfers — each creates a new SshClient with NIO2/timer threads
        int transfers = 5;
        for (int i = 0; i < transfers; i++) {
            template.sendBodyAndHeader(
                    "mina-sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                                       + "?username=admin&password=admin&disconnect=true&knownHostsFile="
                                       + service.getKnownHostsFile(),
                    "Hello World " + i, Exchange.FILE_NAME, "hello" + i + ".txt");

            File file = ftpFile("hello" + i + ".txt").toFile();
            assertTrue(file.exists(), "File should exist: " + file);
        }

        // Allow a brief moment for thread cleanup to complete
        Thread.sleep(500);

        // Verify files were written correctly
        for (int i = 0; i < transfers; i++) {
            File file = ftpFile("hello" + i + ".txt").toFile();
            assertTrue(file.exists(), "File should exist: " + file);
            assertEquals("Hello World " + i,
                    context.getTypeConverter().convertTo(String.class, file));
        }

        // After all transfers with disconnect=true, there should be no SshClient threads remaining.
        // Before the fix, each transfer would leave behind ~5 daemon threads (nio2, timer, resume),
        // resulting in ~25 leaked threads after 5 transfers.
        Set<String> threadsAfter = getSshClientThreadNames();
        Set<String> leakedThreads = threadsAfter.stream()
                .filter(name -> !threadsBefore.contains(name))
                .collect(Collectors.toSet());

        assertTrue(leakedThreads.isEmpty(),
                "SshClient daemon threads should be cleaned up after disconnect, but found leaked threads: "
                                            + leakedThreads);
    }

    /**
     * Returns the names of all alive threads whose name contains "SshClient".
     */
    private Set<String> getSshClientThreadNames() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .map(Thread::getName)
                .filter(name -> name.contains("SshClient"))
                .collect(Collectors.toSet());
    }
}
