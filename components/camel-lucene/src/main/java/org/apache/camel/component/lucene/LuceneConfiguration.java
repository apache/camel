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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

public class LuceneConfiguration {
    private static final transient Log LOG = LogFactory.getLog(LuceneConfiguration.class);
    private URI uri;
    private String protocolType;
    private String authority;
    private String host;
    private String operation;
    private File sourceDirectory;
    private File indexDirectory;
    private Analyzer analyzer;
    private int maxHits;

    public LuceneConfiguration() {
    }

    public LuceneConfiguration(URI uri) throws Exception {
        this();
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
        setOperation(retrieveTokenFromAuthority("operation"));

        sourceDirectory = component.resolveAndRemoveReferenceParameter(
                parameters, "srcDir", File.class, null);
        indexDirectory = component.resolveAndRemoveReferenceParameter(
                parameters, "indexDir", File.class, new File("file:///./indexDirectory"));
        analyzer = component.resolveAndRemoveReferenceParameter(
                parameters, "analyzer", Analyzer.class, new StandardAnalyzer(org.apache.lucene.util.Version.LUCENE_CURRENT));

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

    public String getProtocolType() {
        return protocolType;
    }

    public void setProtocolType(String protocolType) {
        this.protocolType = protocolType;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
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

    public void setSourceDirectory(File sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
    }

    public File getIndexDirectory() {
        return indexDirectory;
    }

    public void setIndexDirectory(File indexDirectory) {
        this.indexDirectory = indexDirectory;
    }

    public Analyzer getAnalyzer() {
        return analyzer;
    }

    public void setAnalyzer(Analyzer analyzer) {
        this.analyzer = analyzer;
    }

    public int getMaxHits() {
        return maxHits;
    }

    public void setMaxHits(int maxHits) {
        this.maxHits = maxHits;
    }    
    
}
