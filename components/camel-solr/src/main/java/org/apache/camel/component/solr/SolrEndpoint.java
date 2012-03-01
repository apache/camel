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
package org.apache.camel.component.solr;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;

/**
 * Represents a Solr endpoint.
 */
public class SolrEndpoint extends DefaultEndpoint {

    private CommonsHttpSolrServer solrServer;
    private String requestHandler;

    public SolrEndpoint() {
    }

    public SolrEndpoint(String uri, SolrComponent component) {
        super(uri, component);
    }

    public SolrEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public SolrEndpoint(String endpointUri, SolrComponent component, String address) throws Exception {
        super(endpointUri, component);
        solrServer = new CommonsHttpSolrServer("http://" + address);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SolrProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for Solr endpoint.");
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public CommonsHttpSolrServer getSolrServer() {
        return solrServer;
    }

    public void setMaxRetries(int maxRetries) {
        solrServer.setMaxRetries(maxRetries);
    }

    public void setSoTimeout(int soTimeout) {
        solrServer.setSoTimeout(soTimeout);
    }

    public void setConnectionTimeout(int connectionTimeout) {
        solrServer.setConnectionTimeout(connectionTimeout);
    }

    public void setDefaultMaxConnectionsPerHost(int defaultMaxConnectionsPerHost) {
        solrServer.setDefaultMaxConnectionsPerHost(defaultMaxConnectionsPerHost);
    }

    public void setMaxTotalConnections(int maxTotalConnections) {
        solrServer.setMaxTotalConnections(maxTotalConnections);
    }

    public void setFollowRedirects(boolean followRedirects) {
        solrServer.setFollowRedirects(followRedirects);
    }

    public void setAllowCompression(boolean allowCompression) {
        solrServer.setAllowCompression(allowCompression);
    }

    public void setRequestHandler(String requestHandler) {
        this.requestHandler = requestHandler;
    }

    public String getRequestHandler() {
        return requestHandler;
    }
}
