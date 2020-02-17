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
package org.apache.camel.component.solr;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Optional;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;

/**
 * The solr component allows you to interface with an Apache Lucene Solr server.
 */
@UriEndpoint(firstVersion = "2.9.0", scheme = "solr,solrs,solrCloud", title = "Solr", syntax = "solr:url", producerOnly = true, label = "monitoring,search")
public class SolrEndpoint extends DefaultEndpoint {

    private String scheme = "http://";

    @UriPath(description = "Hostname and port for the solr server")
    @Metadata(required = true)
    private String url;
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
    @UriParam
    private Boolean followRedirects;
    @UriParam
    private Boolean allowCompression;
    @UriParam(label = "solrCloud")
    private String zkHost;
    @UriParam(label = "solrCloud")
    private String collection;
    @UriParam
    private String requestHandler;
    @UriParam(label = "security", secret = true)
    private String username;
    @UriParam(label = "security", secret = true)
    private String password;

    public SolrEndpoint(String endpointUri, SolrComponent component, String address) throws Exception {
        super(endpointUri, component);
        if (endpointUri.startsWith("solrs")) {
            scheme = "https://";
        }
        URL url = new URL(scheme + address);
        this.url = url.toString();
    }

    /**
     * Set the ZooKeeper host information which the solrCloud could use, such as "zkhost=localhost:8123".
     */
    public void setZkHost(String zkHost) {
        try {
            String decoded = URLDecoder.decode(zkHost, "UTF-8");
            this.zkHost = decoded;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public String getZkHost() {
        return this.zkHost;
    }

    /**
     * Set the collection name which the solrCloud server could use
     */
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

    private CloudSolrClient getCloudServer() {
        CloudSolrClient rVal = null;
        if (this.getZkHost() != null && this.getCollection() != null) {
            rVal = new CloudSolrClient.Builder(java.util.Arrays.asList(zkHost), Optional.empty()).build();
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
            CloudSolrClient cloudServer = getCloudServer();
            if (cloudServer == null) {
                HttpSolrClient.Builder solrServerBuilder = new HttpSolrClient.Builder(url);
                ConcurrentUpdateSolrClient solrStreamingServer = new ConcurrentUpdateSolrClient.Builder(url)
                        .withQueueSize(streamingQueueSize)
                        .withThreadCount(streamingThreadCount)
                        .build();
                if (soTimeout != null) {
                    solrServerBuilder.withSocketTimeout(soTimeout);
                }
                if (connectionTimeout != null) {
                    solrServerBuilder.withConnectionTimeout(connectionTimeout);
                }                

                HttpSolrClient solrServer = solrServerBuilder.build();
                // set the properties on the solr server
                if (followRedirects != null) {
                    solrServer.setFollowRedirects(followRedirects);
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

    /**
     * Set the request handler to be used
     */
    public void setRequestHandler(String requestHandler) {
        this.requestHandler = requestHandler;
    }

    public String getRequestHandler() {
        return requestHandler;
    }

    public int getStreamingThreadCount() {
        return streamingThreadCount;
    }

    /**
     * Set the number of threads for the StreamingUpdateSolrServer
     */
    public void setStreamingThreadCount(int streamingThreadCount) {
        this.streamingThreadCount = streamingThreadCount;
    }

    public int getStreamingQueueSize() {
        return streamingQueueSize;
    }

    /**
     * Set the queue size for the StreamingUpdateSolrServer
     */
    public void setStreamingQueueSize(int streamingQueueSize) {
        this.streamingQueueSize = streamingQueueSize;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    /**
     * Maximum number of retries to attempt in the event of transient errors
     */
    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Integer getSoTimeout() {
        return soTimeout;
    }

    /**
     * Read timeout on the underlying HttpConnectionManager. This is desirable for queries, but probably not for indexing
     */
    public void setSoTimeout(Integer soTimeout) {
        this.soTimeout = soTimeout;
    }

    public Integer getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * connectionTimeout on the underlying HttpConnectionManager
     */
    public void setConnectionTimeout(Integer connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Integer getDefaultMaxConnectionsPerHost() {
        return defaultMaxConnectionsPerHost;
    }

    /**
     * maxConnectionsPerHost on the underlying HttpConnectionManager
     */
    public void setDefaultMaxConnectionsPerHost(Integer defaultMaxConnectionsPerHost) {
        this.defaultMaxConnectionsPerHost = defaultMaxConnectionsPerHost;
    }

    public Integer getMaxTotalConnections() {
        return maxTotalConnections;
    }

    /**
     * maxTotalConnection on the underlying HttpConnectionManager
     */
    public void setMaxTotalConnections(Integer maxTotalConnections) {
        this.maxTotalConnections = maxTotalConnections;
    }

    public Boolean getFollowRedirects() {
        return followRedirects;
    }

    /**
     * indicates whether redirects are used to get to the Solr server
     */
    public void setFollowRedirects(Boolean followRedirects) {
        this.followRedirects = followRedirects;
    }

    public Boolean getAllowCompression() {
        return allowCompression;
    }

    /**
     * Server side must support gzip or deflate for this to have any effect
     */
    public void setAllowCompression(Boolean allowCompression) {
        this.allowCompression = allowCompression;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Sets username for basic auth plugin enabled servers
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Sets password for basic auth plugin enabled servers
     */
    public void setPassword(String password) {
        this.password = password;
    }

}
