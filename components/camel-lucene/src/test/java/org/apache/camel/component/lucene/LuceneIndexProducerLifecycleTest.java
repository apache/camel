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
package org.apache.camel.component.lucene;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.NIOFSDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The insert endpoint owns one index directory that all its producers share: stopping a route must not close it, and
 * concurrent exchanges must not fail on the index write lock.
 */
class LuceneIndexProducerLifecycleTest extends CamelTestSupport {

    private static final String INSERT = "lucene:lifecycleIndex:insert?analyzer=#stdAnalyzer&indexDir=#lifecycleDir";

    @TempDir
    File indexDir;

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("lifecycleDir", indexDir);
        registry.bind("stdAnalyzer", new StandardAnalyzer());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:index").routeId("index").to(INSERT);
                from("direct:other").routeId("other").to(INSERT);
            }
        };
    }

    @Test
    void insertWorksAfterRouteRestart() throws Exception {
        Exchange first = template.send("direct:index", e -> e.getIn().setBody("before restart"));
        assertNull(first.getException(), "insert before the restart");

        context.getRouteController().stopRoute("index");
        context.getRouteController().startRoute("index");

        Exchange second = template.send("direct:index", e -> e.getIn().setBody("after restart"));
        assertNull(second.getException(), "an insert after the route was restarted must succeed");
        assertEquals(2, countIndexedMessages());
    }

    @Test
    void stoppingOneRouteDoesNotBreakAnotherRouteOnTheSameEndpoint() throws Exception {
        context.getRouteController().stopRoute("other");

        Exchange exchange = template.send("direct:index", e -> e.getIn().setBody("still running"));
        assertNull(exchange.getException(),
                "stopping route 'other' must not break the insert of route 'index' to the same endpoint");
        assertEquals(1, countIndexedMessages());
    }

    @Test
    void failedInsertDoesNotBlockLaterInserts() throws Exception {
        // no body: the insert fails after the index writer was opened
        Exchange failed = template.send("direct:index", e -> e.getIn().setBody(null));
        assertNotNull(failed.getException(), "an insert without a body fails");

        Exchange next = template.send("direct:index", e -> e.getIn().setBody("next"));
        assertNull(next.getException(), "a failed insert must not leave the index write lock held");
        assertEquals(1, countIndexedMessages());
    }

    @Test
    void failedCommitDoesNotBlockLaterInserts() throws Exception {
        LuceneEndpoint endpoint = context.getEndpoint(INSERT, LuceneEndpoint.class);
        LuceneIndexer indexer = ((LuceneIndexProducer) endpoint.createProducer()).getIndexer();
        AtomicBoolean failSync = new AtomicBoolean(true);
        NIOFSDirectory original = indexer.getNiofsDirectory();
        // the first commit fails when it syncs the index files
        indexer.setNiofsDirectory(new NIOFSDirectory(indexDir.toPath()) {
            @Override
            public void sync(Collection<String> names) throws IOException {
                if (failSync.getAndSet(false)) {
                    throw new IOException("Simulated commit failure");
                }
                super.sync(names);
            }
        });
        original.close();

        Exchange failed = template.send("direct:index", e -> e.getIn().setBody("commit fails"));
        assertNotNull(failed.getException(), "an insert whose commit fails fails");
        assertFalse(failSync.get(), "the commit should have synced the index files");

        Exchange next = template.send("direct:index", e -> e.getIn().setBody("next"));
        assertNull(next.getException(), "a failed commit must not leave the index write lock held");
        assertEquals(1, countIndexedMessages());
    }

    @Test
    void concurrentInsertsAreIndexed() throws Exception {
        CountDownLatch writerOpen = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // a header value whose conversion to String happens while the first insert holds the index writer
        Object slowHeader = new Object() {
            @Override
            public String toString() {
                writerOpen.countDown();
                try {
                    release.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "slow";
            }
        };

        AtomicReference<Exchange> firstResult = new AtomicReference<>();
        AtomicReference<Exchange> secondResult = new AtomicReference<>();
        Thread first = new Thread(() -> firstResult.set(template.send("direct:index", e -> {
            e.getIn().setHeader("slow", slowHeader);
            e.getIn().setBody("first");
        })));
        first.start();
        assertTrue(writerOpen.await(10, TimeUnit.SECONDS), "first insert should hold the index writer");

        Thread second = new Thread(() -> secondResult.set(template.send("direct:other", e -> e.getIn().setBody("second"))));
        second.start();
        // the second insert either completes or waits for the first one
        await().atMost(10, TimeUnit.SECONDS).until(() -> !second.isAlive()
                || second.getState() == Thread.State.BLOCKED || second.getState() == Thread.State.WAITING);

        release.countDown();
        first.join(10000);
        second.join(10000);
        assertFalse(first.isAlive(), "first insert should have completed");
        assertFalse(second.isAlive(), "second insert should have completed");

        assertNull(firstResult.get().getException(), "first insert");
        assertNull(secondResult.get().getException(),
                "an insert running while another insert to the same index is in progress must not fail");
        assertEquals(2, countIndexedMessages());
    }

    private int countIndexedMessages() throws IOException {
        int count = 0;
        try (FSDirectory dir = FSDirectory.open(indexDir.toPath());
             DirectoryReader reader = DirectoryReader.open(dir)) {
            StoredFields storedFields = reader.storedFields();
            for (int i = 0; i < reader.maxDoc(); i++) {
                if (storedFields.document(i).get("contents") != null) {
                    count++;
                }
            }
        }
        return count;
    }
}
