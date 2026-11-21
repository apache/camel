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
package org.apache.camel.component.file.remote.sftp.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.remote.SftpEndpoint;
import org.apache.camel.component.file.remote.SftpOperations;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.FileUtil;
import org.apache.sshd.sftp.common.SftpConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With the default stepwise and autoCreate, an upload or a download does not change into the directory of its file and
 * back, nor check that the directory exists, for each file: JSch checks every change of directory with two requests per
 * path segment (CAMEL-26657).
 */
class SftpStepwiseRequestsIT extends CamelTestSupport {

    @RegisterExtension
    SftpExistsFileIT.CountingSftpEmbeddedService service = new SftpExistsFileIT.CountingSftpEmbeddedService();

    private Path base;

    @BeforeEach
    void createTestDirectory() throws IOException {
        // the root directory of the server is shared by the tests of this class and kept between runs
        base = Files.createTempDirectory(Files.createDirectories(service.getFtpRootDir()), "test");
    }

    @Test
    void uploadsIntoTheSameDirectorySendOnlyTheFile() throws Exception {
        String uri = sftpUri("p1/out");
        // connects, creates the directory and changes into it
        template.sendBodyAndHeader(uri, "warm-up", Exchange.FILE_NAME, "warm-up.txt");

        service.reset();
        for (int i = 0; i < 10; i++) {
            template.sendBodyAndHeader(uri, "message " + i, Exchange.FILE_NAME, "file-" + i + ".txt");
        }

        // an OPEN, a WRITE and a CLOSE per file: no REALPATH and STAT to change directory and back, and no listing to
        // check that the directory exists
        assertEquals(10, service.requests(SftpConstants.SSH_FXP_OPEN));
        assertEquals(10, service.requests(SftpConstants.SSH_FXP_WRITE));
        assertEquals(10, service.requests(SftpConstants.SSH_FXP_CLOSE));
        assertEquals(30, service.requests(), "requests: " + service.requests());
        assertEquals("message 7", Files.readString(base.resolve("p1/out/file-7.txt")));
    }

    @Test
    void directoryRemovedBetweenUploadsIsCreatedAgain() throws Exception {
        String uri = sftpUri("p1/out");
        template.sendBodyAndHeader(uri, "first", Exchange.FILE_NAME, "first.txt");

        // removed by someone else: autoCreate remembers that it created it on this connection
        FileUtil.removeDir(base.resolve("p1").toFile());

        template.sendBodyAndHeader(uri, "second", Exchange.FILE_NAME, "second.txt");

        assertEquals("second", Files.readString(base.resolve("p1/out/second.txt")));
    }

    @Test
    void operationAfterAStepwiseUploadUsesNamesRelativeToTheStartDirectory() throws Exception {
        Files.createDirectories(base.resolve("p1/out"));
        SftpEndpoint endpoint = context.getEndpoint(sftpUri(""), SftpEndpoint.class);
        SftpOperations operations = (SftpOperations) endpoint.createRemoteFileOperations();
        operations.connect(endpoint.getConfiguration(), null);
        try {
            String start = operations.getCurrentDirectory();
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody("content");

            assertTrue(operations.storeFile(base + "/p1/out/a.txt", exchange, -1));

            // the upload left the connection in the directory of the file, so the next operation changes back
            // before it resolves its name, else it would look for p1/out/p1/out/a.txt
            assertTrue(operations.existsFile(base + "/p1/out/a.txt"));
            assertEquals(start, operations.getCurrentDirectory());
        } finally {
            operations.disconnect();
        }
    }

    private String sftpUri(String directory) {
        return "sftp://localhost:" + service.getPort() + "/" + base + "/" + directory
               + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile();
    }
}
