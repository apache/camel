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

    private String requestHandler;
    private String url;
    private int streamingQueueSize = SolrConstants.DEFUALT_STREAMING_QUEUE_SIZE;
    private int streamingThreadCount = SolrConstants.DEFAULT_STREAMING_THREAD_COUNT;
    private Integer maxRetries;
    private Integer soTimeout;
    private Integer connectionTimeout;
    private Integer defaultMaxConnectionsPerHost;
    private Integer maxTotalConnections;
    private Boolean followRedirects;
    private Boolean allowCompression;

    public SolrEndpoint(String endpointUri, SolrComponent component, String address) throws Exception {
        super(endpointUri, component);
        URL url = new URL("http://" + address);
        this.url = url.toString();
    }

    @Override
    public SolrComponent getComponent() {
        return (SolrComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        // do we have servers?
        SolrComponent.SolrServerReference ref = getComponent().getSolrServers(this);
        if (ref == null) {

            // no then create new servers
            HttpSolrServer solrServer = new HttpSolrServer(url);
            ConcurrentUpdateSolrServer solrStreamingServer = new ConcurrentUpdateSolrServer(url, streamingQueueSize, streamingThreadCount);

            // set the properties on the solr server
            if (maxRetries != null) {
                solrServer.setMaxRetries(maxRetries);
            }
            if (soTimeout != null) {
                solrServer.setSoTimeout(soTimeout);
            }
            if (connectionTimeout != null) {
                solrServer.setConnectionTimeout(connectionTimeout);
            }
            if (defaultMaxConnectionsPerHost != null) {
                solrServer.setDefaultMaxConnectionsPerHost(defaultMaxConnectionsPerHost);
            }
            if (maxTotalConnections != null) {
                solrServer.setMaxTotalConnections(maxTotalConnections);
            }
            if (followRedirects != null) {
                solrServer.setFollowRedirects(followRedirects);
            }
            if (allowCompression != null) {
                solrServer.setAllowCompression(allowCompression);
            }

            ref = new SolrComponent.SolrServerReference();
            ref.setSolrServer(solrServer);
            ref.setUpdateSolrServer(solrStreamingServer);

            getComponent().addSolrServers(this, ref);
        }

        ref.addReference();
        return new SolrProducer(this, ref.getSolrServer(), ref.getUpdateSolrServer());
    }

    protected void onProducerShutdown(SolrProducer producer) {
        SolrComponent.SolrServerReference ref = getComponent().getSolrServers(this);
        if (ref != null) {
            int counter = ref.decReference();
            if (counter <= 0) {
                getComponent().shutdownServers(ref, true);
            }
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for Solr endpoint.");
    }

    @Override
    public boolean isSingleton() {
        return true;
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

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Integer getSoTimeout() {
        return soTimeout;
    }

    public void setSoTimeout(Integer soTimeout) {
        this.soTimeout = soTimeout;
    }

    public Integer getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Integer connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Integer getDefaultMaxConnectionsPerHost() {
        return defaultMaxConnectionsPerHost;
    }

    public void setDefaultMaxConnectionsPerHost(Integer defaultMaxConnectionsPerHost) {
        this.defaultMaxConnectionsPerHost = defaultMaxConnectionsPerHost;
    }

    public Integer getMaxTotalConnections() {
        return maxTotalConnections;
    }

    public void setMaxTotalConnections(Integer maxTotalConnections) {
        this.maxTotalConnections = maxTotalConnections;
    }

    public Boolean getFollowRedirects() {
        return followRedirects;
    }

    public void setFollowRedirects(Boolean followRedirects) {
        this.followRedirects = followRedirects;
    }

    public Boolean getAllowCompression() {
        return allowCompression;
    }

    public void setAllowCompression(Boolean allowCompression) {
        this.allowCompression = allowCompression;
    }

}
