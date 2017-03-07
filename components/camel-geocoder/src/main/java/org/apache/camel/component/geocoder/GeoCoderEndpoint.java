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
package org.apache.camel.component.geocoder;

import java.security.InvalidKeyException;

import com.google.code.geocoder.AdvancedGeoCoder;
import com.google.code.geocoder.Geocoder;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.geocoder.http.AuthenticationHttpClientConfigurer;
import org.apache.camel.component.geocoder.http.AuthenticationMethod;
import org.apache.camel.component.geocoder.http.CompositeHttpConfigurer;
import org.apache.camel.component.geocoder.http.HttpClientConfigurer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;

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
    @UriParam
    private String clientId;
    @UriParam
    private String clientKey;
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
    @UriParam(label = "advanced")
    private HttpClientConfigurer httpClientConfigurer;
    @UriParam(label = "advanced")
    private HttpConnectionManager httpConnectionManager;

    public GeoCoderEndpoint() {
    }

    public GeoCoderEndpoint(String uri, GeoCoderComponent component) {
        super(uri, component);
    }

    public Producer createProducer() throws Exception {
        return new GeoCoderProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from this component");
    }

    public boolean isSingleton() {
        return true;
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

    public HttpClientConfigurer getHttpClientConfigurer() {
        return httpClientConfigurer;
    }

    /**
     * Register a custom configuration strategy for new {@link HttpClient} instances
     * created by producers or consumers such as to configure authentication mechanisms etc
     *
     * @param httpClientConfigurer the strategy for configuring new {@link HttpClient} instances
     */
    public void setHttpClientConfigurer(HttpClientConfigurer httpClientConfigurer) {
        this.httpClientConfigurer = httpClientConfigurer;
    }

    public HttpConnectionManager getHttpConnectionManager() {
        return httpConnectionManager;
    }

    /**
     * To use a custom HttpConnectionManager to manage connections
     */
    public void setHttpConnectionManager(HttpConnectionManager httpConnectionManager) {
        this.httpConnectionManager = httpConnectionManager;
    }

    Geocoder createGeocoder() throws InvalidKeyException {
        HttpConnectionManager connectionManager = this.httpConnectionManager;
        if (connectionManager == null) {
            connectionManager = new MultiThreadedHttpConnectionManager();
        }

        HttpClient httpClient = new HttpClient(connectionManager);
        if (proxyHost != null && proxyPort != null) {
            httpClient.getHostConfiguration().setProxy(proxyHost, proxyPort);
        }

        // validate that if proxy auth username is given then the proxy auth method is also provided
        if (proxyAuthUsername != null && proxyAuthMethod == null) {
            throw new IllegalArgumentException("Option proxyAuthMethod must be provided to use proxy authentication");
        }

        CompositeHttpConfigurer configurer = new CompositeHttpConfigurer();
        if (proxyAuthMethod != null) {
            configureProxyAuth(configurer, proxyAuthMethod, proxyAuthUsername, proxyAuthPassword, proxyAuthDomain, proxyAuthHost);
        }
        if (httpClientConfigurer != null) {
            configurer.addConfigurer(httpClientConfigurer);
        }

        configurer.configureHttpClient(httpClient);

        Geocoder geocoder;
        if (clientId != null) {
            geocoder = new AdvancedGeoCoder(httpClient, clientId, clientKey);
        } else {
            geocoder = new AdvancedGeoCoder(httpClient);
        }

        return geocoder;
    }

    /**
     * Configures the proxy authentication method to be used
     *
     * @return configurer to used
     */
    protected CompositeHttpConfigurer configureProxyAuth(CompositeHttpConfigurer configurer,
            String authMethod, String username, String password, String domain, String host) {

        // no proxy auth is in use
        if (username == null && authMethod == null) {
            return configurer;
        }

        // validate mandatory options given
        if (username != null && authMethod == null) {
            throw new IllegalArgumentException("Option proxyAuthMethod must be provided to use proxy authentication");
        }

        ObjectHelper.notNull(authMethod, "proxyAuthMethod");
        ObjectHelper.notNull(username, "proxyAuthUsername");
        ObjectHelper.notNull(password, "proxyAuthPassword");

        AuthenticationMethod auth = getCamelContext().getTypeConverter().convertTo(AuthenticationMethod.class, authMethod);

        if (auth == AuthenticationMethod.Basic || auth == AuthenticationMethod.Digest) {
            configurer.addConfigurer(AuthenticationHttpClientConfigurer.basicAutenticationConfigurer(true, username, password));
            return configurer;
        } else if (auth == AuthenticationMethod.NTLM) {
            // domain is mandatory for NTML
            ObjectHelper.notNull(domain, "proxyAuthDomain");
            configurer.addConfigurer(AuthenticationHttpClientConfigurer.ntlmAutenticationConfigurer(true, username, password, domain, host));
            return configurer;
        }

        throw new IllegalArgumentException("Unknown proxyAuthMethod " + authMethod);
    }
}
