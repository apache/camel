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
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sftp component uses a remote file or directory name as it is, also when it contains {@code *} or {@code ?}, which
 * the JSch library would otherwise read as wildcards. A {@code \} is a path separator, in every operation.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "Windows does not allow *, ? and \\ in file names")
class SftpFileNameWithSpecialCharactersIT extends CamelTestSupport {

    @RegisterExtension
    SftpExistsFileIT.CountingSftpEmbeddedService service = new SftpExistsFileIT.CountingSftpEmbeddedService();

    private Path base;
    private Path inbox;

    @BeforeEach
    void createTestDirectory() throws IOException {
        // the root directory of the server is shared by the tests of this class and kept between runs
        base = Files.createTempDirectory(Files.createDirectories(service.getFtpRootDir()), "test");
        inbox = Files.createDirectories(base.resolve("inbox"));
    }

    @ParameterizedTest
    @CsvSource({ "data-?.txt, true", "data-*.txt, true", "data-?.txt, false", "data-*.txt, false" })
    void storeFileWithWildcardInName(String name, boolean stepwise) throws Exception {
        Files.writeString(inbox.resolve("data-1.txt"), "other");

        template.sendBodyAndHeader(sftpUri("inbox") + "&stepwise=" + stepwise, "stored", Exchange.FILE_NAME, name);

        assertEquals("stored", Files.readString(inbox.resolve(name)));
        assertEquals("other", Files.readString(inbox.resolve("data-1.txt")));
    }

    @ParameterizedTest
    @CsvSource({ ".?, true", ".*, true", ".?, false", ".*, false" })
    void storeFileInDirectoryWithWildcardName(String directory, boolean existDirCheckUsingLs) throws Exception {
        template.sendBodyAndHeader(sftpUri("inbox") + "&existDirCheckUsingLs=" + existDirCheckUsingLs, "stored",
                Exchange.FILE_NAME, directory + "/hello.txt");

        assertEquals("stored", Files.readString(inbox.resolve(directory).resolve("hello.txt")));
        assertFalse(Files.exists(base.resolve("hello.txt")));
    }

