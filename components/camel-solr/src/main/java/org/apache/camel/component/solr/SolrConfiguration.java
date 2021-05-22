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

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.http.client.HttpClient;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.impl.LBHttpSolrClient;

@UriParams
public class SolrConfiguration {

    private final SolrScheme solrScheme;

    @UriPath(description = "Hostname and port for the solr server. " +
                           "Multiple hosts can be specified, separated with a comma." +
                           "In that case, the scheme decides between CloudSolrClient and LBHttpSolrClient.")
    @Metadata(required = true)
    private String url;
    @UriParam(defaultValue = "" + SolrConstants.DEFUALT_STREAMING_QUEUE_SIZE)
    private int streamingQueueSize = SolrConstants.DEFUALT_STREAMING_QUEUE_SIZE;
    @UriParam(defaultValue = "" + SolrConstants.DEFAULT_STREAMING_THREAD_COUNT)
    private int streamingThreadCount = SolrConstants.DEFAULT_STREAMING_THREAD_COUNT;
    @UriParam
    private Integer soTimeout;
    @UriParam
    private Integer connectionTimeout;
    @UriParam
    private Boolean followRedirects;
    @UriParam
    private Boolean allowCompression;
    @UriParam(label = "solrCloud")
    private String zkHost;
    @UriParam(label = "solrCloud")
    private String zkChroot;
    @UriParam(label = "solrCloud")
    private String collection;
    @UriParam(label = "solrClient")
    private SolrClient solrClient;
    @UriParam(label = "httpClient")
    private HttpClient httpClient;
    @UriParam
    private String requestHandler;
    @UriParam(label = "security", secret = true)
    private String username;
    @UriParam(label = "security", secret = true)
    private String password;
    @UriParam(defaultValue = "false")
    private boolean autoCommit;

    private String endpointUri;
    private Boolean useConcurrentUpdateSolrClient;
    private SolrEndpoint solrEndpoint;

    public SolrConfiguration(String endpointUri, String remaining) throws Exception {
        this.endpointUri = endpointUri;
        if (endpointUri.startsWith("solrs")) {
            solrScheme = SolrScheme.SOLRS;
        } else if (endpointUri.startsWith("solrCloud")) {
            solrScheme = SolrScheme.SOLRCLOUD;
        } else {
            solrScheme = SolrScheme.SOLR;
        }
        this.url = remaining;
        getUrlListFrom(this.url);
    }

