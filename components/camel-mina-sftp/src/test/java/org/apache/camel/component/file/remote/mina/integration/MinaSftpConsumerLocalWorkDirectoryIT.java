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
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.TestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The local work file of a consumed file is deleted when the exchange is done, as with the sftp, ftp, smb and
 * azure-files components.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
class MinaSftpConsumerLocalWorkDirectoryIT extends MinaSftpServerTestSupport {

    @TempDir
    Path testDirectory;

    @Override
    protected void setupResources() throws Exception {
        service.getFtpRootDir().toFile().mkdirs();
    }

    private String consumerUri(String fileName) {
        // a long delay so that a rolled back file is not consumed again while the test checks the local work directory
        return "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
               + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false"
               + "&fileName=" + fileName + "&noop=true&initialDelay=0&delay=600000"
               + "&localWorkDirectory=" + testDirectory.resolve("lwd");
    }

    @Test
    void localWorkFileIsDeletedWhenTheExchangeCompletes() throws Exception {
        Files.writeString(ftpFile("hello.txt"), "Hello World");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(consumerUri("hello.txt"))
                        .process(this::assertBodyIsTheLocalWorkFile)
                        .to("mock:result");
            }

            private void assertBodyIsTheLocalWorkFile(Exchange exchange) {
                File body = exchange.getIn().getBody(File.class);
                assertEquals(testDirectory.resolve("lwd/hello.txt").toFile(), body);
                assertTrue(body.exists(), "The local work file should exist while the exchange is routed");
            }
        });

        mock.assertIsSatisfied();

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertFalse(Files.exists(testDirectory.resolve("lwd/hello.txt")),
                        "The local work file should be deleted when the exchange is done"));
    }

    @Test
    void localWorkFileIsMovedToTheTargetOfAFileProducer() throws Exception {
        Files.writeString(ftpFile("moved.txt"), "Moved World");

        NotifyBuilder done = new NotifyBuilder(context).whenDone(1).create();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(consumerUri("moved.txt"))
                        .to(TestSupport.fileUri(testDirectory, "out"));
            }
        });

        assertTrue(done.matches(10, TimeUnit.SECONDS), "The exchange should be done");

        assertEquals("Moved World", Files.readString(testDirectory.resolve("out/moved.txt")));
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertFalse(Files.exists(testDirectory.resolve("lwd/moved.txt")),
                        "The local work file should not be left in the local work directory"));
    }

    @Test
    void aSecondFileProducerFindsTheLocalWorkFileMoved() throws Exception {
        // as with camel-sftp: the first file producer moves the local work file, which the body still names
        Files.writeString(ftpFile("twice.txt"), "Twice World");

        NotifyBuilder failed = new NotifyBuilder(context).whenFailed(1).create();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(consumerUri("twice.txt"))
                        .to(TestSupport.fileUri(testDirectory, "first"))
                        .to(TestSupport.fileUri(testDirectory, "second"));
            }
        });

        assertTrue(failed.matches(10, TimeUnit.SECONDS), "The second file producer should fail");

        assertEquals("Twice World", Files.readString(testDirectory.resolve("first/twice.txt")));
        assertFalse(Files.exists(testDirectory.resolve("second/twice.txt")));
    }

    @Test
    void aBodyConvertedBeforeTheFileProducerCanBeWrittenTwice() throws Exception {
        // control, which passes without the fix too: the workaround of the upgrade guide
        Files.writeString(ftpFile("converted.txt"), "Converted World");

        NotifyBuilder done = new NotifyBuilder(context).whenCompleted(1).create();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(consumerUri("converted.txt"))
                        .convertBodyTo(byte[].class)
                        .to(TestSupport.fileUri(testDirectory, "first"))
                        .to(TestSupport.fileUri(testDirectory, "second"));
            }
        });

        assertTrue(done.matches(10, TimeUnit.SECONDS), "The exchange should complete");

        assertEquals("Converted World", Files.readString(testDirectory.resolve("first/converted.txt")));
        assertEquals("Converted World", Files.readString(testDirectory.resolve("second/converted.txt")));
    }

    @Test
    void localWorkFileIsDeletedWhenTheExchangeFails() throws Exception {
        Files.writeString(ftpFile("bye.txt"), "Bye World");

        NotifyBuilder failed = new NotifyBuilder(context).whenFailed(1).create();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(consumerUri("bye.txt"))
                        .throwException(new IllegalStateException("Forced"));
            }
        });

        assertTrue(failed.matches(10, TimeUnit.SECONDS), "The exchange should fail");

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertFalse(Files.exists(testDirectory.resolve("lwd/bye.txt")),
                        "The local work file should be deleted when the exchange fails"));
    }
}
