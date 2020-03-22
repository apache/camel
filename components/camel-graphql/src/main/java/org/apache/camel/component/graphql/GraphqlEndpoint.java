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
package org.apache.camel.component.graphql;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;

@UriEndpoint(firstVersion = "3.0.0", scheme = "graphql", title = "GraphQL", syntax = "graphql:httpUri", label = "api", producerOnly = true)
public class GraphqlEndpoint extends DefaultEndpoint {

    @UriPath
    @Metadata(required = true)
    private URI httpUri;
    @UriParam
    private String proxyHost;
    @UriParam(label = "security", secret = true)
    private String accessToken;
    @UriParam(label = "security", secret = true)
    private String username;
    @UriParam(label = "security", secret = true)
    private String password;
    @UriParam
    private String query;
    @UriParam
    private String queryFile;
    @UriParam
    private String operationName;
    @UriParam
    private JsonObject variables;

    private CloseableHttpClient httpClient;

    public GraphqlEndpoint(String uri, Component component) {
        super(uri, component);
    }

    @Override
    protected void doStop() throws Exception {
        HttpClientUtils.closeQuietly(this.httpClient);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new GraphqlProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages at this endpoint: " + getEndpointUri());
    }

    public CloseableHttpClient getHttpclient() {
        if (httpClient == null) {
            this.httpClient = createHttpClient();
        }
        return httpClient;
    }

    private CloseableHttpClient createHttpClient() {
        HttpClientBuilder httpClientBuilder = HttpClients.custom();
        if (proxyHost != null) {
            String[] parts = proxyHost.split(":");
            String hostname = parts[0];
            int port = Integer.parseInt(parts[1]);
            httpClientBuilder.setProxy(new HttpHost(hostname, port));
        }
        if (accessToken != null) {
            httpClientBuilder.setDefaultHeaders(
                    Arrays.asList(new BasicHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)));
        }
        if (username != null && password != null) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
            httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
        }
        return httpClientBuilder.build();
    }

    public URI getHttpUri() {
        return httpUri;
    }

    /**
     * The GraphQL server URI.
     */
    public void setHttpUri(URI httpUri) {
        this.httpUri = httpUri;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * The proxy host in the format "hostname:port".
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public String getAccessToken() {
        return accessToken;
    }

    /**
     * The access token sent in the Authorization header.
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getUsername() {
        return username;
    }

    /**
     * The username for Basic authentication.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * The password for Basic authentication.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getQuery() {
        if (query == null && queryFile != null) {
            try {
                query = IOHelper.loadText(ObjectHelper.loadResourceAsStream(queryFile));
            } catch (IOException e) {
                throw new RuntimeCamelException("Failed to read query file: " + queryFile, e);
            }
        }
        return query;
    }

    /**
     * The query text.
     */
    public void setQuery(String query) {
        this.query = query;
    }

    public String getQueryFile() {
        return queryFile;
    }

    /**
     * The query file name located in the classpath.
     */
    public void setQueryFile(String queryFile) {
        this.queryFile = queryFile;
    }

    public String getOperationName() {
        return operationName;
    }

    /**
     * The query or mutation name.
     */
    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public JsonObject getVariables() {
        return variables;
    }

    /**
     * The JsonObject instance containing the operation variables.
     */
    public void setVariables(JsonObject variables) {
        this.variables = variables;
    }

}
