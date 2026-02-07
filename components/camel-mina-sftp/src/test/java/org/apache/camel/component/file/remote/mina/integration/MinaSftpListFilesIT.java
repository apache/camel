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
 * Integration tests for MINA SFTP file listing via producer operations.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class MinaSftpListFilesIT extends MinaSftpServerTestSupport {

    private String ftpRootDir;

    @BeforeEach
    public void doPostSetup() throws Exception {
        // Ensure the root directory exists
        service.getFtpRootDir().toFile().mkdirs();
        ftpRootDir = service.getFtpRootDir().toString();
    }

    @Test
    public void testUploadAndVerifyFileExists() throws Exception {
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        // Upload files
        template.sendBodyAndHeader(uri, "Content 1", Exchange.FILE_NAME, "verify1.txt");
        template.sendBodyAndHeader(uri, "Content 2", Exchange.FILE_NAME, "verify2.txt");
        template.sendBodyAndHeader(uri, "Content 3", Exchange.FILE_NAME, "verify3.txt");

        // Verify files exist on the embedded server
        assertTrue(ftpFile("verify1.txt").toFile().exists());
        assertTrue(ftpFile("verify2.txt").toFile().exists());
        assertTrue(ftpFile("verify3.txt").toFile().exists());
    }

    @Test
    public void testUploadPreservesFileContent() throws Exception {
        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        String content = "Test content for metadata verification\nLine 2\nLine 3";
        template.sendBodyAndHeader(uri, content, Exchange.FILE_NAME, "metadata.txt");

        File file = ftpFile("metadata.txt").toFile();
        assertTrue(file.exists());
        assertEquals(content, Files.readString(file.toPath()));
        assertTrue(file.length() > 0, "File should have size > 0");
        assertTrue(file.lastModified() > 0, "File should have modification time");
    }

    @Test
    public void testUploadToSubdirectory() throws Exception {
        // Pre-create subdirectory
        ftpFile("existing-subdir").toFile().mkdirs();

        String uri = "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir + "/existing-subdir"
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Subdir content", Exchange.FILE_NAME, "subfile.txt");

        File subdir = ftpFile("existing-subdir").toFile();
        assertTrue(subdir.isDirectory(), "Subdir should be a directory");

        File file = ftpFile("existing-subdir/subfile.txt").toFile();
        assertTrue(file.exists(), "File should exist in subdirectory");
        assertTrue(!file.isDirectory(), "File should not be a directory");
    }
}
