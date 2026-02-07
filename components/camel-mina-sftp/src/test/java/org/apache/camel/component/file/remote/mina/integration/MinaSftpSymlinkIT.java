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

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for symlink handling with the embedded SFTP server and Camel mina-sftp component.
 *
 * <h2>Key Finding</h2>
 * <p>
 * The embedded SFTP server (using NativeFileSystemFactory) correctly supports symlinks. Both relative and absolute
 * symlinks work at the SFTP protocol level.
 * </p>
 *
 * <p>
 * The Camel mina-sftp component can read and write through symlinks as long as the paths are correctly resolved.
 * </p>
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class MinaSftpSymlinkIT extends MinaSftpServerTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(MinaSftpSymlinkIT.class);

    private String ftpRootDir;

    @BeforeEach
    public void doPostSetup() throws Exception {
        service.getFtpRootDir().toFile().mkdirs();
        ftpRootDir = service.getFtpRootDir().toString();
        LOG.info("FTP root directory: {}", ftpRootDir);
    }

    private String baseUri() {
        return "mina-sftp://localhost:" + service.getPort() + "/" + ftpRootDir
               + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false";
    }

    /**
     * Test that the Camel mina-sftp consumer can read files through relative symlinks.
     */
    @Test
    @Timeout(60)
    public void testCamelConsumerWithRelativeSymlink() throws Exception {
        // Create target file
        Path targetPath = ftpFile("consumer-target.txt");
        Files.writeString(targetPath, "Content via symlink");
        LOG.info("Created target file: {}", targetPath);

        // Create relative symlink
        Path symlinkPath = ftpFile("consumer-symlink.txt");
        Files.deleteIfExists(symlinkPath);
        Files.createSymbolicLink(symlinkPath, Path.of("consumer-target.txt"));
        LOG.info("Created symlink: {} -> {}", symlinkPath, Files.readSymbolicLink(symlinkPath));

        // Verify symlink works at filesystem level
        assertTrue(Files.isSymbolicLink(symlinkPath), "Should be a symlink");
        assertEquals("Content via symlink", Files.readString(symlinkPath), "Symlink should be readable");

        // Setup mock endpoint
        MockEndpoint mock = getMockEndpoint("mock:symlink-result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Content via symlink");
        mock.setResultWaitTime(30000);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(baseUri() + "&fileName=consumer-symlink.txt&noop=true&delay=1000&initialDelay=0")
                        .routeId("symlinkConsumerTest")
                        .log("Received file: ${header.CamelFileName} with body: ${body}")
                        .to("mock:symlink-result");
            }
        });

        context.getRouteController().startRoute("symlinkConsumerTest");

        mock.assertIsSatisfied();

        LOG.info("SUCCESS: Camel consumer read file through relative symlink!");

        context.getRouteController().stopRoute("symlinkConsumerTest");
    }

    /**
     * Test that the Camel mina-sftp producer can write files through relative symlinks.
     */
    @Test
    @Timeout(60)
    public void testCamelProducerWithRelativeSymlink() throws Exception {
        // Create initial target file
        Path targetPath = ftpFile("producer-target.txt");
        Files.writeString(targetPath, "Original content");
        LOG.info("Created target file: {}", targetPath);

        // Create relative symlink
        Path symlinkPath = ftpFile("producer-symlink.txt");
        Files.deleteIfExists(symlinkPath);
        Files.createSymbolicLink(symlinkPath, Path.of("producer-target.txt"));
        LOG.info("Created symlink: {} -> {}", symlinkPath, Files.readSymbolicLink(symlinkPath));

        // Send file through symlink
        template.sendBodyAndHeader(baseUri() + "&fileExist=Override", "Updated via Camel symlink",
                Exchange.FILE_NAME, "producer-symlink.txt");

        // Verify target file was updated
        String content = Files.readString(targetPath);
        LOG.info("Target file content after producer write: '{}'", content);
        assertEquals("Updated via Camel symlink", content, "Target file should be updated through symlink");

        LOG.info("SUCCESS: Camel producer wrote file through relative symlink!");
    }
}
