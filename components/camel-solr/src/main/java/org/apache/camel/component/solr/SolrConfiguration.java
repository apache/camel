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

import java.util.Optional;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.http.client.HttpClient;
import org.apache.solr.client.solrj.SolrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriParams
public class SolrConfiguration implements Cloneable {

    private static final Logger LOG = LoggerFactory.getLogger(SolrConfiguration.class);

    private boolean useConcurrentUpdateSolrClient;
    private SolrEndpoint solrEndpoint;
    private SolrScheme solrScheme;

    @UriPath(description = "Hostname and port for the Solr server(s). " +
                           "Multiple hosts can be specified, separated with a comma. " +
                           "See the solrClient parameter for more information on the SolrClient used to connect to Solr.")
    @Metadata(required = true)
    private final String url;
    @UriParam(defaultValue = "" + SolrConstants.DEFUALT_STREAMING_QUEUE_SIZE)
    private int streamingQueueSize = SolrConstants.DEFUALT_STREAMING_QUEUE_SIZE;
    @UriParam(defaultValue = "" + SolrConstants.DEFAULT_STREAMING_THREAD_COUNT)
    private int streamingThreadCount = SolrConstants.DEFAULT_STREAMING_THREAD_COUNT;
    @UriParam
    private Integer soTimeout;
    @UriParam
    private Integer connectionTimeout;
    @UriParam(label = "HttpSolrClient")
    private Boolean followRedirects;
    @UriParam(label = "HttpSolrClient")
    private Boolean allowCompression;
    @UriParam(label = "CloudSolrClient")
    private String zkHost;
    @UriParam(label = "CloudSolrClient")
    private String zkChroot;
    @UriParam(label = "CloudSolrClient")
    private String collection;
    @UriParam
    private SolrClient solrClient;
    @UriParam
    private HttpClient httpClient;
    @UriParam
    private String requestHandler;
    @UriParam(label = "security", secret = true)
    private String username;
    @UriParam(label = "security", secret = true)
    private String password;
    @UriParam(defaultValue = "false")
    private boolean autoCommit;
    @Deprecated
    @UriParam
    private Integer maxRetries;
    @Deprecated
    @UriParam
    private Integer defaultMaxConnectionsPerHost;
    @Deprecated
    @UriParam
    private Integer maxTotalConnections;

    public SolrConfiguration(SolrScheme solrScheme, String url, String zkChroot) {
        this.solrScheme = solrScheme;
        this.url = url;
        this.zkChroot = zkChroot;
    }

    public static SolrConfiguration newInstance(String endpointUri, String remaining) {
        SolrScheme solrScheme = SolrScheme.SOLR.getFrom(endpointUri);
        Optional<String> zkChrootOptional = SolrClientHandler.getZkChrootFromUrl(remaining);
        String url = SolrClientHandler.parseHostsFromUrl(remaining, zkChrootOptional);
        SolrConfiguration solrConfiguration = new SolrConfiguration(solrScheme, url, zkChrootOptional.orElse(null));
        // validate url
        SolrClientHandler.getUrlListFrom(solrConfiguration);
        // return configuration
        return solrConfiguration;
    }

    public SolrScheme getSolrScheme() {
        return solrScheme;
    }

    public void setSolrScheme(SolrScheme solrScheme) {
        this.solrScheme = solrScheme;
    }

    public String getUrl() {
        return url;
    }

    public int getStreamingQueueSize() {
        return streamingQueueSize;
    }

    /**
     * Sets the queue size for the ConcurrentUpdateSolrClient
     */
    public void setStreamingQueueSize(int streamingQueueSize) {
        this.streamingQueueSize = streamingQueueSize;
    }

    public int getStreamingThreadCount() {
        return streamingThreadCount;
    }

