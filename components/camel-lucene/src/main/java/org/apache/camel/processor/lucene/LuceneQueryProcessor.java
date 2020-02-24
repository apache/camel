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
package org.apache.camel.processor.lucene;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.lucene.LuceneSearcher;
import org.apache.camel.processor.lucene.support.Hits;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.IndexSearcher;

public class LuceneQueryProcessor implements Processor {
    private File indexDirectory;
    private Analyzer analyzer;
    private IndexSearcher indexSearcher;
    private LuceneSearcher searcher;
    private String searchPhrase;
    private int maxNumberOfHits;
    private int totalHitsThreshold;

    public LuceneQueryProcessor(String indexDirectoryPath, Analyzer analyzer, String defaultSearchPhrase, int maxNumberOfHits, int totalHitsThreshold) {
        this.setAnalyzer(analyzer);
        this.setIndexDirectory(new File(indexDirectoryPath));
        this.setSearchPhrase(defaultSearchPhrase);
        this.setMaxNumberOfHits(maxNumberOfHits);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Hits hits;

        String phrase = exchange.getIn().getHeader("QUERY", String.class);
        String returnLuceneDocs = exchange.getIn().getHeader("RETURN_LUCENE_DOCS", String.class);
        boolean isReturnLuceneDocs = returnLuceneDocs != null && returnLuceneDocs.equalsIgnoreCase("true");

        if (phrase != null) {
            searcher = new LuceneSearcher();
            searcher.open(indexDirectory, analyzer);
            hits = searcher.search(phrase, maxNumberOfHits, totalHitsThreshold, isReturnLuceneDocs);
        } else {
            throw new IllegalArgumentException("SearchPhrase for LuceneQueryProcessor not set. Set the Header value: QUERY");
        }

        exchange.getIn().setBody(hits);
    }

    public Analyzer getAnalyzer() {
        return analyzer;
    }

    public void setAnalyzer(Analyzer analyzer) {
        this.analyzer = analyzer;
    }

    public IndexSearcher getIndexSearcher() {
        return indexSearcher;
    }

    public void setIndexSearcher(IndexSearcher indexSearcher) {
        this.indexSearcher = indexSearcher;
    }

    public File getIndexDirectory() {
        return indexDirectory;
    }

    public void setIndexDirectory(File indexDirectory) {
        this.indexDirectory = indexDirectory;
    }

    public String getSearchPhrase() {
        return searchPhrase;
    }

    public void setSearchPhrase(String searchPhrase) {
        this.searchPhrase = searchPhrase;
    }

    public int getMaxNumberOfHits() {
        return maxNumberOfHits;
    }

    public void setMaxNumberOfHits(int maxNumberOfHits) {
        this.maxNumberOfHits = maxNumberOfHits;
    }

    public int getTotalHitsThreshold() {
        return totalHitsThreshold;
    }

    public void setTotalHitsThreshold(int totalHitsThreshold) {
        this.totalHitsThreshold = totalHitsThreshold;
    }
}
