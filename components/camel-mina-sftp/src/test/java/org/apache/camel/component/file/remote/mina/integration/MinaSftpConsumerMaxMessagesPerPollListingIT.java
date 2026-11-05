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

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.ftp.services.embedded.ListingCountingSftpEmbeddedService;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.apache.camel.builder.Builder.header;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With maxMessagesPerPoll, the mina-sftp consumer stops reading the directory once it has the files of the poll, rather
 * than listing the whole directory on every poll (CAMEL-26635).
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
class MinaSftpConsumerMaxMessagesPerPollListingIT extends CamelTestSupport {

    // a reply of the server to a directory listing request holds about 150 of these entries
    private static final int FILES = 1000;

    @RegisterExtension
    ListingCountingSftpEmbeddedService service = new ListingCountingSftpEmbeddedService();

    private Path base;

    @BeforeEach
    void createTestDirectory() throws IOException {
        // the root directory of the server is shared by the tests of this class and kept between runs
        base = Files.createTempDirectory(Files.createDirectories(service.getFtpRootDir()), "test");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void stopsListingAtLimit(boolean stepwise) throws Exception {
        createFiles(base.resolve("inbox"), "file", FILES);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(10);
        mock.message(0).exchangeProperty(Exchange.BATCH_SIZE).isEqualTo(10);

        consumeOnePoll("inbox", "&maxMessagesPerPoll=10&stepwise=" + stepwise);

        mock.assertIsSatisfied();
        // not the 1,002 entries of the directory
        assertTrue(service.entriesListed() < FILES, "entries listed: " + service.entriesListed());
        assertEquals(service.directoriesOpened(), service.directoriesClosed(), "directories left open");
    }

    @Test
    void listsWholeDirectoryWithPreSort() throws Exception {
        createFiles(base.resolve("inbox"), "file", FILES);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(10);
        // the last files of the listing, as the whole directory is sorted before the limit
        mock.message(0).header(Exchange.FILE_NAME).isEqualTo("file-0999.txt");

        consumeOnePoll("inbox", "&maxMessagesPerPoll=10&preSort=-name&stepwise=false");

        mock.assertIsSatisfied();
        assertEquals(FILES + 2, service.entriesListed());
    }

    @Test
    void listsWholeDirectoryWhenNotEager() throws Exception {
        createFiles(base.resolve("inbox"), "file", FILES);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(10);

        consumeOnePoll("inbox", "&maxMessagesPerPoll=10&eagerMaxMessagesPerPoll=false&stepwise=false");

        mock.assertIsSatisfied();
        assertEquals(FILES + 2, service.entriesListed());
    }

    @Test
    void listsWholeDirectoryWithDoneFile() throws Exception {
        Path inbox = base.resolve("inbox");
        createFiles(inbox, "file", FILES);
        Files.writeString(inbox.resolve("file-0999.txt.done"), "");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "file-0999.txt");

        consumeOnePoll("inbox", "&maxMessagesPerPoll=10&doneFileName=${file:name}.done&stepwise=false");

        mock.assertIsSatisfied();
        assertEquals(FILES + 3, service.entriesListed());
    }

    @Test
    void consumesEveryFileOverThePolls() throws Exception {
        // the files stay in the directory, so each poll reads further into it
        createFiles(base.resolve("inbox"), "file", 300);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(300);
        mock.expectsNoDuplicates(header(Exchange.FILE_NAME));
        mock.allMessages().exchangeProperty(Exchange.BATCH_SIZE).isLessThanOrEqualTo(100);

        consume("inbox", "&maxMessagesPerPoll=100&stepwise=false");

        mock.assertIsSatisfied();
        assertEquals(service.directoriesOpened(), service.directoriesClosed(), "directories left open");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void consumesSubDirectories(boolean stepwise) throws Exception {
        Path inbox = base.resolve("inbox");
        createFiles(inbox, "a", 5);
        createFiles(inbox.resolve("sub"), "b", 5);
        createFiles(inbox.resolve("sub").resolve("deeper"), "c", 5);
        createFiles(inbox.resolve("other"), "d", 5);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(20);
        mock.expectsNoDuplicates(header(Exchange.FILE_NAME));
        mock.allMessages().exchangeProperty(Exchange.BATCH_SIZE).isLessThanOrEqualTo(3);

        consume("inbox", "&maxMessagesPerPoll=3&recursive=true&stepwise=" + stepwise);

        mock.assertIsSatisfied();
    }

    @Test
    void resolvesSymbolicLinks() throws Exception {
        Path inbox = Files.createDirectories(base.resolve("inbox"));
        Path archive = Files.createDirectories(base.resolve("archive"));
        Files.writeString(inbox.resolve("hello.txt"), "Hello World");
        Files.writeString(archive.resolve("report.txt"), "Archived");
        // resolved on the server while the directory is read
        createSymbolicLink(inbox.resolve("latest.txt"), Path.of("hello.txt"));
        createSymbolicLink(inbox.resolve("report.txt"), Path.of("..", "archive", "report.txt"));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World", "Hello World");
        mock.expectedHeaderValuesReceivedInAnyOrder(Exchange.FILE_NAME, "hello.txt", "latest.txt");
        // the link outside the starting directory is skipped
        mock.message(0).exchangeProperty(Exchange.BATCH_SIZE).isEqualTo(2);

        consumeWithBodies("inbox", "&maxMessagesPerPoll=10&stepwise=false");

        mock.assertIsSatisfied();
    }

    private void consume(String directory, String query) throws Exception {
        // the tests only need the names: downloading hundreds of files takes too long on some machines
        consume(directory, 100, "&download=false" + query);
    }

    private void consumeOnePoll(String directory, String query) throws Exception {
        consume(directory, 60000, "&download=false" + query);
    }

    private void consumeWithBodies(String directory, String query) throws Exception {
        consume(directory, 100, query);
    }

    private void consume(String directory, long delay, String query) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("mina-sftp://localhost:" + service.getPort() + "/" + base.resolve(directory)
                     + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false"
                     + "&initialDelay=0&delay=" + delay + "&noop=true" + query)
                        .to("mock:result");
            }
        });
    }

    private static void createFiles(Path directory, String prefix, int count) throws IOException {
        Files.createDirectories(directory);
        for (int i = 0; i < count; i++) {
            Files.writeString(directory.resolve(String.format("%s-%04d.txt", prefix, i)), "x");
        }
    }

    private static void createSymbolicLink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (FileAlreadyExistsException e) {
            throw new IllegalStateException(e);
        } catch (UnsupportedOperationException | FileSystemException e) {
            // such as Windows when the user may not create symbolic links
            Assumptions.abort("Symbolic links cannot be created on this platform: " + e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