    /**
     * Sets the number of threads for the ConcurrentUpdateSolrClient
     */
    public void setStreamingThreadCount(int streamingThreadCount) {
        this.streamingThreadCount = streamingThreadCount;
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
     * Sets the socket timeout on the SolrClient
     */
    public void setSoTimeout(Integer soTimeout) {
        this.soTimeout = soTimeout;
    }

    public Integer getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Sets the connection timeout on the SolrClient
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
     * Indicates whether redirects are used to get to the Solr server
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

    public String getZkHost() {
        return zkHost;
    }

    /**
     * Set the ZooKeeper host(s) urls which the CloudSolrClient uses, e.g. "zkHost=localhost:2181,localhost:2182".
     * Optionally add the chroot, e.g. "zkHost=localhost:2181,localhost:2182/rootformysolr". In case the first part of
     * the url path (='contextroot') is set to 'solr' (e.g. 'localhost:2181/solr' or 'localhost:2181/solr/..'), then
     * that path is not considered as zookeeper chroot for backward compatibility reasons (this behaviour can be
     * overridden via zkChroot parameter).
     */
    public void setZkHost(String zkHost) {
        Optional<String> zkChrootFromZkHost = SolrClientHandler.getZkChrootFromUrl(zkHost);
        if (zkChrootFromZkHost.isPresent() && getZkChroot() == null) {
            setZkChroot(zkChrootFromZkHost.get());
        }
        this.zkHost = SolrClientHandler.parseHostsFromUrl(zkHost, zkChrootFromZkHost);
        setSolrScheme(SolrScheme.SOLRCLOUD);
    }

    public String getZkChroot() {
        return zkChroot;
    }

    /**
     * Set the chroot of the zookeeper connection (include the leading slash; e.g. '/mychroot')
     */
    public void setZkChroot(String zkChroot) {
        this.zkChroot = zkChroot;
        setSolrScheme(SolrScheme.SOLRCLOUD);
    }

    public String getCollection() {
        return collection;
    }

    /**
     * Set the default collection for SolrCloud
     */
    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getRequestHandler() {
        return requestHandler;
    }

    /**
     * Set the request handler to be used
     */
    public void setRequestHandler(String requestHandler) {
        this.requestHandler = requestHandler;
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

    public boolean isAutoCommit() {
        return autoCommit;
    }

    /**
     * If true, each producer operation will be automatically followed by a commit
     */
    public void setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    public SolrClient getSolrClient() {
        return solrClient;
    }

    /**
     * Uses the provided solr client to connect to solr. When this parameter is not specified, camel applies the
     * following rules to determine the SolrClient: 1) when zkHost or zkChroot (=zookeeper root) parameter is set, then
     * the CloudSolrClient is used. 2) when multiple hosts are specified in the uri (separated with a comma), then the
     * CloudSolrClient (uri scheme is 'solrCloud') or the LBHttpSolrClient (uri scheme is not 'solrCloud') is used. 3)
     * when the solr operation is INSERT_STREAMING, then the ConcurrentUpdateSolrClient is used. 4) otherwise, the
     * HttpSolrClient is used. Note: A CloudSolrClient should point to zookeeper endpoint(s); other clients point to
     * Solr endpoint(s). The SolrClient can also be set via the exchange header 'CamelSolrClient'.
     */
    public void setSolrClient(SolrClient solrClient) {
        this.solrClient = solrClient;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * Sets the http client to be used by the solrClient. This is only applicable when solrClient is not set.
     */
    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public boolean getUseConcurrentUpdateSolrClient() {
        return useConcurrentUpdateSolrClient;
    }

    void setUseConcurrentUpdateSolrClient(boolean useConcurrentUpdateSolrClient) {
        this.useConcurrentUpdateSolrClient = useConcurrentUpdateSolrClient;
    }

    public SolrEndpoint getSolrEndpoint() {
        return solrEndpoint;
    }

    void setSolrEndpoint(SolrEndpoint solrEndpoint) {
        this.solrEndpoint = solrEndpoint;
    }

    public SolrConfiguration deepCopy() {
        try {
            return (SolrConfiguration) this.clone();
        } catch (CloneNotSupportedException e) {
            LOG.error(
                    String.format(
                            "Could not generate new configuration based on configuration of existing endpoint %s",
                            getSolrEndpoint()),
                    e);
        }
        return null;
    }

    public enum SolrScheme {

        SOLR("solr:", "http://"),
        SOLRS("solrs:", "https://"),
        SOLRCLOUD("solrCloud:", "");

        private final String uri;
        private final String scheme;

        SolrScheme(String uri, String scheme) {
            this.uri = uri;
            this.scheme = scheme;
        }

        public SolrScheme getFrom(String endpointUri) {
            for (SolrScheme solrScheme : SolrScheme.values()) {
                if (endpointUri.startsWith(solrScheme.uri)) {
                    return solrScheme;
                }
            }
            throw new IllegalArgumentException("Invalid endpoint uri");
        }

        public String getUri() {
            return uri;
        }

        public String getScheme() {
            return scheme;
        }

    }

}
