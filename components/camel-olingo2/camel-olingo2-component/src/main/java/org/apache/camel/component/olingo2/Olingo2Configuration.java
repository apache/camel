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
package org.apache.camel.component.olingo2;

import java.util.Map;

import org.apache.camel.component.olingo2.internal.Olingo2ApiName;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.olingo.odata2.api.ep.EntityProviderReadProperties;
import org.apache.olingo.odata2.api.ep.EntityProviderWriteProperties;

/**
 * Component configuration for Olingo2 component.
 */
@UriParams
@Configurer(extended = true)
public class Olingo2Configuration {

    private static final String DEFAULT_CONTENT_TYPE = ContentType.APPLICATION_JSON.toString();
    private static final int DEFAULT_TIMEOUT = 30 * 1000;

    @UriPath
    @Metadata(required = true)
    private Olingo2ApiName apiName;
    @UriPath
    @Metadata(required = true)
    private String methodName;
    @UriParam
    private String serviceUri;
    @UriParam(defaultValue = "application/json;charset=utf-8")
    private String contentType = DEFAULT_CONTENT_TYPE;
    @UriParam
    private Map<String, String> httpHeaders;
    @UriParam
    private EntityProviderReadProperties entityProviderReadProperties;
    @UriParam
    private EntityProviderWriteProperties entityProviderWriteProperties;
    @UriParam(defaultValue = "" + DEFAULT_TIMEOUT)
    private int connectTimeout = DEFAULT_TIMEOUT;
    @UriParam(defaultValue = "" + DEFAULT_TIMEOUT)
    private int socketTimeout = DEFAULT_TIMEOUT;
    @UriParam
    private HttpHost proxy;
    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "advanced")
    private HttpAsyncClientBuilder httpAsyncClientBuilder;
    @UriParam(label = "advanced")
    private HttpClientBuilder httpClientBuilder;
    @UriParam
    private boolean filterAlreadySeen;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean splitResult = true;

    public Olingo2ApiName getApiName() {
        return apiName;
    }

    /**
     * What kind of operation to perform
     */
    public void setApiName(Olingo2ApiName apiName) {
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
     * Content-Type header value can be used to specify JSON or XML message format, defaults to
     * application/json;charset=utf-8
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

    public EntityProviderReadProperties getEntityProviderReadProperties() {
        return entityProviderReadProperties;
    }

    /**
     * Custom entity provider read properties applied to all read operations.
     */
    public void setEntityProviderReadProperties(EntityProviderReadProperties entityProviderReadProperties) {
        this.entityProviderReadProperties = entityProviderReadProperties;
    }

    public EntityProviderWriteProperties getEntityProviderWriteProperties() {
        return entityProviderWriteProperties;
    }

    /**
     * Custom entity provider write properties applied to create, update, patch, batch and merge operations. For
     * instance users can skip the Json object wrapper or enable content only mode when sending request data. A service
     * URI set in the properties will always be overwritten by the serviceUri configuration parameter. Please consider
     * to using the serviceUri configuration parameter instead of setting the respective write property here.
     */
    public void setEntityProviderWriteProperties(EntityProviderWriteProperties entityProviderWriteProperties) {
        this.entityProviderWriteProperties = entityProviderWriteProperties;
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
     * Custom HTTP async client builder for more complex HTTP client configuration, overrides connectionTimeout,
     * socketTimeout, proxy and sslContext. Note that a socketTimeout MUST be specified in the builder, otherwise OData
     * requests could block indefinitely
     */
    public void setHttpAsyncClientBuilder(HttpAsyncClientBuilder httpAsyncClientBuilder) {
        this.httpAsyncClientBuilder = httpAsyncClientBuilder;
    }

    public HttpClientBuilder getHttpClientBuilder() {
        return httpClientBuilder;
    }

    /**
     * Custom HTTP client builder for more complex HTTP client configuration, overrides connectionTimeout,
     * socketTimeout, proxy and sslContext. Note that a socketTimeout MUST be specified in the builder, otherwise OData
     * requests could block indefinitely
     */
    public void setHttpClientBuilder(HttpClientBuilder httpClientBuilder) {
        this.httpClientBuilder = httpClientBuilder;
    }

    /**
     * Filter flag for filtering out already seen results
     */
    public boolean isFilterAlreadySeen() {
        return filterAlreadySeen;
    }

    /**
     * Set this to true to filter out results that have already been communicated by this component.
     */
    public void setFilterAlreadySeen(boolean filterAlreadySeen) {
        this.filterAlreadySeen = filterAlreadySeen;
    }

    public boolean isSplitResult() {
        return splitResult;
    }

    /**
     * For endpoints that return an array or collection, a consumer endpoint will map every element to distinct
     * messages, unless splitResult is set to false.
     */
    public void setSplitResult(boolean splitResult) {
        this.splitResult = splitResult;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(serviceUri).append(contentType)
                .append(httpHeaders).append(connectTimeout)
                .append(socketTimeout).append(proxy)
                .append(entityProviderReadProperties).append(entityProviderWriteProperties)
                .append(filterAlreadySeen).append(splitResult)
                .append(sslContextParameters).append(httpAsyncClientBuilder).append(httpClientBuilder).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Olingo2Configuration) {
            Olingo2Configuration other = (Olingo2Configuration) obj;
            return connectTimeout == other.connectTimeout && filterAlreadySeen == other.filterAlreadySeen
                    && splitResult == other.splitResult && socketTimeout == other.socketTimeout && serviceUri == null
                    ? other.serviceUri == null
                    : serviceUri.equals(other.serviceUri) && contentType == null
                            ? other.contentType == null
                    : contentType.equals(other.contentType) && httpHeaders == null
                            ? other.httpHeaders == null
                    : httpHeaders.equals(other.httpHeaders) && entityProviderReadProperties == null
                            ? other.entityProviderReadProperties == null
                    : entityProviderReadProperties.equals(other.entityProviderReadProperties) && proxy == null
                            ? other.proxy == null
                    : proxy.equals(other.proxy) && entityProviderWriteProperties == null
                            ? other.entityProviderWriteProperties == null
                    : entityProviderWriteProperties.equals(other.entityProviderWriteProperties)
                            && sslContextParameters == null
                            ? other.sslContextParameters == null
                    : sslContextParameters.equals(other.sslContextParameters) && httpAsyncClientBuilder == null
                            ? other.httpAsyncClientBuilder == null
                    : httpAsyncClientBuilder.equals(other.httpAsyncClientBuilder) && httpClientBuilder == null
                            ? other.httpClientBuilder == null
                    : httpClientBuilder.equals(other.httpClientBuilder);
        }
        return false;
    }
}
