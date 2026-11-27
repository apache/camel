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
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;
import org.apache.solr.client.solrj.SolrClient;

@UriParams
public class SolrConfiguration {

    @UriPath(description = "The host name of the Solr server. Set it to default to use the host option of the component, or"
                           + " localhost when that is not set.")
    @Metadata(required = true)
    private String host;
    @UriPath(defaultValue = "" + SolrConstants.DEFAULT_PORT,
             description = "The port of the Solr server. If not set, the port option of the component is used, or 8983 when"
                           + " that is not set.")
    private int port = -1;
    @UriPath(defaultValue = SolrConstants.DEFAULT_BASE_PATH,
             description = "The base path of the Solr server. A path that starts with /solr/ sets the collection and the request"
                           + " handler instead: in /solr/products/update, products is the collection and /update the request"
                           + " handler.")
    private String basePath;
    @UriParam(label = "security", security = "secret",
              description = "The user name for HTTP basic authentication with Solr, used together with the password option by"
                            + " the client that Camel creates. Not used when the solrClient option is set.")
    private String username;
    @UriParam(label = "security", security = "secret",
              description = "The password for HTTP basic authentication with Solr, used together with the username option by"
                            + " the client that Camel creates. Not used when the solrClient option is set.")
    private String password;
    @UriParam(description = "The operation that the producer performs. The CamelSolrOperation header replaces this option, and a"
                            + " body that is a SolrJ request, such as a SolrQuery or an UpdateRequest, replaces both. If none is"
                            + " set, the body and the headers decide, for example a Map body inserts a document.")
    private SolrOperation operation;
    @UriParam(description = "Whether the producer adds the commit=true parameter to each request, so that Solr commits an insert"
                            + " or a delete at once.")
    private boolean autoCommit;
    @UriParam(description = "The maximum number of documents that a search returns, which is the rows parameter of Solr (10 by"
                            + " default in Solr). The CamelSolrSize header replaces this option. Not used for a QueryRequest"
                            + " body.")
    private Integer size;
    @UriParam(description = "The index of the first document that a search returns, which is the start parameter of Solr (0 by"
                            + " default in Solr). The CamelSolrFrom header replaces this option. Not used for a QueryRequest"
                            + " body.")
    private Integer from;
    @UriParam(description = "The name of the collection that the requests go to. The CamelSolrCollection header replaces this"
                            + " option. If not set, the defaultCollection option of the component is used, or the default"
                            + " collection of the solrClient.")
    private String collection;
    @UriParam(description = "The path of the request handler, for example /update/json. An insert or a delete sends the update"
                            + " request to it (/update when not set), and a search sends it as the qt parameter. The"
                            + " CamelSolrRequestHandler header replaces this option.")
    private String requestHandler;
    @UriParam(defaultValue = "false",
              description = "Whether a DELETE takes the body as a query, or a collection of queries, instead of the id of the"
                            + " documents to delete. The CamelSolrDeleteByQuery header replaces this option.")
    private boolean deleteByQuery;
    @UriParam(label = "security",
              description = "Whether the client that Camel creates connects to Solr with HTTPS instead of HTTP.")
    private boolean enableSSL;
    @UriParam(label = "security",
              description = "The X.509 certificate of the certificate authority that the client that Camel creates trusts for"
                            + " HTTPS. It is loaded from the classpath by default, or with the classpath:, file: or http:"
                            + " prefix. Not used when the sslContextParameters option is set.")
    @Metadata(supportFileReference = true)
    private String certificatePath;
    @UriParam(label = "security",
              description = "The TLS configuration (key store, trust store, protocols, cipher suites, named groups) of the client"
                            + " that Camel creates. It takes precedence over the certificatePath option. If not set, the global"
                            + " SSL context parameters are used when the useGlobalSslContextParameters option of the component"
                            + " is enabled.")
    private SSLContextParameters sslContextParameters;
    @UriParam(description = "The time in milliseconds that the client that Camel creates waits for the response to a request. If"
                            + " not set, the requestTimeout option of the component is used.")
    private Long requestTimeout;
    @UriParam(description = "The time in milliseconds that the client that Camel creates waits to connect to Solr. If not set,"
                            + " the connectionTimeout option of the component is used.")
    private Long connectionTimeout;
    @UriParam(defaultValue = "true",
              description = "Whether the producer sends the requests asynchronously. This needs a client that extends"
                            + " HttpSolrClientBase, such as the HttpJdkSolrClient that Camel creates; with another client the"
                            + " producer sends the requests synchronously.")
    private boolean async = true;
    @UriParam(description = "To use a custom org.apache.solr.client.solrj.SolrClient. When set, the host, port, basePath,"
                            + " username, password, enableSSL, certificatePath, sslContextParameters, connectionTimeout and"
                            + " requestTimeout options do not configure the client. If not set, Camel creates an"
                            + " HttpJdkSolrClient for the endpoint. Camel does not close a client that is set.")
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

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public SolrOperation getOperation() {
        return operation;
    }

    public void setOperation(SolrOperation operation) {
        this.operation = operation;
    }

    public boolean isAutoCommit() {
        return autoCommit;
    }

    public void setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    public Integer getFrom() {
        return from;
    }

    public void setFrom(Integer from) {
        this.from = from;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getRequestHandler() {
        return requestHandler;
    }

    public void setRequestHandler(String requestHandler) {
        this.requestHandler = requestHandler;
    }

    public SolrClient getSolrClient() {
        return solrClient;
    }

    public void setSolrClient(SolrClient solrClient) {
        this.solrClient = solrClient;
    }

    public boolean isDeleteByQuery() {
        return deleteByQuery;
    }

    public void setDeleteByQuery(boolean deleteByQuery) {
        this.deleteByQuery = deleteByQuery;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEnableSSL() {
        return enableSSL;
    }

    public void setEnableSSL(boolean enableSSL) {
        this.enableSSL = enableSSL;
    }

    public String getCertificatePath() {
        return certificatePath;
    }

    public void setCertificatePath(String certificatePath) {
        this.certificatePath = certificatePath;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public Long getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public Long getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

}