    public SolrScheme getSolrScheme() {
        return solrScheme;
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

    public int getStreamingThreadCount() {
        return streamingThreadCount;
    }

    /**
     * Set the number of threads for the StreamingUpdateSolrServer
     */
    public void setStreamingThreadCount(int streamingThreadCount) {
        this.streamingThreadCount = streamingThreadCount;
    }

    public Integer getSoTimeout() {
        return soTimeout;
    }

    /**
     * Read timeout on the SolrClient
     */
    public void setSoTimeout(Integer soTimeout) {
        this.soTimeout = soTimeout;
    }

    public Integer getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * connectionTimeout on the SolrClient
     */
    public void setConnectionTimeout(Integer connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
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

    public String getZkHost() {
        return zkHost;
    }

    /**
     * Set the ZooKeeper host urls which the CloudSolrClient uses, e.g. "zkhost=localhost:8123,localhost:8124".
     * Optionally add the chroot, e.g. "zkhost=localhost:8123,localhost:8124/mysolr".
     */
    public void setZkHost(String zkHost) {
        if (zkHost.contains("/")) {
            String[] parts = zkHost.split("/");
            this.zkHost = parts[0];
            this.zkChroot = parts[1];
        } else {
            this.zkHost = zkHost;
        }
    }

    public String getZkChroot() {
        return zkChroot;
    }

    /**
     * Set the chroot of the zookeeper connections
     */
    public void setZkChroot(String zkChroot) {
        this.zkChroot = zkChroot;
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
     * If true, each producer operation will be committed automatically
     */
    public void setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    public SolrClient getSolrClient() {
        return solrClient;
    }

    /**
     * Uses provided solr client to connect to solr
     */
    public void setSolrClient(SolrClient solrClient) {
        this.solrClient = solrClient;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * Uses provided http client to generate the solr client
     */
    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Boolean getUseConcurrentUpdateSolrClient() {
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

    private String getFirstUrlFrom(String url) throws Exception {
        return getUrlListFrom(url).get(0);
    }

    private List<String> getUrlListFrom(String url) throws Exception {
        List<String> urlList = Arrays
                .asList(url.split(","))
                .stream()
                .map(s -> solrScheme.getScheme().concat(s))
                .collect(Collectors.toList());
        // validate url syntax when scheme available (=not solrCloud)
        if (!SolrScheme.SOLRCLOUD.equals(solrScheme)) {
            for (String s : urlList) {
                new URL(s);
            }
        }
        return urlList;
    }

    /**
     * signature defines parameters deciding whether or not to share the solrClient - sharing allowed: same signature -
     * sharing not allowed: different signature
     */
    public String getSignature() {
        if (solrClient != null) {
            return solrClient.toString();
        }
        StringBuilder sb = new StringBuilder();
        if (solrEndpoint != null) {
            sb.append(solrEndpoint);
        }
        if (useConcurrentUpdateSolrClient) {
            sb.append("_");
            sb.append(useConcurrentUpdateSolrClient);
        }
        return sb.toString();
    }

    protected SolrClient initSolrClient() throws Exception {
        // explicilty defined solrClient
        if (solrClient != null) {
            return solrClient;
        }
        // backward compatibility (CloudSolrClient only used when
        //      zkHost and collection are not null
        if (zkHost != null && collection != null && zkChroot == null) {
            return getCloudSolrClient(Arrays.asList(zkHost));
        }
        // more than 1 server provided:
        //      if solrCloud uri then CloudSolrClient else LBHttpSolrClient
        List<String> serverUrls = getUrlListFrom(zkHost != null ? zkHost : url);
        if (serverUrls.size() > 1) {
            if (SolrScheme.SOLRCLOUD.equals(solrScheme)) {
                return getCloudSolrClient(serverUrls);
            } else {
                return getLBHttpSolrClient(serverUrls);
            }
        }
        // config with ConcurrentUpdateSolrClient
        if (useConcurrentUpdateSolrClient) {
            return getConcurrentUpdateSolrClient();
        }
        // base HttpSolrClient
        return getHttpSolrClient();
    }

    private SolrClient getCloudSolrClient(List<String> serverUrls) {
        CloudSolrClient.Builder builder = zkHost != null
                ? new CloudSolrClient.Builder(serverUrls, zkChroot == null ? Optional.empty() : Optional.of(zkChroot))
                : new CloudSolrClient.Builder(serverUrls);
        if (connectionTimeout != null) {
            builder.withConnectionTimeout(connectionTimeout);
        }
        if (soTimeout != null) {
            builder.withSocketTimeout(soTimeout);
        }
        if (httpClient != null) {
            builder.withHttpClient(httpClient);
        }
        CloudSolrClient cloudSolrClient = builder.build();
        if (collection != null) {
            cloudSolrClient.setDefaultCollection(collection);
        }
        return cloudSolrClient;
    }

    private SolrClient getLBHttpSolrClient(List<String> serverUrls) {
        LBHttpSolrClient.Builder builder = new LBHttpSolrClient.Builder();
        for (String serverUrl : serverUrls) {
            builder.withBaseSolrUrl(serverUrl);
        }
        if (connectionTimeout != null) {
            builder.withConnectionTimeout(connectionTimeout);
        }
        if (soTimeout != null) {
            builder.withSocketTimeout(soTimeout);
        }
        if (httpClient != null) {
            builder.withHttpClient(httpClient);
        }
        return builder.build();
    }

    private SolrClient getConcurrentUpdateSolrClient() throws Exception {
        ConcurrentUpdateSolrClient.Builder builder = new ConcurrentUpdateSolrClient.Builder(getFirstUrlFrom(url));
        if (connectionTimeout != null) {
            builder.withConnectionTimeout(connectionTimeout);
        }
        if (soTimeout != null) {
            builder.withSocketTimeout(soTimeout);
        }
        if (httpClient != null) {
            builder.withHttpClient(httpClient);
        }
        builder.withQueueSize(streamingQueueSize);
        builder.withThreadCount(streamingThreadCount);
        return builder.build();
    }

    private SolrClient getHttpSolrClient() throws Exception {
        HttpSolrClient.Builder builder = new HttpSolrClient.Builder(getFirstUrlFrom(url));
        if (connectionTimeout != null) {
            builder.withConnectionTimeout(connectionTimeout);
        }
        if (soTimeout != null) {
            builder.withSocketTimeout(soTimeout);
        }
        if (httpClient != null) {
            builder.withHttpClient(httpClient);
        }
        if (allowCompression != null) {
            builder.allowCompression(allowCompression);
        }
        HttpSolrClient httpSolrClient = builder.build();
        if (followRedirects != null) {
            httpSolrClient.setFollowRedirects(followRedirects);
        }
        return httpSolrClient;
    }

    public SolrConfiguration newCopy() throws CloneNotSupportedException {
        return (SolrConfiguration) super.clone();
    }

    public enum SolrScheme {

        SOLR("http://"),
        SOLRS("https://"),
        SOLRCLOUD("");

        private final String scheme;

        SolrScheme(String scheme) {
            this.scheme = scheme;
        }

        public String getScheme() {
            return scheme;
        }
    }

}
