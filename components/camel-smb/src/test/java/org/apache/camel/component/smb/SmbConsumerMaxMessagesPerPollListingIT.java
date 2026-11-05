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
package org.apache.camel.component.smb;

import java.util.UUID;

import com.hierynomus.smbj.SmbConfig;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.builder.Builder.header;

/**
 * With maxMessagesPerPoll, the smb consumer reads the directory one entry at a time and stops once it has the files of
 * the poll (CAMEL-26635), unless smbj can serve the listing from its directory lease cache. Either way, the polls pick
 * up every file.
 */
public class SmbConsumerMaxMessagesPerPollListingIT extends SmbServerTestSupport {

    @BindToRegistry("noDirectoryLeases")
    private final SmbConfig noDirectoryLeases = SmbConfig.builder().withDirectoryLeasingEnabled(false).build();

    private final String directory = "listing-" + UUID.randomUUID();

    @Test
    public void testConsumesEveryFileOverThePollsWithoutDirectoryLeases() throws Exception {
        // the files stay in the directory, so each poll reads further into it
        createFiles("", "file", "txt", 30);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(30);
        mock.expectsNoDuplicates(header(Exchange.FILE_NAME));
        mock.allMessages().exchangeProperty(Exchange.BATCH_SIZE).isLessThanOrEqualTo(10);

        consume("maxMessagesPerPoll=10&smbConfig=#noDirectoryLeases");

        mock.assertIsSatisfied();
    }

    @Test
    public void testConsumesSubDirectoriesWithoutDirectoryLeases() throws Exception {
        createFiles("", "a", "txt", 5);
        createFiles("sub/", "b", "txt", 5);
        createFiles("sub/deeper/", "c", "txt", 5);
        createFiles("other/", "d", "txt", 5);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(20);
        mock.expectsNoDuplicates(header(Exchange.FILE_NAME));
        mock.allMessages().exchangeProperty(Exchange.BATCH_SIZE).isLessThanOrEqualTo(3);

        consume("maxMessagesPerPoll=3&recursive=true&smbConfig=#noDirectoryLeases");

        mock.assertIsSatisfied();
    }

    @Test
    public void testConsumesEveryMatchingFileWithSearchPattern() throws Exception {
        // a listing with a search pattern is not served from the directory lease cache of smbj
        createFiles("", "file", "txt", 20);
        createFiles("", "other", "dat", 5);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(20);
        mock.expectsNoDuplicates(header(Exchange.FILE_NAME));
        mock.allMessages().header(Exchange.FILE_NAME).endsWith(".txt");
        mock.allMessages().exchangeProperty(Exchange.BATCH_SIZE).isLessThanOrEqualTo(5);

        consume("maxMessagesPerPoll=5&searchPattern=*.txt");

        mock.assertIsSatisfied();
    }

    @Test
    public void testConsumesEveryFileOverThePollsWithDirectoryLeases() throws Exception {
        createFiles("", "file", "txt", 30);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(30);
        mock.expectsNoDuplicates(header(Exchange.FILE_NAME));
        mock.allMessages().exchangeProperty(Exchange.BATCH_SIZE).isLessThanOrEqualTo(10);

        consume("maxMessagesPerPoll=10");

        mock.assertIsSatisfied();
    }

    private void createFiles(String subDirectory, String prefix, String extension, int count) {
        String uri = String.format("smb:%s/%s/%s?username=%s&password=%s",
                service.address(), service.shareName(), directory, service.userName(), service.password());
        for (int i = 0; i < count; i++) {
            sendFile(uri, "x", String.format("%s%s-%04d.%s", subDirectory, prefix, i, extension));
        }
    }

    private void consume(String query) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(String.format("smb:%s/%s/%s?username=%s&password=%s&initialDelay=0&delay=100&noop=true&%s",
                        service.address(), service.shareName(), directory, service.userName(), service.password(), query))
                        .to("mock:result");
            }
        });
    }
}
