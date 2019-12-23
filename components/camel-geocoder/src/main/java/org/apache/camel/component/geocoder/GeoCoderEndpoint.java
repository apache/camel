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
package org.apache.camel.component.geocoder;

import java.net.InetSocketAddress;
import java.net.Proxy;

import com.google.maps.GeoApiContext;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.geocoder.http.AuthenticationMethod;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * The geocoder component is used for looking up geocodes (latitude and longitude) for a given address, or reverse lookup.
 */
@UriEndpoint(firstVersion = "2.12.0", scheme = "geocoder", title = "Geocoder", syntax = "geocoder:address:latlng", producerOnly = true, label = "api,location")
public class GeoCoderEndpoint extends DefaultEndpoint {

    @UriPath
    private String address;
    @UriPath
    private String latlng;
    @UriParam(defaultValue = "en")
    private String language = "en";
    @UriParam(label = "security", secret = true)
    private String clientId;
    @UriParam(label = "security", secret = true)
    private String clientKey;
    @UriParam(label = "security", secret = true)
    private String apiKey;
    @UriParam
    private boolean headersOnly;
    @UriParam(label = "proxy")
    private String proxyHost;
    @UriParam(label = "proxy")
    private Integer proxyPort;
    @UriParam(label = "proxy")
    private String proxyAuthMethod;
    @UriParam(label = "proxy")
    private String proxyAuthUsername;
    @UriParam(label = "proxy")
    private String proxyAuthPassword;
    @UriParam(label = "proxy")
    private String proxyAuthDomain;
    @UriParam(label = "proxy")
    private String proxyAuthHost;

    public GeoCoderEndpoint() {
    }

    public GeoCoderEndpoint(String uri, GeoCoderComponent component) {
        super(uri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new GeoCoderProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from this component");
    }

    public String getLanguage() {
        return language;
    }

    /**
     * The language to use.
     */
    public void setLanguage(String language) {
        this.language = language;
    }

    public String getAddress() {
        return address;
    }

    /**
     * The geo address which should be prefixed with <tt>address:</tt>
     */
    public void setAddress(String address) {
        this.address = address;
    }

    public String getLatlng() {
        return latlng;
    }

    /**
     * The geo latitude and longitude which should be prefixed with <tt>latlng:</tt>
     */
    public void setLatlng(String latlng) {
        this.latlng = latlng;
    }

    public boolean isHeadersOnly() {
        return headersOnly;
    }

    /**
     * Whether to only enrich the Exchange with headers, and leave the body as-is.
     */
    public void setHeadersOnly(boolean headersOnly) {
        this.headersOnly = headersOnly;
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * To use google premium with this client id
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientKey() {
        return clientKey;
    }

    /**
     * To use google premium with this client key
     */
    public void setClientKey(String clientKey) {
        this.clientKey = clientKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    /**
     * To use google apiKey
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * The proxy host name
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    /**
     * The proxy port number
     */
    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }


    public String getProxyAuthMethod() {
        return proxyAuthMethod;
    }

    /**
     * Authentication method for proxy, either as Basic, Digest or NTLM.
     */
    public void setProxyAuthMethod(String proxyAuthMethod) {
        this.proxyAuthMethod = proxyAuthMethod;
    }

    public String getProxyAuthUsername() {
        return proxyAuthUsername;
    }

    /**
     * Username for proxy authentication
     */
    public void setProxyAuthUsername(String proxyAuthUsername) {
        this.proxyAuthUsername = proxyAuthUsername;
    }

    public String getProxyAuthPassword() {
        return proxyAuthPassword;
    }

    /**
     * Password for proxy authentication
     */
    public void setProxyAuthPassword(String proxyAuthPassword) {
        this.proxyAuthPassword = proxyAuthPassword;
    }

    public String getProxyAuthDomain() {
        return proxyAuthDomain;
    }

    /**
     * Domain for proxy NTML authentication
     */
    public void setProxyAuthDomain(String proxyAuthDomain) {
        this.proxyAuthDomain = proxyAuthDomain;
    }

    public String getProxyAuthHost() {
        return proxyAuthHost;
    }

    /**
     * Optional host for proxy NTML authentication
     */
    public void setProxyAuthHost(String proxyAuthHost) {
        this.proxyAuthHost = proxyAuthHost;
    }

    GeoApiContext createGeoApiContext() {
        GeoApiContext.Builder builder = new GeoApiContext.Builder();
        if (clientId != null) {
            builder = builder.enterpriseCredentials(clientId, clientKey);
        } else {
            builder = builder.apiKey(getApiKey());
        }
        if (isProxyDefined()) {
            builder = builder.proxy(createProxy());
            if (isProxyAuthDefined()) {
                builder = configureProxyAuth(builder);
            }
        }
        return builder.build();
    }

    private GeoApiContext.Builder configureProxyAuth(GeoApiContext.Builder builder) {
        AuthenticationMethod auth = getCamelContext().getTypeConverter().convertTo(AuthenticationMethod.class, proxyAuthMethod);
        if (auth == AuthenticationMethod.Basic || auth == AuthenticationMethod.Digest) {
            builder = builder.proxyAuthentication(proxyAuthUsername, proxyAuthPassword);
        } else {
            throw new IllegalArgumentException("Unknown proxyAuthMethod " + proxyAuthMethod);
        }
        return builder;
    }

    private Proxy createProxy() {
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
    }

    private boolean isProxyDefined() {
        return proxyHost != null && proxyPort != null;
    }

    private boolean isProxyAuthDefined() {
        return proxyAuthMethod != null;
    }
}
