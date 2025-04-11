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
package org.apache.camel.component.file.consumer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.remote.sftp.integration.SftpServerTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//import org.apache.sshd.util.test.CoreTestSupportUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotentReadLockIT extends SftpServerTestSupport {
    //    private static final String TEST_PATH = "test/IdempotentReadLock";

    private static Logger logger = LogManager.getLogger();

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        testConfigurationBuilder.withUseAdviceWith(true);
    }

    @Test
    void testFile() throws Exception {
        runTest("file_test.txt", "file:{{ftp.root.dir}}?fileName=file_test.txt");
    }

    @Test
    void testSftp() throws Exception {
        runTest("sftp_test.txt", "sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}?fileName=sftp_test.txt"
                                 + "&username=admin&password=admin&disconnect=true&knownHostsFile="
                                 + service.getKnownHostsFile());
    }

    private void runTest(String testFileName, String fromUriPrefix) throws Exception {
        File ftpRootDir = service.getFtpRootDir().toFile();
        File testFile = new File(ftpRootDir, testFileName);

        AdviceWith.adviceWith(context, "test-route", a -> {
            a.replaceFromWith(fromUriPrefix
                              + "&delay=200&initialDelay=100"
                              + "&idempotent=true"
                              + "&idempotentEager=true"
                              + "&idempotentKey=test:${file:onlyname}"
                              + "&readLock=changed"
                              + "&readLockMinAge=1000"
                              + "&readLockCheckInterval=500"
                              + "&readLockTimeout=1500"
                              + "&moveFailed=failed/${file:name}");
        });

        if (ftpRootDir.exists()) {
            FileUtils.cleanDirectory(ftpRootDir);
        }

        resultEndpoint.expectedMessageCount(1);

        context.start();

        Endpoint endpoint = context.getRoutes().get(0).getEndpoint();
        assertTrue(endpoint instanceof GenericFileEndpoint);
        GenericFileEndpoint<?> fileEndpoint = (GenericFileEndpoint<?>) endpoint;

        assertTrue(fileEndpoint.isIdempotent());
        IdempotentRepository idempotentRepository = fileEndpoint.getIdempotentRepository();
        assertFalse(idempotentRepository.contains("test:" + testFile.getName()));

        System.out.printf("***** Starting to write to file '%s' at %s%n", testFile.getName(), new Date());
        try (FileOutputStream fos = new FileOutputStream(testFile)) {
            logger.info("Updating file " + testFile.getPath());
            for (int i = 1; i <= 10; i++) {
                String line = String.format("Hello World %d%n", i);
                fos.write(line.getBytes());
                logger.trace("\tFile {} updated with: {}", testFile.getPath(), line);
                Thread.sleep(500);
            }
        } catch (IOException | InterruptedException e) {
            logger.warn("Failed to update file: " + testFile.getPath(), e);
        }
        System.out.printf("***** Done writing to file '%s' at %s%n", testFile.getName(), new Date());

        MockEndpoint.assertIsSatisfied(context);

        assertTrue(idempotentRepository.contains("test:" + testFile.getName()));

        context.stop();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("file:placeholder")
                        .id("test-route")
                        .process(exchange -> {
                            String fileName = exchange.getMessage().getHeader("CamelFileName", String.class);
                            System.out.printf("***** File '%s' received at %s%n", fileName, new Date());
                        })
                        .to("mock:result");
            }
        };
    }
}
