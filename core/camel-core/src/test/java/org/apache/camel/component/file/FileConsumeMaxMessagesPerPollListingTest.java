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
package org.apache.camel.component.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * With maxMessagesPerPoll, the file consumer reads the directory one entry at a time and stops once it has the files of
 * the poll (CAMEL-26635). The listing must pick up the same files as reading the whole directory first.
 */
public class FileConsumeMaxMessagesPerPollListingTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testConsumesEveryFileOverThePolls() throws Exception {
        // the files stay in the directory, so each poll reads further into it
        createFiles(testDirectory(), "file", 30);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(30);
        mock.expectsNoDuplicates(header(Exchange.FILE_NAME));
        mock.allMessages().exchangeProperty(Exchange.BATCH_SIZE).isLessThanOrEqualTo(10);

        consume("noop=true&maxMessagesPerPoll=10");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testConsumesSubDirectories() throws Exception {
        createFiles(testDirectory(), "a", 5);
        createFiles(testDirectory("sub"), "b", 5);
        createFiles(testDirectory("sub/deeper"), "c", 5);
        createFiles(testDirectory("other"), "d", 5);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(20);
        mock.expectsNoDuplicates(header(Exchange.FILE_NAME_ONLY));
        mock.allMessages().exchangeProperty(Exchange.BATCH_SIZE).isLessThanOrEqualTo(3);

        consume("noop=true&recursive=true&maxMessagesPerPoll=3");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSkipsHiddenDirectories() throws Exception {
        createFiles(testDirectory("visible"), "a", 2);
        createFiles(testDirectory(".hidden"), "b", 2);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedHeaderValuesReceivedInAnyOrder(Exchange.FILE_NAME_ONLY, "a-0000.txt", "a-0001.txt");
        mock.setAssertPeriod(200);

        consume("noop=true&recursive=true&maxMessagesPerPoll=10");

        assertMockEndpointsSatisfied();
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "POSIX permissions")
    public void testSkipsUnreadableDirectory() throws Exception {
        createFiles(testDirectory("readable"), "a", 2);
        Path unreadable = createFiles(testDirectory("unreadable"), "b", 2);
        Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("-wx------"));
        try {
            Assumptions.assumeFalse(Files.isReadable(unreadable), "the directory can still be read (root user)");

            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedHeaderValuesReceivedInAnyOrder(Exchange.FILE_NAME_ONLY, "a-0000.txt", "a-0001.txt");
            mock.setAssertPeriod(200);

            // as when the whole directory is listed, a directory that cannot be read has no files
            consume("noop=true&recursive=true&maxMessagesPerPoll=10");

            assertMockEndpointsSatisfied();
        } finally {
            Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    public void testPreSortSortsTheWholeDirectory() throws Exception {
        createFiles(testDirectory(), "file", 30);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedHeaderValuesReceivedInAnyOrder(Exchange.FILE_NAME, "file-0029.txt", "file-0028.txt");

        consume("noop=true&maxMessagesPerPoll=2&preSort=-name&delay=60000");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDoneFileIsLookedUpInTheDirectory() throws Exception {
        createFiles(testDirectory(), "file", 30);
        Files.writeString(testFile("file-0017.txt.done"), "");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedHeaderValuesReceivedInAnyOrder(Exchange.FILE_NAME, "file-0017.txt");
        mock.setAssertPeriod(200);

        consume("noop=true&maxMessagesPerPoll=2&doneFileName=${file:name}.done");

        assertMockEndpointsSatisfied();
    }

    private void consume(String query) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                String delay = query.matches("(.*&)?delay=.*") ? "" : "delay=10&";
                from(fileUri("?initialDelay=0&" + delay + query)).to("mock:result");
            }
        });
        context.start();
    }

    private static Path createFiles(Path directory, String prefix, int count) throws IOException {
        Files.createDirectories(directory);
        for (int i = 0; i < count; i++) {
            Files.writeString(directory.resolve(String.format("%s-%04d.txt", prefix, i)), "x");
        }
        return directory;
    }
}
