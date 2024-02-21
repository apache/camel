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
package org.apache.camel.component.vertx.http;

import java.net.URI;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.ClientOptionsBase;
import io.vertx.core.net.ProxyType;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.spi.CookieStore;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.jsse.SSLContextParameters;

@UriParams
public class VertxHttpConfiguration {

    @UriPath(name = "httpUri")
    @Metadata(required = true)
    private URI httpUri;
    @UriParam(label = "producer",
              enums = "OPTIONS,GET,HEAD,POST,PUT,DELETE,TRACE,CONNECT,PATCH,PROPFIND,PROPPATCH,MKCOL,COPY,MOVE,LOCK,UNLOCK,MKCALENDAR,VERSION_CONTROL,REPORT,CHECKIN,CHECKOUT,UNCHECKOUT,MKWORKSPACE,UPDATE,LABEL,MERGE,BASELINE_CONTROL,MKACTIVITY,ORDERPATCH,ACL,SEARCH")
    private HttpMethod httpMethod;
    @UriParam(label = "producer", defaultValue = "-1")
    private long timeout = -1;
    @UriParam(label = "producer", defaultValue = "60000")
    private int connectTimeout = ClientOptionsBase.DEFAULT_CONNECT_TIMEOUT;
    @UriParam(label = "producer", defaultValue = "VertxHttpHeaderFilterStrategy")
    private HeaderFilterStrategy headerFilterStrategy = new VertxHttpHeaderFilterStrategy();
    @UriParam(label = "producer")
    private VertxHttpBinding vertxHttpBinding;
    @UriParam(label = "producer", defaultValue = "true")
    private boolean throwExceptionOnFailure = true;
    @UriParam(label = "producer", defaultValue = "false")
    private boolean transferException;
    @UriParam(label = "producer", defaultValue = "200-299")
    private String okStatusCodeRange = "200-299";
    @UriParam(label = "producer", defaultValue = "false")
    private boolean sessionManagement;
    @UriParam(label = "producer", defaultValue = "InMemoryCookieStore")
    private CookieStore cookieStore;
    @UriParam(label = "producer", defaultValue = "false")
    private boolean useCompression;
    @UriParam(label = "producer", defaultValue = "true")
    private boolean responsePayloadAsByteArray = true;
    @UriParam(label = "security")
    private String basicAuthUsername;
    @UriParam(label = "security")
    private String basicAuthPassword;
    @UriParam(label = "security")
    private String bearerToken;
    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "proxy")
    private String proxyHost;
    @UriParam(label = "proxy")
    private Integer proxyPort;
    @UriParam(label = "proxy", enums = "HTTP,SOCKS4,SOCKS5")
    private ProxyType proxyType;
    @UriParam(label = "proxy")
    private String proxyUsername;
    @UriParam(label = "proxy")
    private String proxyPassword;
    @UriParam(label = "producer")
    private WebClientOptions webClientOptions;

    /**
     * The HTTP URI to connect to
     */
    public void setHttpUri(URI httpUri) {
        this.httpUri = httpUri;
    }

    public URI getHttpUri() {
        return httpUri;
    }

    /**
     * The HTTP method to use. The HttpMethod header cannot override this option if set
     */
    public void setHttpMethod(HttpMethod httpMethod) {
        this.httpMethod = httpMethod;
    }

    /**
     * The HTTP method to use. The HttpMethod header cannot override this option if set
     */
    public void setHttpMethod(String httpMethod) {
        this.httpMethod = HttpMethod.valueOf(httpMethod);
    }

    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    /**
     * The amount of time in milliseconds after which if the request does not return any data within the timeout period
     * a TimeoutException fails the request.
     * <p/>
     * Setting zero or a negative value disables the timeout.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public long getTimeout() {
        return timeout;
    }

    /**
     * The amount of time in milliseconds until a connection is established. A timeout value of zero is interpreted as
     * an infinite timeout.
     */
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * A custom org.apache.camel.spi.HeaderFilterStrategy to filter header to and from Camel message.
     */
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * A custom VertxHttpBinding which can control how to bind between Vert.x and Camel.
     */
    public void setVertxHttpBinding(VertxHttpBinding vertxHttpBinding) {
        this.vertxHttpBinding = vertxHttpBinding;
    }

    public VertxHttpBinding getVertxHttpBinding() {
        return vertxHttpBinding;
    }

    /**
     * Disable throwing HttpOperationFailedException in case of failed responses from the remote server
     */
    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    /**
     * If enabled and an Exchange failed processing on the consumer side, and if the caused Exception was sent back
     * serialized in the response as a application/x-java-serialized-object content type. On the producer side the
     * exception will be deserialized and thrown as is, instead of HttpOperationFailedException. The caused exception is
     * required to be serialized.
     * <p/>
     * This is by default turned off. If you enable this then be aware that Camel will deserialize the incoming data
     * from the request to a Java object, which can be a potential security risk.
     */
    public void setTransferException(boolean transferException) {
        this.transferException = transferException;
    }

    public boolean isTransferException() {
        return transferException;
    }

    /**
     * The status codes which are considered a success response. The values are inclusive. Multiple ranges can be
     * defined, separated by comma, e.g. 200-204,209,301-304. Each range must be a single number or from-to with the
     * dash included
     */
    public void setOkStatusCodeRange(String okStatusCodeRange) {
        this.okStatusCodeRange = okStatusCodeRange;
    }

    public String getOkStatusCodeRange() {
        return okStatusCodeRange;
    }

    /**
     * Enables session management via WebClientSession. By default the client is configured to use an in-memory
     * CookieStore. The cookieStore option can be used to override this
     */
    public void setSessionManagement(boolean sessionManagement) {
        this.sessionManagement = sessionManagement;
    }

    public boolean isSessionManagement() {
        return sessionManagement;
    }

    /**
     * A custom CookieStore to use when session management is enabled. If this option is not set then an in-memory
     * CookieStore is used
     */
    public void setCookieStore(CookieStore cookieStore) {
        this.cookieStore = cookieStore;
    }

    public CookieStore getCookieStore() {
        return cookieStore;
    }

    /**
     * Set whether compression is enabled to handled compressed (E.g gzipped) responses
     */
    public void setUseCompression(boolean useCompression) {
        this.useCompression = useCompression;
    }

    public boolean isUseCompression() {
        return useCompression;
    }

    public boolean isResponsePayloadAsByteArray() {
        return responsePayloadAsByteArray;
    }

    /**
     * Whether the response body should be byte[] or as io.vertx.core.buffer.Buffer
     */
    public void setResponsePayloadAsByteArray(boolean responsePayloadAsByteArray) {
        this.responsePayloadAsByteArray = responsePayloadAsByteArray;
    }

    /**
     * The user name to use for basic authentication
     */
    public void setBasicAuthUsername(String basicAuthUsername) {
        this.basicAuthUsername = basicAuthUsername;
    }

    public String getBasicAuthUsername() {
        return basicAuthUsername;
    }

    /**
     * The password to use for basic authentication
     */
    public void setBasicAuthPassword(String basicAuthPassword) {
        this.basicAuthPassword = basicAuthPassword;
    }

    public String getBasicAuthPassword() {
        return basicAuthPassword;
    }

    /**
     * The bearer token to use for bearer token authentication
     */
    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    public String getBearerToken() {
        return bearerToken;
    }

    /**
     * The proxy server host address
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * The proxy server port
     */
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * The proxy server username if authentication is required
     */
    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
    }

    public String getProxyUsername() {
        return proxyUsername;
    }

    /**
     * The proxy server password if authentication is required
     */
    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    /**
     * The proxy server type
     */
    public void setProxyType(ProxyType proxyType) {
        this.proxyType = proxyType;
    }

    public ProxyType getProxyType() {
        return proxyType;
    }

    /**
     * Sets customized options for configuring the Vert.x WebClient
     */
    public void setWebClientOptions(WebClientOptions webClientOptions) {
        this.webClientOptions = webClientOptions;
    }

    public WebClientOptions getWebClientOptions() {
        return webClientOptions;
    }

    /**
     * To configure security using SSLContextParameters
     */
    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }
}
