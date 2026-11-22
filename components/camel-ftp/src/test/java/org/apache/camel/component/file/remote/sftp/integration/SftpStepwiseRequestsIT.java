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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.SftpEndpoint;
import org.apache.camel.component.file.remote.SftpOperations;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.stream.ByteArrayInputStreamCache;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.FileUtil;
import org.apache.sshd.sftp.common.SftpConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With the default stepwise and autoCreate, an upload or a download does not change into the directory of its file and
 * back, nor check that the directory exists, for each file (CAMEL-26657). JSch checks every change of directory with
 * two requests (REALPATH and STAT), and stepwise changes directory one path segment at a time.
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

        // an OPEN, a WRITE and a CLOSE per file: no REALPATH and STAT to change directory and back, and no listing or
        // attributes to check that the directory exists
        assertEquals(10, service.requests(SftpConstants.SSH_FXP_OPEN));
        assertEquals(10, service.requests(SftpConstants.SSH_FXP_WRITE));
        assertEquals(10, service.requests(SftpConstants.SSH_FXP_CLOSE));
        assertEquals(0, service.requests(SftpConstants.SSH_FXP_REALPATH));
        assertEquals(0, service.requests(SftpConstants.SSH_FXP_STAT));
        assertEquals(0, service.requests(SftpConstants.SSH_FXP_LSTAT));
        assertEquals(0, service.requests(SftpConstants.SSH_FXP_OPENDIR));
        for (int i = 0; i < 10; i++) {
            assertEquals("message " + i, Files.readString(base.resolve("p1/out/file-" + i + ".txt")));
        }
    }

    @Test
    void directoryRemovedBetweenUploadsIsCreatedAgain() throws Exception {
        String uri = sftpUri("p1/out");
        template.sendBodyAndHeader(uri, "first", Exchange.FILE_NAME, "first.txt");

        // removed by someone else while the connection stays in it
        FileUtil.removeDir(base.resolve("p1").toFile());
        service.reset();

        template.sendBodyAndHeader(uri, "second", Exchange.FILE_NAME, "second.txt");

        assertEquals("second", Files.readString(base.resolve("p1/out/second.txt")));
        // the upload into the remembered directory failed, so the directory was created again and the upload tried
        // once more
        assertEquals(2, service.requests(SftpConstants.SSH_FXP_OPEN));
        assertTrue(service.requests(SftpConstants.SSH_FXP_MKDIR) >= 1, "requests: " + service.requests());
    }

    @Test
    void streamCacheBodyIsUploadedAgainIntoARemovedDirectory() throws Exception {
        String uri = sftpUri("p1/out");
        template.sendBodyAndHeader(uri, "first", Exchange.FILE_NAME, "first.txt");

        FileUtil.removeDir(base.resolve("p1").toFile());
        service.reset();

        // a stream body reaches the producer as a stream cache with the default stream caching: it is read again
        // from the start
        template.sendBodyAndHeader(uri, new ByteArrayInputStreamCache(new ByteArrayInputStream("second".getBytes())),
                Exchange.FILE_NAME, "second.txt");

        assertEquals("second", Files.readString(base.resolve("p1/out/second.txt")));
        assertTrue(service.requests(SftpConstants.SSH_FXP_MKDIR) >= 1, "requests: " + service.requests());
    }

    @Test
    void autoCreateRemembersOnlyTheMostRecentDirectories() throws Exception {
        // without stepwise, an upload into a remembered directory sends no request to change or check a directory
        String uri = sftpUri("") + "&stepwise=false";
        // more directories than the operations remember, each checked or created once
        for (int i = 0; i <= 100; i++) {
            template.sendBodyAndHeader(uri, "x", Exchange.FILE_NAME, "d" + i + "/x.txt");
        }

        service.reset();
        template.sendBodyAndHeader(uri, "y", Exchange.FILE_NAME, "d100/y.txt");
        assertEquals(0, service.requests(SftpConstants.SSH_FXP_STAT), "requests: " + service.requests());

        // the first one was forgotten, so it is checked again
        service.reset();
        template.sendBodyAndHeader(uri, "y", Exchange.FILE_NAME, "d0/y.txt");
        assertTrue(service.requests(SftpConstants.SSH_FXP_STAT) >= 1, "requests: " + service.requests());

        assertEquals("y", Files.readString(base.resolve("d0/y.txt")));
        assertEquals("y", Files.readString(base.resolve("d100/y.txt")));
    }

    @Test
    void readerBodyIsNotUploadedAgainIntoARemovedDirectory() throws Exception {
        String uri = sftpUri("p1/out");
        template.sendBodyAndHeader(uri, "first", Exchange.FILE_NAME, "first.txt");

        FileUtil.removeDir(base.resolve("p1").toFile());
        service.reset();

        // the failed upload may have read the reader (which is not an InputStream), so a second upload could store a
        // part of it
        StringReader stream = new StringReader("second");
        assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, stream, Exchange.FILE_NAME, "second.txt"));
        assertEquals(1, service.requests(SftpConstants.SSH_FXP_OPEN));
        assertEquals(0, service.requests(SftpConstants.SSH_FXP_MKDIR));

        // the next upload checks the directory again
        template.sendBodyAndHeader(uri, "third", Exchange.FILE_NAME, "third.txt");
        assertEquals("third", Files.readString(base.resolve("p1/out/third.txt")));
    }

    @Test
    void uploadsIntoAnotherDirectoryAndWithoutPathUseTheStartDirectory() throws Exception {
        String uri = sftpUri("");
        template.sendBodyAndHeader(uri, "1", Exchange.FILE_NAME, "a/1.txt");
        template.sendBodyAndHeader(uri, "2", Exchange.FILE_NAME, "b/2.txt");
        template.sendBodyAndHeader(uri, "3", Exchange.FILE_NAME, "a/3.txt");
        template.sendBodyAndHeader(uri, "x", Exchange.FILE_NAME, "x.txt");

        // each name is relative to the directory of the endpoint, whichever directory the previous upload left the
        // connection in
        assertEquals("1", Files.readString(base.resolve("a/1.txt")));
        assertEquals("2", Files.readString(base.resolve("b/2.txt")));
        assertEquals("3", Files.readString(base.resolve("a/3.txt")));
        assertEquals("x", Files.readString(base.resolve("x.txt")));
        assertFalse(Files.exists(base.resolve("b/a")));
        assertFalse(Files.exists(base.resolve("a/x.txt")));
    }

    @Test
    void uploadAfterAChangeOfDirectoryThatFailedHalfWayUsesTheStartDirectory() throws Exception {
        Files.createDirectories(base.resolve("p1"));
        Files.createDirectories(base.resolve("p2"));
        // the operations themselves, as the producer disconnects after a failed upload
        SftpEndpoint endpoint = context.getEndpoint(sftpUri("") + "&autoCreate=false", SftpEndpoint.class);
        SftpOperations operations = (SftpOperations) endpoint.createRemoteFileOperations();
        operations.connect(endpoint.getConfiguration(), null);
        try {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody("content");

            // the change into p1 succeeds, the change into missing fails
            assertThrows(GenericFileOperationFailedException.class,
                    () -> operations.storeFile(base + "/p1/missing/a.txt", exchange, -1));
            assertTrue(operations.storeFile(base + "/p2/b.txt", exchange, -1));
        } finally {
            operations.disconnect();
        }

        assertEquals("content", Files.readString(base.resolve("p2/b.txt")));
        assertFalse(Files.exists(base.resolve("p1/" + base)));
    }

    @Test
    void readLockChecksEveryFileOfAPollAfterADownload() throws Exception {
        Files.createDirectories(base.resolve("inbox"));
        Files.writeString(base.resolve("inbox/a.txt"), "a");
        Files.writeString(base.resolve("inbox/b.txt"), "b");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("a", "b");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // the read lock checks the second file by its name relative to the starting directory, after the
                // download of the first file left the connection in the inbox directory
                from(sftpUri("inbox") + "&noop=true&initialDelay=0&delay=60000&readLock=changed"
                     + "&readLockCheckInterval=100&readLockTimeout=5000")
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void operationAfterAStepwiseUploadUsesNamesRelativeToTheStartDirectory() throws Exception {
        Files.createDirectories(base.resolve("p1/out"));
        SftpEndpoint endpoint = context.getEndpoint(sftpUri(""), SftpEndpoint.class);
        SftpOperations operations = (SftpOperations) endpoint.createRemoteFileOperations();
        operations.connect(endpoint.getConfiguration(), null);
        try {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody("content");

            assertTrue(operations.storeFile(base + "/p1/out/a.txt", exchange, -1));

            // base is a relative path: the upload left the connection in the directory of the file, so the existence
            // check must change back before it resolves the name, else it would look for p1/out/<base>/p1/out/a.txt
            assertTrue(operations.existsFile(base + "/p1/out/a.txt"));
        } finally {
            operations.disconnect();
        }
    }

    private String sftpUri(String directory) {
        return "sftp://localhost:" + service.getPort() + "/" + base + "/" + directory
               + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile();
    }
}
