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
package org.apache.camel.component.olingo2;

import java.util.Map;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;

/**
 * Component configuration for Olingo2 component.
 */
@UriParams
public class Olingo2Configuration {

    private static final String DEFAULT_CONTENT_TYPE = ContentType.APPLICATION_JSON.toString();
    private static final int DEFAULT_TIMEOUT = 30 * 1000;

    @UriParam
    private String serviceUri;

    @UriParam
    private String contentType = DEFAULT_CONTENT_TYPE;

    @UriParam
    private Map<String, String> httpHeaders;

    // common connection parameters for convenience
    @UriParam
    private int connectTimeout = DEFAULT_TIMEOUT;

    @UriParam
    private int socketTimeout = DEFAULT_TIMEOUT;

    @UriParam
    private HttpHost proxy;

    @UriParam
    private SSLContextParameters sslContextParameters;

    // for more complex configuration, use a client builder
    @UriParam
    private HttpAsyncClientBuilder httpAsyncClientBuilder;

    public String getServiceUri() {
        return serviceUri;
    }

    public void setServiceUri(String serviceUri) {
        this.serviceUri = serviceUri;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Map<String, String> getHttpHeaders() {
        return httpHeaders;
    }

    public void setHttpHeaders(Map<String, String> httpHeaders) {
        this.httpHeaders = httpHeaders;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public HttpHost getProxy() {
        return proxy;
    }

    public void setProxy(HttpHost proxy) {
        this.proxy = proxy;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public HttpAsyncClientBuilder getHttpAsyncClientBuilder() {
        return httpAsyncClientBuilder;
    }

    public void setHttpAsyncClientBuilder(HttpAsyncClientBuilder httpAsyncClientBuilder) {
        this.httpAsyncClientBuilder = httpAsyncClientBuilder;
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
            .hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Olingo2Configuration) {
            Olingo2Configuration other = (Olingo2Configuration) obj;
            return serviceUri == null ? other.serviceUri == null : serviceUri.equals(other.serviceUri)
                && contentType == null ? other.contentType == null : contentType.equals(other.contentType)
                && httpHeaders == null ? other.httpHeaders == null : httpHeaders.equals(other.httpHeaders)
                && connectTimeout == other.connectTimeout
                && socketTimeout == other.socketTimeout
                && proxy == null ? other.proxy == null : proxy.equals(other.proxy)
                && sslContextParameters == null ? other.sslContextParameters == null : sslContextParameters.equals(other.sslContextParameters)
                && httpAsyncClientBuilder == null ? other.httpAsyncClientBuilder == null
                : httpAsyncClientBuilder.equals(other.httpAsyncClientBuilder);
        }
        return false;
    }
}