    @Test
    void chmodDirectoryOnlyChangesCreatedDirectory() throws Exception {
        Path other = Files.createDirectories(inbox.resolve("d1"));
        Set<PosixFilePermission> mode = Files.getPosixFilePermissions(other);

        template.sendBodyAndHeader(sftpUri("inbox") + "&chmodDirectory=777", "stored", Exchange.FILE_NAME, "d?/hello.txt");

        assertEquals("stored", Files.readString(inbox.resolve("d?").resolve("hello.txt")));
        assertEquals(PosixFilePermissions.fromString("rwxrwxrwx"), Files.getPosixFilePermissions(inbox.resolve("d?")));
        assertEquals(mode, Files.getPosixFilePermissions(other));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void appendToFileWithWildcardInName(boolean stepwise) throws Exception {
        Files.writeString(inbox.resolve("data-?.txt"), "first ");
        Files.writeString(inbox.resolve("data-1.txt"), "other");

        template.sendBodyAndHeader(sftpUri("inbox") + "&fileExist=Append&stepwise=" + stepwise, "second",
                Exchange.FILE_NAME, "data-?.txt");

        assertEquals("first second", Files.readString(inbox.resolve("data-?.txt")));
        assertEquals("other", Files.readString(inbox.resolve("data-1.txt")));
    }

    @Test
    void checksumFileOfFileWithWildcardInName() throws Exception {
        Files.writeString(inbox.resolve("data-1.txt.MD5"), "other");

        template.sendBodyAndHeader(sftpUri("inbox") + "&checksumFileAlgorithm=MD5", "stored", Exchange.FILE_NAME,
                "data-?.txt");

        assertEquals("stored", Files.readString(inbox.resolve("data-?.txt")));
        assertTrue(Files.exists(inbox.resolve("data-?.txt.MD5")));
        assertEquals("other", Files.readString(inbox.resolve("data-1.txt.MD5")));
    }

    @Test
    void storeAgainWithBackslashInNameKeepsFileWithThatLiteralName() throws Exception {
        String uri = sftpUri("inbox") + "&tempPrefix=inprogress-";
        template.sendBodyAndHeader(uri, "first", Exchange.FILE_NAME, "sub\\file.txt");
        // the \ is a path separator, so a file with this literal name is another file
        Files.writeString(inbox.resolve("sub\\file.txt"), "unrelated");

        template.sendBodyAndHeader(uri, "second", Exchange.FILE_NAME, "sub\\file.txt");

        assertEquals("second", Files.readString(inbox.resolve("sub").resolve("file.txt")));
        assertEquals("unrelated", Files.readString(inbox.resolve("sub\\file.txt")));
    }

    @Test
    void tempPrefixWithBackslashInDirectoryNameWhenServerDoesNotAnswerLstat() throws Exception {
        Files.writeString(base.resolve("keep.txt"), "other");
        // the existence check of the target then lists the directory of the name
        service.onLstat(() -> {
            throw new AccessDeniedException("lstat");
        });

        template.sendBodyAndHeader(sftpUri("inbox") + "&tempPrefix=inprogress-", "stored", Exchange.FILE_NAME,
                "\\.\\./keep.txt");

        assertEquals("stored", Files.readString(inbox.resolve("keep.txt")));
        assertEquals("other", Files.readString(base.resolve("keep.txt")));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void chmodOnlyChangesStoredFile(boolean stepwise) throws Exception {
        Path other = inbox.resolve("data-1.txt");
        Files.writeString(other, "other");
        Set<PosixFilePermission> mode = Files.getPosixFilePermissions(other);

        template.sendBodyAndHeader(sftpUri("inbox") + "&chmod=777&stepwise=" + stepwise, "stored",
                Exchange.FILE_NAME, "data-?.txt");

        assertEquals("stored", Files.readString(inbox.resolve("data-?.txt")));
        assertEquals(PosixFilePermissions.fromString("rwxrwxrwx"), Files.getPosixFilePermissions(inbox.resolve("data-?.txt")));
        assertEquals(mode, Files.getPosixFilePermissions(other));
    }

    @ParameterizedTest
    @CsvSource({ "true, true", "true, false", "false, true", "false, false" })
    void consumeAndDeleteFileWithWildcardInName(boolean download, boolean stepwise) throws Exception {
        Files.writeString(inbox.resolve("data-*.txt"), "wanted");
        Files.writeString(inbox.resolve("data-1.txt"), "other");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "data-*.txt");
        if (download) {
            mock.expectedBodiesReceived("wanted");
        }

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                RouteDefinition route = from(sftpUri("inbox") + "&fileName=data-*.txt&delete=true&initialDelay=0"
                                             + "&delay=60000&download=" + download + "&stepwise=" + stepwise);
                if (download) {
                    route.convertBodyTo(String.class);
                }
                route.to("mock:result");
            }
        });

        mock.assertIsSatisfied();
        // the file is deleted once the exchange is done
        await().atMost(10, TimeUnit.SECONDS).until(() -> !Files.exists(inbox.resolve("data-*.txt")));
        assertTrue(Files.exists(inbox.resolve("data-1.txt")));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void consumeAndMoveFileWithWildcardInName(boolean stepwise) throws Exception {
        Files.writeString(inbox.resolve("data-?.txt"), "wanted");
        Files.writeString(inbox.resolve("data-1.txt"), "other");
        Path done = Files.createDirectories(inbox.resolve("done"));
        Files.writeString(done.resolve("data-2.txt"), "done before");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "data-?.txt");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(sftpUri("inbox") + "&fileName=data-?.txt&move=done/${file:name}&initialDelay=0"
                     + "&delay=60000&stepwise=" + stepwise)
                        .to("mock:result");
            }
        });

        mock.assertIsSatisfied();
        // the file is renamed into the done directory once the exchange is done
        await().atMost(10, TimeUnit.SECONDS).until(() -> Files.exists(done.resolve("data-?.txt")));
        assertEquals("wanted", Files.readString(done.resolve("data-?.txt")));
        assertFalse(Files.exists(inbox.resolve("data-?.txt")));
        assertEquals("other", Files.readString(inbox.resolve("data-1.txt")));
        assertEquals("done before", Files.readString(done.resolve("data-2.txt")));
    }

    @Test
    void consumeFileWithWildcardInNameToLocalWorkDirectory() throws Exception {
        Files.writeString(inbox.resolve("data-?.txt"), "wanted");
        Files.writeString(inbox.resolve("data-1.txt"), "other");
        Path work = Files.createTempDirectory(base, "work");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("wanted");
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "data-?.txt");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(sftpUri("inbox") + "&fileName=data-?.txt&noop=true&initialDelay=0&delay=60000"
                     + "&localWorkDirectory=" + work)
                        .convertBodyTo(String.class).to("mock:result");
            }
        });

        mock.assertIsSatisfied();
        assertEquals("other", Files.readString(inbox.resolve("data-1.txt")));
    }

    @Test
    void failedStepwiseRetrieveChangesBackToTheStartingDirectory() throws Exception {
        // the endpoint directory is relative to the home directory of the server, so the consumer changes to it
        // from the directory it started in, at every poll
        Files.writeString(inbox.resolve("data-1.txt"), "other");
        AtomicInteger polls = new AtomicInteger();
        context.getRegistry().bind("countingPollStrategy", new PollingConsumerPollStrategy() {
            @Override
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                polls.incrementAndGet();
                return true;
            }

            @Override
            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
                // noop
            }

            @Override
            public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) {
                return false;
            }
        });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("wanted");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(sftpUri("inbox") + "&useList=false&fileName=data-*.txt&ignoreFileNotFoundOrPermissionError=true"
                     + "&stepwise=true&initialDelay=0&delay=100&pollStrategy=#countingPollStrategy")
                        .convertBodyTo(String.class).to("mock:result");
            }
        });

        // the retrieve of data-*.txt fails at these polls, as there is no file with that name
        await().atMost(10, TimeUnit.SECONDS).until(() -> polls.get() >= 3);
        Files.writeString(inbox.resolve("data-*.txt"), "wanted");

        mock.assertIsSatisfied();
        assertEquals("other", Files.readString(inbox.resolve("data-1.txt")));
    }

    private String sftpUri(String directory) {
        return "sftp://localhost:" + service.getPort() + "/" + base + "/" + directory
               + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile();
    }
}
