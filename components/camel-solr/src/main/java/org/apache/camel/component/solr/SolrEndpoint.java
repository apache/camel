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

import java.net.URL;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;

/**
 * Represents a Solr endpoint.
 */
public class SolrEndpoint extends DefaultEndpoint {

    private HttpSolrServer solrServer;
    private ConcurrentUpdateSolrServer streamingSolrServer;
    private String requestHandler;
    private int streamingThreadCount;
    private int streamingQueueSize;

    public SolrEndpoint(String endpointUri, SolrComponent component, String address, Map<String, Object> parameters) throws Exception {
        super(endpointUri, component);
        // check the url address
        URL url = new URL("http://" + address);
        solrServer = new HttpSolrServer(url.toString());
        streamingQueueSize = getIntFromString((String) parameters.get(SolrConstants.PARAM_STREAMING_QUEUE_SIZE), SolrConstants.DEFUALT_STREAMING_QUEUE_SIZE);
        streamingThreadCount = getIntFromString((String) parameters.get(SolrConstants.PARAM_STREAMING_THREAD_COUNT), SolrConstants.DEFAULT_STREAMING_THREAD_COUNT);
        streamingSolrServer = new ConcurrentUpdateSolrServer(url.toString(), streamingQueueSize, streamingThreadCount);
    }

    public static int getIntFromString(String value, int defaultValue) {
        if (value != null && value.length() > 0) {
            return Integer.parseInt(value);
        }
        return defaultValue;
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

    public HttpSolrServer getSolrServer() {
        return solrServer;
    }

    public ConcurrentUpdateSolrServer getStreamingSolrServer() {
        return streamingSolrServer;
    }

    public void setStreamingSolrServer(ConcurrentUpdateSolrServer streamingSolrServer) {
        this.streamingSolrServer = streamingSolrServer;
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

    public int getStreamingThreadCount() {
        return streamingThreadCount;
    }

    public void setStreamingThreadCount(int streamingThreadCount) {
        this.streamingThreadCount = streamingThreadCount;
    }

    public int getStreamingQueueSize() {
        return streamingQueueSize;
    }

    public void setStreamingQueueSize(int streamingQueueSize) {
        this.streamingQueueSize = streamingQueueSize;
    }
}
