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
package org.apache.camel.component.seda;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A message sent InOnly to SEDA from inside a Multicast or Split must keep its spooled stream cache file until the SEDA
 * route is done with it, and must not leave the file behind afterwards.
 */
public class SedaStreamCachingSpoolTest extends ContextTestSupport {

    private static final byte[] DATA = createData(16 * 1024);

    // the seda route waits until the parent exchange is done, so the file would already be deleted if the
    // copy did not hold its own reference to it
    private final CountDownLatch parentDone = new CountDownLatch(1);

    @Test
    public void testMulticastToSeda() throws Exception {
        sendAndAssert("direct:multicast", stream(), 1);
    }

    @Test
    public void testParallelMulticastToSeda() throws Exception {
        sendAndAssert("direct:multicastParallel", stream(), 1);
    }

    @Test
    public void testRecipientListToSeda() throws Exception {
        // the recipient list does not tie the stream caches of its copies to the parent, so this has always worked
        sendAndAssert("direct:recipientList", stream(), 1);
    }

    @Test
    public void testSplitToSeda() throws Exception {
        sendAndAssert("direct:split", List.of(stream(), stream()), 2);
    }

    @Test
    public void testDirectToSeda() throws Exception {
        // without an EIP creating sub exchanges this has always worked
        sendAndAssert("direct:start", stream(), 1);
    }

    private void sendAndAssert(String uri, Object body, int expected) throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(expected);

        template.sendBody(uri, body);
        parentDone.countDown();

        assertMockEndpointsSatisfied();
        for (Exchange exchange : result.getReceivedExchanges()) {
            assertArrayEquals(DATA, exchange.getMessage().getBody(byte[].class));
        }

        // the copy deletes its spool file when the seda route is done
        File spoolDir = testDirectory().toFile();
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            String[] files = spoolDir.list();
            assertNotNull(files);
            assertEquals(0, files.length, "Spool files left behind: " + List.of(files));
        });
    }

    private static InputStream stream() {
        // a stream that is not converted to an in-memory cache, so it is spooled to disk
        return new BufferedInputStream(new ByteArrayInputStream(DATA));
    }

    private static byte[] createData(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) ('a' + i % 26);
        }
        return data;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.getStreamCachingStrategy().setSpoolDirectory(testDirectory().toFile());
                context.getStreamCachingStrategy().setSpoolEnabled(true);
                context.getStreamCachingStrategy().setSpoolThreshold(1024);
                context.getStreamCachingStrategy().setRemoveSpoolDirectoryWhenStopping(false);
                context.setStreamCaching(true);

                from("direct:start").to("seda:b");

                from("direct:multicast").multicast().to("seda:b", "mock:other");

                from("direct:multicastParallel").multicast().parallelProcessing().to("seda:b", "mock:other");

                from("direct:recipientList").recipientList(constant("seda:b,mock:other"));

                // each part is spooled when it enters the part route, which runs within the split
                from("direct:split").split(body()).to("direct:part");
                from("direct:part").to("seda:b");

                from("seda:b")
                        .process(e -> parentDone.await(20, TimeUnit.SECONDS))
                        .convertBodyTo(byte[].class)
                        .to("mock:result");
            }
        };
    }
}
