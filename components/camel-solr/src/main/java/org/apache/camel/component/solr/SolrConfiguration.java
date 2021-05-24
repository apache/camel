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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.StringHelper;
import org.apache.http.client.HttpClient;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.impl.LBHttpSolrClient;

@UriParams
public class SolrConfiguration implements Cloneable {

    private final SolrScheme solrScheme;

    private boolean useConcurrentUpdateSolrClient;
    private SolrEndpoint solrEndpoint;

    @UriPath(description = "Hostname and port for the server. " +
                           "Multiple hosts can be specified, separated with a comma. " +
                           "The host/port can be for the solr endpoint (solrhost:solrport/solr) " +
                           "or for the zookeeper endpoint (zkhost:zkport(optional: /chroot)). " +
                           "See the solrClient for more information on what SolrClient is used.")
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

    public SolrConfiguration(String endpointUri, String remaining) throws Exception {
        solrScheme = SolrScheme.SOLR.getFrom(endpointUri);
        Optional<String> chroot = getChrootFromPath(remaining);
        if (chroot.isPresent()) {
            zkChroot = chroot.get();
        }
        url = parseHostsFromUrl(remaining, chroot);
        // validate url
        getUrlListFrom(url);
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
     * Set the ZooKeeper host(s) urls which the CloudSolrClient uses, e.g. "zkHost=localhost:8123,localhost:8124".
     * Optionally add the chroot, e.g. "zkHost=localhost:8123,localhost:8124/rootformysolr". When first part of the
     * chroot path in the zkHost parameter is "solr" (e.g. "localhost:8123/solr" or "localhost:8123/solr/.."), then that
     * path is not considered as zookeeper chroot for backward compatibility; Specify explicitly the zkChroot parameter
     * (zkChroot=solr or zkChroot=solr/..) when this 'solr' path should be considered a zookeeper chroot anyway.
     */
    public void setZkHost(String zkHost) {
        Optional<String> chroot = getChrootFromPath(zkHost);
        if (chroot.isPresent()) {
            this.zkChroot = chroot.get();
        }
        this.zkHost = parseHostsFromUrl(zkHost, chroot);
    }

    public String getZkChroot() {
        return zkChroot;
    }

    /**
     * Set the chroot of the zookeeper connection (including the leading slash; e.g. '/mychroot')
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
     * If true, each producer operation will be followed by a commit automatically
     */
    public void setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    public SolrClient getSolrClient() {
        return solrClient;
    }

    /**
     * Uses the provided solr client to connect to solr When this parameter is not specified, the following logic is
     * applied to determine the SolrClient being used: - when both zkHost and collection (=default collection)
     * parameters are set, the CloudSolrClient is used - when zkChroot (=zookeeper root) parameter is set, the
     * CloudSolrClient is used - when multiple hosts are specified (in zkHost or in uri; separated with a comma) then
     * the following SolrClient is used: - CloudSolrClient when the uri is solrCloud - LBHttpSolrClient when the uri is
     * not solrCloud (solr or solrs) - when the solr operation is INSERT_STREAMING, then the ConcurrentUpdateSolrClient
     * is used - otherwise, the HttpSolrClient is used
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
        // add scheme when required
        List<String> urlList = Arrays
                .asList(url.split(","))
                .stream()
                .map(s -> solrScheme.getScheme().concat(s))
                .collect(Collectors.toList());
        // validate url syntax via parsing in URL instance
        // (solrCloud requires addition of HTTP scheme to be considered a valid URL scheme
        for (String s : urlList) {
            try {
                new URL(
                        SolrScheme.SOLRCLOUD.equals(solrScheme) ? SolrScheme.SOLR.getScheme().concat(s) : s);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(
                        String.format(
                                "Url '%s' not valid for endpoint with uri=%s",
                                s,
                                solrScheme.getUri()));
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
        List<String> urlList = getUrlListFrom(zkHost != null ? zkHost : url);
        // backward compatibility (CloudSolrClient only used when
        //      zkHost and collection are not null
        if ((zkHost != null && collection != null)
                || (zkChroot != null && !zkChroot.isEmpty())) {
            return getCloudSolrClient(urlList);
        }
        // more than 1 server provided:
        //      if solrCloud uri then CloudSolrClient else LBHttpSolrClient
        if (urlList.size() > 1) {
            if (SolrScheme.SOLRCLOUD.equals(solrScheme)) {
                return getCloudSolrClient(urlList);
            } else {
                return getLBHttpSolrClient(urlList);
            }
        }
        // config with ConcurrentUpdateSolrClient
        if (useConcurrentUpdateSolrClient) {
            return getConcurrentUpdateSolrClient();
        }
        // base HttpSolrClient
        return getHttpSolrClient();
    }

    private SolrClient getCloudSolrClient(List<String> urlList) {
        Optional<String> zkChrootOptional = Optional.ofNullable(zkChroot);
        CloudSolrClient.Builder builder = new CloudSolrClient.Builder(urlList, zkChrootOptional);
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

    private static Optional<String> getChrootFromPath(String path) {
        // check further slash characters for 1st subPath = 'solr'
        if (!path.contains("/")) {
            return Optional.empty();
        }
        return Arrays.asList(path.split("/")).get(1).equals("solr")
                ? Optional.empty() : Optional.of(path.substring(path.indexOf('/')));
    }

    private static String parseHostsFromUrl(String path, Optional<String> chroot) {
        String hostsPath = StringHelper.removeStartingCharacters(path, '/');
        return (chroot.isPresent()) ? hostsPath.substring(0, hostsPath.indexOf('/') - 1) : hostsPath;
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
                if (endpointUri.startsWith(solrScheme.getUri())) {
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
