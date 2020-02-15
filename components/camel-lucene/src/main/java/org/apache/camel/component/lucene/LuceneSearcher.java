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

import org.apache.camel.processor.lucene.support.Hit;
import org.apache.camel.processor.lucene.support.Hits;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.NIOFSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuceneSearcher {
    private static final Logger LOG = LoggerFactory.getLogger(LuceneSearcher.class);
    private Analyzer analyzer;
    private IndexSearcher indexSearcher; 
    private IndexReader indexReader;
    private ScoreDoc[] hits;

    public void open(File indexDirectory, Analyzer analyzer) throws IOException {
        if (indexDirectory != null) {
            indexReader = DirectoryReader.open(new NIOFSDirectory(indexDirectory.toPath()));
        } else {
            indexReader = DirectoryReader.open(new NIOFSDirectory(new File("./indexDirectory").toPath()));
        }
        indexSearcher = new IndexSearcher(indexReader);
        this.analyzer = analyzer;
    }

    public void close() throws IOException {
        indexReader.close();        
        analyzer.close();
    }
    
    public Hits search(String searchPhrase, int maxNumberOfHits, int totalHitsThreshold) throws Exception {
        return search(searchPhrase, maxNumberOfHits, totalHitsThreshold, false);
    }

    public Hits search(String searchPhrase, int maxNumberOfHits, int totalHitsThreshold, boolean returnLuceneDocs) throws Exception {
        Hits searchHits = new Hits();

        int numberOfHits = doSearch(searchPhrase, maxNumberOfHits, totalHitsThreshold);
        searchHits.setNumberOfHits(numberOfHits);

        for (ScoreDoc hit : hits) {
            Document selectedDocument = indexSearcher.doc(hit.doc);
            Hit aHit = new Hit();
            if (returnLuceneDocs) {
                aHit.setDocument(selectedDocument);
            }
            aHit.setHitLocation(hit.doc);
            aHit.setScore(hit.score);
            aHit.setData(selectedDocument.get("contents"));
            searchHits.getHit().add(aHit);
        }        

        return searchHits;
    }
                
    private int doSearch(String searchPhrase, int maxNumberOfHits, int totalHitsThreshold) throws NullPointerException, ParseException, IOException {
        LOG.trace("*** Search Phrase: {} ***", searchPhrase);

        QueryParser parser = new QueryParser("contents", analyzer);
        Query query = parser.parse(searchPhrase);
        TopScoreDocCollector collector = TopScoreDocCollector.create(maxNumberOfHits, totalHitsThreshold);
        indexSearcher.search(query, collector);
        hits = collector.topDocs().scoreDocs;
        
        LOG.trace("*** Search generated {} hits ***", hits.length);
        return hits.length;
    }
}
