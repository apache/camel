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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.SynchronizationAdapter;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the SEDA producer does not add the copy of an InOnly exchange to the queue (discardWhenFull, offerTimeout, or a
 * full queue), the on completions of the exchange (such as the commit or rollback of the consumer) must still run.
 */
public class SedaQueueFullOnCompletionTest extends ContextTestSupport {

    private static final byte[] DATA = new byte[16 * 1024];

    private final List<String> events = new CopyOnWriteArrayList<>();

    @Test
    public void testDiscardWhenFull() {
        template.sendBody("direct:discard", "A");
        // the queue is full, so this one is discarded, which completes the exchange
        template.sendBody("direct:discard", "B");

        assertEquals(List.of("complete:B"), events);
    }

    @Test
    public void testOfferTimeout() {
        template.sendBody("direct:offer", "A");
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:offer", "B"));
        assertInstanceOf(IllegalStateException.class, e.getCause());

        assertEquals(List.of("failure:B"), events);
    }

    @Test
    public void testQueueFull() {
        template.sendBody("direct:add", "A");
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:add", "B"));
        assertInstanceOf(IllegalStateException.class, e.getCause());

        assertEquals(List.of("failure:B"), events);
    }

    @Test
    public void testDiscardWhenFullFileConsumer() throws Exception {
        Path in = testDirectory("in", true);
        NotifyBuilder notify = new NotifyBuilder(context).fromRoute("files").whenDone(2).create();
        Files.writeString(in.resolve("a.txt"), "A");
        Files.writeString(in.resolve("b.txt"), "B");
        context.getRouteController().startRoute("files");
        assertTrue(notify.matches(10, TimeUnit.SECONDS));

        // a.txt is queued, and b.txt is discarded, which commits it (moves it to .camel)
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(Files.exists(in.resolve(".camel/b.txt"))));
        assertTrue(Files.exists(in.resolve("a.txt")));

        MockEndpoint mock = getMockEndpoint("mock:files");
        mock.expectedBodiesReceived("A");
        context.getRouteController().startRoute("filesQueue");
        assertMockEndpointsSatisfied();
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(Files.exists(in.resolve(".camel/a.txt"))));
    }

    @Test
    public void testDiscardWhenFullStreamCache() throws Exception {
        template.sendBody("direct:spool", new BufferedInputStream(new ByteArrayInputStream(DATA)));
        // discarded: the stream cache of its copy is released as well
        template.sendBody("direct:spool", new BufferedInputStream(new ByteArrayInputStream(DATA)));

        MockEndpoint mock = getMockEndpoint("mock:spool");
        mock.expectedMessageCount(1);
        context.getRouteController().startRoute("spoolQueue");
        assertMockEndpointsSatisfied();
        assertArrayEquals(DATA, mock.getReceivedExchanges().get(0).getMessage().getBody(byte[].class));

        File spoolDir = testDirectory("spool").toFile();
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            String[] files = spoolDir.list();
            assertNotNull(files);
            assertEquals(0, files.length, "Spool files left behind: " + List.of(files));
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.getStreamCachingStrategy().setSpoolDirectory(testDirectory("spool").toFile());
                context.getStreamCachingStrategy().setSpoolEnabled(true);
                context.getStreamCachingStrategy().setSpoolThreshold(1024);
                context.getStreamCachingStrategy().setRemoveSpoolDirectoryWhenStopping(false);
                context.setStreamCaching(true);

                Processor record = e -> e.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                    @Override
                    public void onComplete(Exchange exchange) {
                        events.add("complete:" + exchange.getMessage().getBody(String.class));
                    }

                    @Override
                    public void onFailure(Exchange exchange) {
                        events.add("failure:" + exchange.getMessage().getBody(String.class));
                    }
                });

                // the queues have no consumers, so the first message fills them
                from("direct:discard").process(record).to("seda:discard?size=1&discardWhenFull=true");
                from("direct:offer").errorHandler(noErrorHandler())
                        .process(record).to("seda:offer?size=1&blockWhenFull=true&offerTimeout=10");
                from("direct:add").errorHandler(noErrorHandler())
                        .process(record).to("seda:add?size=1");

                from(fileUri("in?initialDelay=0&delay=10&sortBy=file:name")).routeId("files").autoStartup(false)
                        .to("seda:files?size=1&discardWhenFull=true");
                from("seda:files?size=1&discardWhenFull=true").routeId("filesQueue").autoStartup(false)
                        .convertBodyTo(String.class).to("mock:files");

                from("direct:spool").to("seda:spool?size=1&discardWhenFull=true");
                from("seda:spool?size=1&discardWhenFull=true").routeId("spoolQueue").autoStartup(false)
                        .convertBodyTo(byte[].class).to("mock:spool");
            }
        };
    }
}
