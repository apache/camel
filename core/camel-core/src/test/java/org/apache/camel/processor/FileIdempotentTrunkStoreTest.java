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
package org.apache.camel.processor;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.processor.idempotent.FileIdempotentRepository;
import org.hamcrest.MatcherAssert;
import org.hamcrest.collection.IsIterableContainingInOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileIdempotentTrunkStoreTest extends ContextTestSupport {
    protected Endpoint startEndpoint;
    protected MockEndpoint resultEndpoint;
    private IdempotentRepository repo;
    private File store;

    @Test
    public void testTrunkFileStore() throws Exception {
        resultEndpoint.expectedBodiesReceived("A", "B", "C", "D", "E");

        sendMessage("AAAAAAAAAA", "A");
        sendMessage("BBBBBBBBBB", "B");
        sendMessage("CCCCCCCCCC", "C");
        sendMessage("AAAAAAAAAA", "A");
        sendMessage("DDDDDDDDDD", "D");
        sendMessage("BBBBBBBBBB", "B");
        sendMessage("EEEEEEEEEE", "E");

        resultEndpoint.assertIsSatisfied();

        resultEndpoint.reset();
        resultEndpoint.expectedBodiesReceived("Z", "X");

        // should trunk the file store
        sendMessage("ZZZZZZZZZZ", "Z");
        sendMessage("XXXXXXXXXX", "X");

        resultEndpoint.assertIsSatisfied();

        assertTrue(repo.contains("XXXXXXXXXX"));

        // check the file should only have the last 2 entries as it was trunked
        try (Stream<String> fileContent = Files.lines(store.toPath())) {
            List<String> fileEntries = fileContent.toList();
            // expected order
            MatcherAssert.assertThat(fileEntries, IsIterableContainingInOrder.contains("ZZZZZZZZZZ", "XXXXXXXXXX"));
        }
    }

    protected void sendMessage(final Object messageId, final Object body) {
        template.send(startEndpoint, new Processor() {
            public void process(Exchange exchange) {
                // now lets fire in a message
                Message in = exchange.getIn();
                in.setBody(body);
                in.setHeader("messageId", messageId);
            }
        });
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        store = testFile("idempotentfilestore.dat").toFile();
        // delete file store before testing
        if (store.exists()) {
            store.delete();
        }

        // 5 elements in cache, and 50 bytes as max size limit for when trunking
        // should start
        repo = FileIdempotentRepository.fileIdempotentRepository(store, 5, 50);
        repo.start();

        super.setUp();
        startEndpoint = resolveMandatoryEndpoint("direct:start");
        resultEndpoint = getMockEndpoint("mock:result");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").idempotentConsumer(header("messageId"), repo).to("mock:result");
            }
        };
    }
}
