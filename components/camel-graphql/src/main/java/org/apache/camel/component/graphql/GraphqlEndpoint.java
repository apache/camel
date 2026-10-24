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
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.http.HttpClientConfigurer;
import org.apache.camel.http.base.HttpHeaderFilterStrategy;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.net.URIAuthority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Send GraphQL queries and mutations to external systems.
 */
@UriEndpoint(firstVersion = "3.0.0", scheme = "graphql", title = "GraphQL", syntax = "graphql:httpUri",
             category = { Category.API }, producerOnly = true, lenientProperties = true)
@BodyTypes(producerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  dependsOn = { "query", "queryFile", "queryHeader" },
                                  description = "The query, when none of query, queryFile and queryHeader is set: text, or"
                                                + " bytes, a stream or a file of it. A Map is not a query. With one of them the"
                                                + " body is not sent, except that an org.apache.camel.util.json.JsonObject body"
                                                + " is the variables when neither variables nor variablesHeader is set"),
           producerOut = @BodyType(value = { Carrier.TEXT, Carrier.NULL },
                                   description = "The response as text, the JSON with the data and errors of GraphQL: unmarshal"
                                                 + " it to read them. Errors in the response do not fail the exchange; a status"
                                                 + " outside 2xx does, unless throwExceptionOnFailure=false, which keeps the"
                                                 + " response as text. null for a response without a body"))
public class GraphqlEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    private static final Logger LOG = LoggerFactory.getLogger(GraphqlEndpoint.class);

    @UriPath
    @Metadata(required = true)
    private URI httpUri;
    @UriParam
    private String proxyHost;
    @UriParam(label = "security", security = "secret")
    private String accessToken;
    @UriParam(label = "security", security = "secret")
    private String username;
    @UriParam(label = "security", security = "secret")
    private String password;
    @UriParam(label = "security", defaultValue = "Bearer")
    private String jwtAuthorizationType;
    @UriParam
    private String query;
    @UriParam
    private String queryFile;
    @UriParam
    private String operationName;
    @UriParam
    private JsonObject variables;
    @UriParam
    private String variablesHeader;
    @UriParam
    private String queryHeader;
    @UriParam(label = "advanced")
    private HttpClient httpClient;
    @UriParam(label = "producer", defaultValue = "true",
              description = "Option to disable throwing the HttpOperationFailedException in case of failed responses from the"
                            + " remote server. This allows you to get all responses regardless of the HTTP status code.")
    private boolean throwExceptionOnFailure = true;
    @UriParam(label = "common,advanced",
              description = "To use a custom HeaderFilterStrategy to filter header to and from Camel message.")
    private HeaderFilterStrategy headerFilterStrategy;

    public GraphqlEndpoint(String uri, Component component) {
        super(uri, component);
    }

    @Override
    public String getServiceUrl() {
        if (httpUri != null) {
            return httpUri.toString();
        }
        return null;
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (username != null) {
            return Map.of("username", username);
        }
        return null;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (headerFilterStrategy == null) {
            headerFilterStrategy = new HttpHeaderFilterStrategy();
        }
    }

    @Override
    public String getServiceProtocol() {
        return "rest";
    }

    @Override
    public Producer createProducer() throws Exception {
        return new GraphqlProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages at this endpoint: " + getEndpointUri());
    }

    HttpClientConfigurer createHttpClientConfigurer() {
        return new AuthClientConfigurer();
    }

    private class AuthClientConfigurer implements HttpClientConfigurer {
        @Override
        public void configureHttpClient(HttpClientBuilder httpClientBuilder) {
            if (proxyHost != null) {
                String[] parts = proxyHost.split(":");
                String hostname = parts[0];
                int port = Integer.parseInt(parts[1]);
                httpClientBuilder.setProxy(new HttpHost(hostname, port));
            }
            boolean basicAuth = username != null && password != null;
            if (accessToken == null && !basicAuth) {
                return;
            }
            // the credentials belong to the authority of httpUri, as camel-http does for its own (CAMEL-24452)
            String scheme = httpUri.getScheme();
            URIAuthority authority = httpUriAuthority();
            if (scheme == null || authority == null) {
                LOG.warn("Cannot read the scheme, host and port of httpUri on {}: the access token and the basic "
                         + "credentials are not sent",
                        GraphqlEndpoint.this);
                return;
            }
            String host = authority.getHostName();
            int port = effectivePort(scheme, authority.getPort());
            if (accessToken != null) {
                String authType = "Bearer";
                if (jwtAuthorizationType != null) {
                    authType = jwtAuthorizationType;
                }
                Header authorization = new BasicHeader(HttpHeaders.AUTHORIZATION, authType + " " + accessToken);
                // added per request rather than as a default header, which would go with every request the client
                // executes
                httpClientBuilder.addRequestInterceptorLast((request, entity, context) -> {
                    if (!request.containsHeader(HttpHeaders.AUTHORIZATION) && isAuthority(request, scheme, host, port)) {
                        request.addHeader(authorization);
                    }
                });
            }
            if (basicAuth) {
                CredentialsStore credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(
                        new AuthScope(scheme, host, port, null, null),
                        new UsernamePasswordCredentials(username, password.toCharArray()));
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            }
        }
    }

    private static boolean isAuthority(HttpRequest request, String scheme, String host, int port) {
        URIAuthority target = request.getAuthority();
        return target != null
                && scheme.equalsIgnoreCase(request.getScheme())
                && host.equalsIgnoreCase(target.getHostName())
                && port == effectivePort(scheme, target.getPort());
    }

    /**
     * The authority of httpUri as HttpClient reads it, which also accepts a host name that java.net.URI does not parse,
     * such as one with an underscore.
     */
    private URIAuthority httpUriAuthority() {
        if (httpUri.getHost() != null) {
            return new URIAuthority(httpUri.getHost(), httpUri.getPort());
        }
        if (httpUri.getRawAuthority() == null) {
            return null;
        }
        try {
            return URIAuthority.create(httpUri.getRawAuthority());
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static int effectivePort(String scheme, int port) {
        if (port >= 0) {
            return port;
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return 80;
        }
        return -1;
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
            InputStream is = null;
            try {
                is = ResourceHelper.resolveResourceAsInputStream(getCamelContext(), queryFile);
                query = IOHelper.loadText(is);
            } catch (IOException e) {
                throw new RuntimeCamelException("Failed to read query file: " + queryFile, e);
            } finally {
                IOHelper.close(is);
            }
        }
        return query;
    }

    /**
     * The JWT Authorization type. Default is Bearer.
     */
    public void setJwtAuthorizationType(String jwtAuthorizationType) {
        this.jwtAuthorizationType = jwtAuthorizationType;
    }

    public String getJwtAuthorizationType() {
        return this.jwtAuthorizationType;
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
     * The query file name located in the classpath (or use file: to load from file system).
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

    public String getVariablesHeader() {
        return variablesHeader;
    }

    /**
     * The name of a header containing a JsonObject instance containing the operation variables.
     */
    public void setVariablesHeader(String variablesHeader) {
        this.variablesHeader = variablesHeader;
    }

    public String getQueryHeader() {
        return queryHeader;
    }

    /**
     * The name of a header containing the GraphQL query.
     */
    public void setQueryHeader(String queryHeader) {
        this.queryHeader = queryHeader;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * To use a custom pre-existing Http Client. Beware that when using this, then other configurations such as proxy,
     * access token, is not applied and all this must be pre-configured on the Http Client.
     */
    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    @Override
    public boolean isLenientProperties() {
        return true;
    }
}
