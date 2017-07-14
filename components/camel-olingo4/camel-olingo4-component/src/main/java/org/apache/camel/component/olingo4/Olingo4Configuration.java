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
package org.apache.camel.component.olingo4;

import java.util.Map;

import org.apache.camel.component.olingo4.internal.Olingo4ApiName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;

/**
 * Component configuration for Olingo4 component.
 */
@UriParams
public class Olingo4Configuration {

    private static final String DEFAULT_CONTENT_TYPE = ContentType.APPLICATION_JSON.toString();
    private static final int DEFAULT_TIMEOUT = 30 * 1000;

    @UriPath
    @Metadata(required = "true")
    private Olingo4ApiName apiName;
    @UriPath @Metadata(required = "true")
    private String methodName;
    @UriParam
    private String serviceUri;
    @UriParam(defaultValue = "application/json;charset=utf-8")
    private String contentType = DEFAULT_CONTENT_TYPE;
    @UriParam
    private Map<String, String> httpHeaders;
    @UriParam(defaultValue = "" + DEFAULT_TIMEOUT)
    private int connectTimeout = DEFAULT_TIMEOUT;
    @UriParam(defaultValue = "" + DEFAULT_TIMEOUT)
    private int socketTimeout = DEFAULT_TIMEOUT;
    @UriParam
    private HttpHost proxy;
    @UriParam
    private SSLContextParameters sslContextParameters;
    @UriParam
    private HttpAsyncClientBuilder httpAsyncClientBuilder;
    @UriParam
    private HttpClientBuilder httpClientBuilder;

    public Olingo4ApiName getApiName() {
        return apiName;
    }

    /**
     * What kind of operation to perform
     */
    public void setApiName(Olingo4ApiName apiName) {
        this.apiName = apiName;
    }

    public String getMethodName() {
        return methodName;
    }

    /**
     * What sub operation to use for the selected operation
     */
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getServiceUri() {
        return serviceUri;
    }

    /**
     * Target OData service base URI, e.g. http://services.odata.org/OData/OData.svc
     */
    public void setServiceUri(String serviceUri) {
        this.serviceUri = serviceUri;
    }

    public String getContentType() {
        return contentType;
    }

    /**
     * Content-Type header value can be used to specify JSON or XML message format, defaults to application/json;charset=utf-8
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Map<String, String> getHttpHeaders() {
        return httpHeaders;
    }

    /**
     * Custom HTTP headers to inject into every request, this could include OAuth tokens, etc.
     */
    public void setHttpHeaders(Map<String, String> httpHeaders) {
        this.httpHeaders = httpHeaders;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * HTTP connection creation timeout in milliseconds, defaults to 30,000 (30 seconds)
     */
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    /**
     * HTTP request timeout in milliseconds, defaults to 30,000 (30 seconds)
     */
    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public HttpHost getProxy() {
        return proxy;
    }

    /**
     * HTTP proxy server configuration
     */
    public void setProxy(HttpHost proxy) {
        this.proxy = proxy;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * To configure security using SSLContextParameters
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public HttpAsyncClientBuilder getHttpAsyncClientBuilder() {
        return httpAsyncClientBuilder;
    }

    /**
     * Custom HTTP async client builder for more complex HTTP client configuration, overrides connectionTimeout, socketTimeout, proxy and sslContext.
     * Note that a socketTimeout MUST be specified in the builder, otherwise OData requests could block indefinitely
     */
    public void setHttpAsyncClientBuilder(HttpAsyncClientBuilder httpAsyncClientBuilder) {
        this.httpAsyncClientBuilder = httpAsyncClientBuilder;
    }

    public HttpClientBuilder getHttpClientBuilder() {
        return httpClientBuilder;
    }

    /**
     * Custom HTTP client builder for more complex HTTP client configuration, overrides connectionTimeout, socketTimeout, proxy and sslContext.
     * Note that a socketTimeout MUST be specified in the builder, otherwise OData requests could block indefinitely
     */
    public void setHttpClientBuilder(HttpClientBuilder httpClientBuilder) {
        this.httpClientBuilder = httpClientBuilder;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(serviceUri)
            .append(contentType)
            .append(httpHeaders)
            .append(connectTimeout)
            .append(socketTimeout)
            .append(proxy)
            .append(sslContextParameters)
            .append(httpAsyncClientBuilder)
            .append(httpClientBuilder)
            .hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Olingo4Configuration) {
            Olingo4Configuration other = (Olingo4Configuration) obj;
            return serviceUri == null ? other.serviceUri == null : serviceUri.equals(other.serviceUri)
                && contentType == null ? other.contentType == null : contentType.equals(other.contentType)
                && httpHeaders == null ? other.httpHeaders == null : httpHeaders.equals(other.httpHeaders)
                && connectTimeout == other.connectTimeout
                && socketTimeout == other.socketTimeout
                && proxy == null ? other.proxy == null : proxy.equals(other.proxy)
                && sslContextParameters == null ? other.sslContextParameters == null : sslContextParameters.equals(other.sslContextParameters)
                && httpAsyncClientBuilder == null ? other.httpAsyncClientBuilder == null
                : httpAsyncClientBuilder.equals(other.httpAsyncClientBuilder)
                && httpClientBuilder == null ? other.httpClientBuilder == null : httpClientBuilder.equals(other.httpClientBuilder);
        }
        return false;
    }
}
