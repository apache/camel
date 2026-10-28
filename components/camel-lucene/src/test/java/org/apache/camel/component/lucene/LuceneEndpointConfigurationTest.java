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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Each lucene endpoint keeps the options of its own URI, also when the CamelContext has several lucene endpoints.
 */
class LuceneEndpointConfigurationTest extends CamelTestSupport {

    private static final String QUERY_A = "lucene:indexA:query?analyzer=#stdAnalyzer&indexDir=#dirA&maxHits=5";
    private static final String QUERY_B = "lucene:indexB:query?analyzer=#stdAnalyzer&indexDir=#dirB&maxHits=20";

    @TempDir
    static File dirA;
    @TempDir
    static File dirB;

    @BeforeAll
    static void createIndexes() throws Exception {
        createIndex(dirA, "alpha");
        createIndex(dirB, "beta");
    }

    private static void createIndex(File indexDir, String term) throws Exception {
        try (FSDirectory dir = FSDirectory.open(indexDir.toPath());
             IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
            for (int i = 0; i < 10; i++) {
                Document doc = new Document();
                doc.add(new TextField("contents", term + " document " + i, Field.Store.YES));
                writer.addDocument(doc);
            }
            writer.commit();
        }
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("dirA", dirA);
        registry.bind("dirB", dirB);
        registry.bind("stdAnalyzer", new StandardAnalyzer());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").to(QUERY_A);
                from("direct:b").to(QUERY_B);
            }
        };
    }

    @Test
    void endpointsKeepTheirOwnOptions() {
        LuceneEndpoint a = context.getEndpoint(QUERY_A, LuceneEndpoint.class);
        LuceneEndpoint b = context.getEndpoint(QUERY_B, LuceneEndpoint.class);
        assertEquals(dirA, a.getConfig().getIndexDir(), "indexDir of " + QUERY_A);
        assertEquals(5, a.getConfig().getMaxHits(), "maxHits of " + QUERY_A);
        assertEquals("indexA", a.getConfig().getHost(), "host of " + QUERY_A);
        assertEquals(dirB, b.getConfig().getIndexDir(), "indexDir of " + QUERY_B);
        assertEquals(20, b.getConfig().getMaxHits(), "maxHits of " + QUERY_B);
        assertEquals("indexB", b.getConfig().getHost(), "host of " + QUERY_B);
    }

    @Test
    void eachQueryEndpointSearchesItsOwnIndex() {
        Exchange a = template.send("direct:a", e -> e.getIn().setHeader(LuceneConstants.HEADER_QUERY, "alpha"));
        assertNull(a.getException());
        assertEquals(5, a.getMessage().getBody(Hits.class).getNumberOfHits(),
                QUERY_A + " must search its own index (10 alpha documents) with its own maxHits (5)");

        Exchange b = template.send("direct:b", e -> e.getIn().setHeader(LuceneConstants.HEADER_QUERY, "beta"));
        assertNull(b.getException());
        assertEquals(10, b.getMessage().getBody(Hits.class).getNumberOfHits(),
                QUERY_B + " must search its own index (10 beta documents)");
    }
}
