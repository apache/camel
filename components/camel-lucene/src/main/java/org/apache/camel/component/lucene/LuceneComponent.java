/**
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
import java.net.URI;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.lucene.analysis.Analyzer;

public class LuceneComponent extends UriEndpointComponent {
    private LuceneConfiguration config;

    public LuceneComponent() {
        super(LuceneEndpoint.class);
        config = new LuceneConfiguration();
    }

    public LuceneComponent(CamelContext context) {
        super(context, LuceneEndpoint.class);
        config = new LuceneConfiguration();
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
            throws Exception {
        config.parseURI(new URI(uri), parameters, this);
        LuceneEndpoint luceneEndpoint = new LuceneEndpoint(uri, this, config);
        setProperties(luceneEndpoint.getConfig(), parameters);
        return luceneEndpoint;
    }

    public LuceneConfiguration getConfig() {
        return config;
    }

    /**
     * To use a shared lucene configuration. Properties of the shared configuration can also be set individually.
     */
    public void setConfig(LuceneConfiguration config) {
        this.config = config;
    }

    public String getHost() {
        return config.getHost();
    }

    /**
     * The URL to the lucene server
     * @param host
     */
    public void setHost(String host) {
        config.setHost(host);
    }

    public LuceneOperation getOperation() {
        return config.getOperation();
    }

    /**
     * Operation to do such as insert or query.
     * @param operation
     */
    public void setOperation(LuceneOperation operation) {
        config.setOperation(operation);
    }

    public File getSourceDirectory() {
        return config.getSourceDirectory();
    }

    /**
     * An optional directory containing files to be used to be analyzed and added to the index at producer startup.
     * @param sourceDirectory
     */
    public void setSourceDirectory(File sourceDirectory) {
        config.setSourceDirectory(sourceDirectory);
    }

    public File getIndexDirectory() {
        return config.getIndexDirectory();
    }

    /**
     * A file system directory in which index files are created upon analysis of the document by the specified analyzer
     * @param indexDirectory
     */
    public void setIndexDirectory(File indexDirectory) {
        config.setIndexDirectory(indexDirectory);
    }

    public Analyzer getAnalyzer() {
        return config.getAnalyzer();
    }

    /**
     * An Analyzer builds TokenStreams, which analyze text. It thus represents a policy for extracting index terms from text.
     * The value for analyzer can be any class that extends the abstract class org.apache.lucene.analysis.Analyzer.
     * Lucene also offers a rich set of analyzers out of the box
     * @param analyzer
     */
    public void setAnalyzer(Analyzer analyzer) {
        config.setAnalyzer(analyzer);
    }

    public int getMaxHits() {
        return config.getMaxHits();
    }

    /**
     * An integer value that limits the result set of the search operation
     * @param maxHits
     */
    public void setMaxHits(int maxHits) {
        config.setMaxHits(maxHits);
    }

}
