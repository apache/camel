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
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.util.Version;

@UriParams
public class LuceneConfiguration {
    private URI uri;
    private String authority;
    private Version luceneVersion = LuceneConstants.LUCENE_VERSION;

    @UriPath @Metadata(required = "true")
    private String host;
    @UriPath @Metadata(required = "true")
    private LuceneOperation operation;
    @UriParam(name = "srcDir")
    private File sourceDirectory;
    @UriParam(name = "indexDir")
    private File indexDirectory;
    @UriParam
    private Analyzer analyzer;
    @UriParam
    private int maxHits;

    public LuceneConfiguration() {
    }

    public LuceneConfiguration(URI uri) throws Exception {
        this.uri = uri;
    }

    public void parseURI(URI uri, Map<String, Object> parameters, LuceneComponent component) throws Exception {
        String protocol = uri.getScheme();
        
        if (!protocol.equalsIgnoreCase("lucene")) {
            throw new IllegalArgumentException("Unrecognized Lucene protocol: " + protocol + " for uri: " + uri);
        }
        setUri(uri);
        setAuthority(uri.getAuthority());
        if (!isValidAuthority()) {
            throw new URISyntaxException(uri.toASCIIString(), 
                    "Incorrect URI syntax and/or Operation specified for the Lucene endpoint."
                    + "Please specify the syntax as \"lucene:[Endpoint Name]:[Operation]?[Query]\""); 
        }
        setHost(retrieveTokenFromAuthority("hostname"));

        String op = retrieveTokenFromAuthority("operation");
        if (op != null) {
            op = op.toLowerCase();
        }
        setOperation(LuceneOperation.valueOf(op));

        sourceDirectory = component.resolveAndRemoveReferenceParameter(
                parameters, "srcDir", File.class, null);
        indexDirectory = component.resolveAndRemoveReferenceParameter(
                parameters, "indexDir", File.class, new File("file:///./indexDirectory"));
        analyzer = component.resolveAndRemoveReferenceParameter(
                parameters, "analyzer", Analyzer.class, new StandardAnalyzer());

        setMaxHits(component.getAndRemoveParameter(parameters, "maxHits", Integer.class, 10));
    }
    
    private boolean isValidAuthority() throws URISyntaxException {
        if ((!authority.contains(":")) 
            || ((authority.split(":")[0]) == null)  
            || ((!authority.split(":")[1].equalsIgnoreCase("insert")) && (!authority.split(":")[1].equalsIgnoreCase("query")))) {
            return false;
        }
        return true;
        
    }
    
    private String retrieveTokenFromAuthority(String token) throws URISyntaxException {
        String retval;
        
        if (token.equalsIgnoreCase("hostname")) {
            retval = uri.getAuthority().split(":")[0];
        } else {
            retval = uri.getAuthority().split(":")[1];
        }
        return retval;
    }

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    public String getHost() {
        return host;
    }

    /**
     * The URL to the lucene server
     */
    public void setHost(String host) {
        this.host = host;
    }

    public LuceneOperation getOperation() {
        return operation;
    }

    /**
     * Operation to do such as insert or query.
     */
    public void setOperation(LuceneOperation operation) {
        this.operation = operation;
    }

    public String getAuthority() {
        return authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public File getSourceDirectory() {
        return sourceDirectory;
    }

    /**
     * An optional directory containing files to be used to be analyzed and added to the index at producer startup.
     */
    public void setSourceDirectory(File sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
    }

    public File getIndexDirectory() {
        return indexDirectory;
    }

    /**
     * A file system directory in which index files are created upon analysis of the document by the specified analyzer
     */
    public void setIndexDirectory(File indexDirectory) {
        this.indexDirectory = indexDirectory;
    }

    public Analyzer getAnalyzer() {
        return analyzer;
    }

    /**
     * An Analyzer builds TokenStreams, which analyze text. It thus represents a policy for extracting index terms from text.
     * The value for analyzer can be any class that extends the abstract class org.apache.lucene.analysis.Analyzer.
     * Lucene also offers a rich set of analyzers out of the box
     */
    public void setAnalyzer(Analyzer analyzer) {
        this.analyzer = analyzer;
    }

    public int getMaxHits() {
        return maxHits;
    }

    /**
     * An integer value that limits the result set of the search operation
     */
    public void setMaxHits(int maxHits) {
        this.maxHits = maxHits;
    }
    
    public void setLuceneVersion(Version luceneVersion) {
        this.luceneVersion = luceneVersion;
    }

    public Version getLuceneVersion() {
        return luceneVersion;
    }
    
}
