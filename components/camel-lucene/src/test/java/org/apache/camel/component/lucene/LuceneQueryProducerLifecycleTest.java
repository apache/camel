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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.lucene.support.Hit;
import org.apache.camel.processor.lucene.support.Hits;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The query producer must keep working after its route is restarted, must stop cleanly when it never queried, and must
 * answer each exchange with the hits of its own query.
 */
class LuceneQueryProducerLifecycleTest extends CamelTestSupport {

    @TempDir
    static File indexDir;

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("queryDir", indexDir);
        registry.bind("queryAnalyzer", new StandardAnalyzer());
    }

    @BeforeEach
    void createIndex() throws Exception {
        try (FSDirectory dir = FSDirectory.open(indexDir.toPath());
             IndexWriter writer = new IndexWriter(
                     dir, new IndexWriterConfig(new StandardAnalyzer()).setOpenMode(IndexWriterConfig.OpenMode.CREATE))) {
            for (int i = 0; i < 20; i++) {
                addDoc(writer, "alpha document " + i);
                addDoc(writer, "beta document " + i);
            }
            writer.commit();
        }
    }

    private static void addDoc(IndexWriter writer, String contents) throws Exception {
        Document doc = new Document();
        doc.add(new TextField("contents", contents, Field.Store.YES));
        writer.addDocument(doc);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:query").routeId("query")
                        .to("lucene:queryIndex:query?analyzer=#queryAnalyzer&indexDir=#queryDir&maxHits=20");
            }
        };
    }

    private Exchange query(String phrase) {
        return template.send("direct:query", e -> e.getIn().setHeader(LuceneConstants.HEADER_QUERY, phrase));
    }

    @Test
    void queryWorksAfterRouteRestart() throws Exception {
        Exchange before = query("alpha");
        assertNull(before.getException(), "query before the restart");
        assertEquals(20, before.getMessage().getBody(Hits.class).getNumberOfHits());

        context.getRouteController().stopRoute("query");
        context.getRouteController().startRoute("query");

        Exchange after = query("alpha");
        assertNull(after.getException(), "a query after the route was restarted must succeed");
        assertEquals(20, after.getMessage().getBody(Hits.class).getNumberOfHits());
    }

    /**
     * The producer is shared by concurrent exchanges. Before the fix, its searcher kept the reader, the searcher and
     * the hits of the last query in fields, so an exchange could get the hits of another exchange's query (a few dozen
     * of 20000 queries on a laptop). This test cannot force that interleaving, it only makes it very likely.
     */
    @Test
    void concurrentQueriesGetTheirOwnHits() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 20000; i++) {
                String term = i % 2 == 0 ? "alpha" : "beta";
                Callable<String> task = () -> {
                    Exchange exchange = query(term);
                    if (exchange.getException() != null) {
                        return term + ": " + exchange.getException();
                    }
                    for (Hit hit : exchange.getMessage().getBody(Hits.class).getHit()) {
                        if (!hit.getData().startsWith(term)) {
                            return term + ": got hit '" + hit.getData() + "'";
                        }
                    }
                    return null;
                };
                results.add(pool.submit(task));
            }
            List<String> wrong = new ArrayList<>();
            for (Future<String> result : results) {
                String error = result.get(30, TimeUnit.SECONDS);
                if (error != null) {
                    wrong.add(error);
                }
            }
            assertTrue(wrong.isEmpty(), "each query must get the hits of its own query, but " + wrong.size()
                                        + " of 20000 did not, e.g. " + wrong.stream().limit(3).toList());
        } finally {
            pool.shutdownNow();
        }
    }
}
