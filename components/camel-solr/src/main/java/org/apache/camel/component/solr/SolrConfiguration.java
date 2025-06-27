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

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.apache.solr.client.solrj.SolrClient;

@UriParams
public class SolrConfiguration {

    @UriPath
    @Metadata(required = true)
    private String host;
    @UriPath(defaultValue = "" + SolrConstants.DEFAULT_PORT)
    private int port = -1;
    @UriPath(defaultValue = SolrConstants.DEFAULT_BASE_PATH)
    private String basePath;
    @UriParam(label = "security", secret = true)
    private String username;
    @UriParam(label = "security", secret = true)
    private String password;
    @UriParam
    private SolrOperation operation;
    @UriParam
    private boolean autoCommit;
    @UriParam
    private Integer size;
    @UriParam
    private Integer from;
    @UriParam
    private String collection;
    @UriParam
    private String requestHandler;
    @UriParam(defaultValue = "true")
    private boolean deleteByQuery = false;
    @UriParam(label = "security")
    private boolean enableSSL;
    @UriParam(label = "security")
    @Metadata(supportFileReference = true)
    private String certificatePath;
    @UriParam
    private Long requestTimeout;
    @UriParam
    private Long connectionTimeout;
    @UriParam(defaultValue = "true")
    private boolean async = true;
    @UriParam
    @Metadata(label = "advanced")
    private SolrClient solrClient;

    /**
     * Uri of the solr instance with :host:port. Only a single solr instance should be targeted. When failover multiple
     * instances is required, use a camel Failover Load Balancer with sticky mode or provide your configured
     * CloudSolrClient as client. Only host and port are "documented" for the endpoint uri, without a base path
     * (currently /solr and /api). This is to promote the use of solr endpoint without a base path: we want to avoid
     * locking in (this evolution of) the base path into the camel configuration of the endpoint.
     */
    public void configure(String uriString) throws URISyntaxException {
        URI uri = new URI(uriString);
        String value = uri.getHost();
        if (value != null) {
            if (!value.equalsIgnoreCase("default")) {
                setHost(value);
            }
        } else {
            throw new ResolveEndpointFailedException(uriString, "Solr instance host must be configured on the endpoint");
        }
        int uriPort = uri.getPort();
        if (uriPort > 0) {
            setPort(uriPort);
        } else if (this.port <= 0) {
            // resolve the default port if no port number was provided, and not already configured with a port number
            setPort(SolrConstants.DEFAULT_PORT);
        }
        String userInfo = uri.getUserInfo();
        if (userInfo != null) {
            String[] parts = uri.getUserInfo().split(":");
            if (parts.length == 2) {
                setUsername(parts[0]);
                setPassword(parts[1]);
            } else {
                setUsername(userInfo);
            }
        }
        // parse remaining path when set and not equal to "/solr"
        String remainingPath = uri.getPath();
        if (ObjectHelper.isNotEmpty(remainingPath)
                && !remainingPath.equals(SolrConstants.DEFAULT_BASE_PATH)) {
            if (remainingPath.startsWith(SolrConstants.DEFAULT_BASE_PATH.concat("/"))) {
                // the default solr base path //host:port/solr/<collection>/<requestHandler>
                // --> use collection and request handler
                String[] parts = remainingPath.substring(SolrConstants.DEFAULT_BASE_PATH.concat("/").length()).split("/");
                if (parts.length > 0) {
                    setCollection(parts[0]);
                }
                if (parts.length > 1) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 1; i < parts.length; i++) {
                        sb.append("/").append(parts[i]);
                    }
                    setRequestHandler(sb.toString());
                }
            } else {
                // NOT the default solr base path --> use path as base path for backward compatibility
                // (collection and requesthandler can be set via endpoint parameters)
                setBasePath(remainingPath);
            }
        }
    }

    public String getSolrBaseUrl() {
        return getSolrBaseUrl(isEnableSSL(), getHost(), getPort(), getBasePath());
    }

    public static String getSolrBaseUrl(boolean sslEnabled, String host, int port, String basePath) {
        return String.format(
                "%s://%s:%d%s",
                sslEnabled ? "https" : "http",
                host == null ? SolrConstants.DEFAULT_HOST : host,
                port == -1 ? SolrConstants.DEFAULT_PORT : port,
                basePath == null ? "/solr" : basePath);
    }

    public String getHost() {
        return host;
    }

    /**
     * The solr instance host name (set to 'default' to use the host name defined on component level)
     */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * The solr instance base path (usually "/solr")
     */
    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getBasePath() {
        return basePath;
    }

    /**
     * The solr instance port number
     */
    public void setPort(int port) {
        this.port = port;
    }

    public SolrOperation getOperation() {
        return operation;
    }

    /**
     * What operation to perform
     */
    public void setOperation(SolrOperation operation) {
        this.operation = operation;
    }

    public boolean isAutoCommit() {
        return autoCommit;
    }

    /**
     * If true, each producer insert/delete operation will be automatically performing a commit
     */
    public void setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    /**
     * Starting index of the response.
     */
    public Integer getFrom() {
        return from;
    }

    public void setFrom(Integer from) {
        this.from = from;
    }

    /**
     * Size of the response.
     */
    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    /**
     * The name of the collection to act against
     */
    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    /**
     * The path of the update request handler (use for update requests / set solr parameter qt for search requests)
     */
    public String getRequestHandler() {
        return requestHandler;
    }

    public void setRequestHandler(String requestHandler) {
        this.requestHandler = requestHandler;
    }

    /**
     * The solr client to connect to solr. When solrClient bean is provided, all connection properties will be used from
     * that solrClient (host, port, username, password, connectionTimeout, requestTimeout, enableSSL, ...). When not
     * explicitly configured, camel uses the HttpJdkSolrClient.
     */
    public SolrClient getSolrClient() {
        return solrClient;
    }

    public void setSolrClient(SolrClient solrClient) {
        this.solrClient = solrClient;
    }

    /**
     * For the delete instruction, interprete body as query/queries instead of id/ids.
     */
    public boolean isDeleteByQuery() {
        return deleteByQuery;
    }

    public void setDeleteByQuery(boolean deleteByQuery) {
        this.deleteByQuery = deleteByQuery;
    }

    /**
     * Basic authenticate user
     */
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Password for authenticating
     */
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Enable SSL
     */
    public boolean isEnableSSL() {
        return enableSSL;
    }

    public void setEnableSSL(boolean enableSSL) {
        this.enableSSL = enableSSL;
    }

    /**
     * The certificate that can be used to access the solr host. It can be loaded by default from classpath, but you can
     * prefix with classpath:, file:, or http: to load the resource from different systems.
     */
    public String getCertificatePath() {
        return certificatePath;
    }

    public void setCertificatePath(String certificatePath) {
        this.certificatePath = certificatePath;
    }

    /**
     * The time in ms to wait before connection will time out.
     */
    public Long getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    /**
     * Use async request processing (when supported by the solr client)
     */
    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    /**
     * The time in ms to wait before the request will time out (former soTimeout).
     */
    public Long getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

}
