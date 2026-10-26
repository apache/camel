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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.lucene.support.Hit;
import org.apache.camel.processor.lucene.support.Hits;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What the insert endpoint writes must be found by the query endpoint, and the fields of one exchange (or one source
 * file) must be one document.
 */
class LuceneInsertAndQueryTest extends CamelTestSupport {

    @TempDir
    File indexDir;

    @TempDir
    File srcIndexDir;

    @TempDir
    File srcDir;

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        Files.writeString(new File(srcDir, "quote.txt").toPath(), "A day without sunshine is like night",
                StandardCharsets.UTF_8);
        registry.bind("indexDir", indexDir);
        registry.bind("srcIndexDir", srcIndexDir);
        registry.bind("srcDir", srcDir);
        registry.bind("stdAnalyzer", new StandardAnalyzer());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:insert").to("lucene:insertIndex:insert?analyzer=#stdAnalyzer&indexDir=#indexDir");
                from("direct:query").to("lucene:queryIndex:query?analyzer=#stdAnalyzer&indexDir=#indexDir&maxHits=20");
                from("direct:insertWithSrcDir")
                        .to("lucene:srcIndex:insert?analyzer=#stdAnalyzer&indexDir=#srcIndexDir&srcDir=#srcDir");
                from("direct:querySrcDir")
                        .to("lucene:srcQuery:query?analyzer=#stdAnalyzer&indexDir=#srcIndexDir&maxHits=20");
            }
        };
    }

    private Hits query(String endpoint, String phrase) {
        Exchange exchange = template.send(endpoint, e -> {
            e.getIn().setHeader(LuceneConstants.HEADER_QUERY, phrase);
            e.getIn().setHeader(LuceneConstants.HEADER_RETURN_LUCENE_DOCS, "true");
        });
        assertNull(exchange.getException(), "query " + phrase);
        return exchange.getMessage().getBody(Hits.class);
    }

    @Test
    void insertedBodyIsFoundByQuery() {
        Exchange inserted = template.send("direct:insert", e -> e.getIn().setBody("the quick brown fox"));
        assertNull(inserted.getException(), "insert");

        Hits hits = query("direct:query", "fox");
        assertEquals(1, hits.getNumberOfHits(), "a word of the inserted body must be found");
        assertEquals("the quick brown fox", hits.getHit().get(0).getData());
    }

    @Test
    void headersAndBodyOfOneExchangeAreOneDocument() throws Exception {
        Exchange inserted = template.send("direct:insert", e -> {
            e.getIn().setHeader("author", "carlin");
            e.getIn().setBody("I think, therefore I am");
        });
        assertNull(inserted.getException(), "insert");

        try (FSDirectory dir = FSDirectory.open(indexDir.toPath());
             DirectoryReader reader = DirectoryReader.open(dir)) {
            assertEquals(1, reader.numDocs(), "one exchange must be one document");
        }

        Hits hits = query("direct:query", "author:carlin");
        assertEquals(1, hits.getNumberOfHits(), "a header of the exchange must be found");
        Hit hit = hits.getHit().get(0);
        assertEquals("I think, therefore I am", hit.getData(), "a hit on a header must carry the body of its exchange");
        Document document = hit.getDocument();
        assertEquals("carlin", document.get("author"));
        assertEquals(inserted.getExchangeId(), document.get("exchangeId"));
    }

    @Test
    void headersNamedLikeTheExchangeFieldsDoNotReplaceThem() {
        Exchange inserted = template.send("direct:insert", e -> {
            e.getIn().setHeader("contents", "header value");
            e.getIn().setHeader("exchangeId", "header id");
            e.getIn().setBody("the real body");
        });
        assertNull(inserted.getException(), "insert");

        Hits hits = query("direct:query", "real");
        assertEquals(1, hits.getNumberOfHits());
        Hit hit = hits.getHit().get(0);
        assertEquals("the real body", hit.getData(), "a header named contents must not replace the body");
        assertEquals(inserted.getExchangeId(), hit.getDocument().get("exchangeId"),
                "a header named exchangeId must not replace the exchange id");
    }

    @Test
    void sourceFileIsFoundByQueryWithItsPath() throws Exception {
        // the source directory is indexed when the insert endpoint is created
        Exchange inserted = template.send("direct:insertWithSrcDir", e -> e.getIn().setBody("unrelated"));
        assertNull(inserted.getException(), "insert");

        Hits hits = query("direct:querySrcDir", "sunshine");
        assertEquals(1, hits.getNumberOfHits(), "a word of a source file must be found");
        Document document = hits.getHit().get(0).getDocument();
        assertNotNull(document, "the hit must carry its document");
        String path = new File(srcDir, "quote.txt").getPath();
        assertEquals(path, document.get("path"), "the path must be in the document of the file contents");

        try (FSDirectory dir = FSDirectory.open(srcIndexDir.toPath());
             DirectoryReader reader = DirectoryReader.open(dir)) {
            // the path is not tokenized: the whole path is one term
            int pathHits = new IndexSearcher(reader).count(new TermQuery(new Term("path", path)));
            assertEquals(1, pathHits, "the path must be indexed as one term");
        }
    }
}
