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

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;

/**
 * Represents a Solr endpoint.
 */
@UriEndpoint(scheme = "solr", syntax = "solr:url", producerOnly = true, label = "monitoring,search")
public class SolrEndpoint extends DefaultEndpoint {

    @UriPath @Metadata(required = "true")
    private String url;
    private String scheme = "http://";
    @UriParam(defaultValue = "" + SolrConstants.DEFUALT_STREAMING_QUEUE_SIZE)
    private int streamingQueueSize = SolrConstants.DEFUALT_STREAMING_QUEUE_SIZE;
    @UriParam(defaultValue = "" + SolrConstants.DEFAULT_STREAMING_THREAD_COUNT)
    private int streamingThreadCount = SolrConstants.DEFAULT_STREAMING_THREAD_COUNT;
    @UriParam
    private Integer maxRetries;
    @UriParam
    private Integer soTimeout;
    @UriParam
    private Integer connectionTimeout;
    @UriParam
    private Integer defaultMaxConnectionsPerHost;
    @UriParam
    private Integer maxTotalConnections;
    @UriParam(defaultValue = "false")
    private Boolean followRedirects;
    @UriParam(defaultValue = "false")
    private Boolean allowCompression;
    @UriParam
    private String zkHost;
    @UriParam
    private String collection;
    @UriParam
    private String requestHandler;

    public SolrEndpoint(String endpointUri, SolrComponent component, String address) throws Exception {
        super(endpointUri, component);
        if (endpointUri.startsWith("solrs")) {
            scheme = "https://";
        }
        URL url = new URL(scheme + address);
        this.url = url.toString();
    }
   
    public void setZkHost(String zkHost) throws UnsupportedEncodingException {
        String decoded = URLDecoder.decode(zkHost, "UTF-8");
        this.zkHost = decoded;
    }

    public String getZkHost() {
        return this.zkHost;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getCollection() {
        return this.collection;
    }

    @Override
    public SolrComponent getComponent() {
        return (SolrComponent) super.getComponent();
    }
    
    private CloudSolrServer getCloudServer() {
        CloudSolrServer rVal = null;
        if (this.getZkHost() != null && this.getCollection() != null) {
            rVal = new CloudSolrServer(zkHost);
            rVal.setDefaultCollection(this.getCollection());
        }
        return rVal;
    }
    
    @Override
    public Producer createProducer() throws Exception {
        // do we have servers?
        SolrComponent.SolrServerReference ref = getComponent().getSolrServers(this);
        if (ref == null) {
            // no then create new servers
            ref = new SolrComponent.SolrServerReference();
            CloudSolrServer cloudServer = getCloudServer();
            if (cloudServer == null) {
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
                ref.setSolrServer(solrServer);
                ref.setUpdateSolrServer(solrStreamingServer);
            }
            ref.setCloudSolrServer(cloudServer);

            getComponent().addSolrServers(this, ref);
        }

        ref.addReference();
        return new SolrProducer(this, ref.getSolrServer(), ref.getUpdateSolrServer(), ref.getCloudSolrServer());
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
