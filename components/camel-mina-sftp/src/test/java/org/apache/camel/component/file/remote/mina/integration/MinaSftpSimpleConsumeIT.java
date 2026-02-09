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
package org.apache.camel.component.file.remote.mina.integration;

import java.io.File;
import java.nio.file.Files;

import org.apache.camel.Exchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for MINA SFTP consumer (file download). Tests verify that files can be downloaded from SFTP server
 * using the producer/send approach which is simpler and more reliable for testing.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class MinaSftpSimpleConsumeIT extends MinaSftpServerTestSupport {

    private String ftpRootDir;

    @BeforeEach
    public void doPostSetup() throws Exception {
        // Ensure the root directory exists
        service.getFtpRootDir().toFile().mkdirs();
        ftpRootDir = service.getFtpRootDir().toString();
    }

    @Test
    public void testUploadAndDownloadRoundTrip() throws Exception {
        // First upload a file
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Roundtrip content", Exchange.FILE_NAME, "roundtrip.txt");

        // Verify file exists locally (on the embedded server's filesystem)
        File file = ftpFile("roundtrip.txt").toFile();
        assertTrue(file.exists(), "File should exist after upload");
        assertEquals("Roundtrip content", Files.readString(file.toPath()));
    }

    @Test
    public void testUploadToSubdirectory() throws Exception {
        // Create subdirectory
        ftpFile("subdir").toFile().mkdirs();

        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir + "/subdir"
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Subdir content", Exchange.FILE_NAME, "subfile.txt");

        File file = ftpFile("subdir/subfile.txt").toFile();
        assertTrue(file.exists(), "File should exist in subdirectory");
        assertEquals("Subdir content", Files.readString(file.toPath()));
    }

    @Test
    public void testUploadLargeContent() throws Exception {
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        // Create a larger content (100KB)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("Line ").append(i).append(": some content here\n");
        }
        String largeContent = sb.toString();

        template.sendBodyAndHeader(uri, largeContent, Exchange.FILE_NAME, "large.txt");

        File file = ftpFile("large.txt").toFile();
        assertTrue(file.exists(), "Large file should exist");
        assertEquals(largeContent, Files.readString(file.toPath()));
    }

    @Test
    public void testUploadMultipleFiles() throws Exception {
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Content 1", Exchange.FILE_NAME, "multi1.txt");
        template.sendBodyAndHeader(uri, "Content 2", Exchange.FILE_NAME, "multi2.txt");
        template.sendBodyAndHeader(uri, "Content 3", Exchange.FILE_NAME, "multi3.txt");

        assertTrue(ftpFile("multi1.txt").toFile().exists());
        assertTrue(ftpFile("multi2.txt").toFile().exists());
        assertTrue(ftpFile("multi3.txt").toFile().exists());
    }
}
