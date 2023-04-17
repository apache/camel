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
package org.apache.camel.component.salesforce.codegen;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.component.salesforce.SalesforceHttpClient;
import org.apache.camel.component.salesforce.SalesforceLoginConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.utils.SecurityUtils;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.camel.component.salesforce.internal.client.DefaultRestClient;
import org.apache.camel.component.salesforce.internal.client.PubSubApiClient;
import org.apache.camel.component.salesforce.internal.client.RestClient;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.StringHelper;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.Origin;
import org.eclipse.jetty.client.ProxyConfiguration;
import org.eclipse.jetty.client.Socks4Proxy;
import org.eclipse.jetty.client.api.Authentication;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.client.util.DigestAuthentication;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;

/**
 * Base class for any Salesforce Execution.
 */
public abstract class AbstractSalesforceExecution {

    // default connect and call timeout
    private static final int DEFAULT_TIMEOUT = 60000;

    /**
     * Salesforce client id.
     */
    String clientId;

    /**
     * Salesforce client secret.
     */
    String clientSecret;

    /**
     * HTTP client properties.
     */
    Map<String, Object> httpClientProperties;

    /**
     * Proxy authentication URI.
     */
    String httpProxyAuthUri;

    /**
     * Addresses to NOT Proxy.
     */
    Set<String> httpProxyExcludedAddresses;

    /**
     * HTTP Proxy host.
     */
    String httpProxyHost;

    /**
     * Addresses to Proxy.
     */
    Set<String> httpProxyIncludedAddresses;

    /**
     * Proxy authentication password.
     */
    String httpProxyPassword;

    /**
     * HTTP Proxy port.
     */
    Integer httpProxyPort;

    /**
     * Proxy authentication realm.
     */
    String httpProxyRealm;

    /**
     * Proxy uses Digest authentication.
     */
    boolean httpProxyUseDigestAuth;

    /**
     * Proxy authentication username.
     */
    String httpProxyUsername;

    /**
     * Is HTTP Proxy secure, i.e. using secure sockets, true by default.
     */
    boolean isHttpProxySecure = true;

    /**
     * Is it a SOCKS4 Proxy?
     */
    boolean isHttpProxySocks4;

    /**
     * Salesforce login URL, defaults to https://login.salesforce.com.
     */
    String loginUrl;

    /**
     * Salesforce password.
     */
    String password;

    /**
     * SSL Context parameters.
     */
    SSLContextParameters sslContextParameters;

    /**
     * Salesforce username.
     */
    String userName;

    /**
     * Salesforce API version.
     */
    String version;

    private long responseTimeout;

    private SalesforceHttpClient httpClient;
    private SalesforceSession session;
    private RestClient restClient;
    private PubSubApiClient pubSubApiClient;
    private String pubSubHost;
    private int pubSubPort;

    public final void execute() throws Exception {
        setup();
        login();
        try {
            executeWithClient();
        } finally {
            disconnectFromSalesforce(restClient);
        }
    }

    public long getResponseTimeout() {
        return responseTimeout;
    }

    private void login() {
        try {
            httpClient = createHttpClient();

            // connect to Salesforce
            getLog().info("Logging in to Salesforce");
            session = httpClient.getSession();
            try {
                session.login(null);

            } catch (final SalesforceException e) {
                final String msg = "Salesforce login error " + e.getMessage();
                throw new RuntimeException(msg, e);
            }
            getLog().info("Salesforce login successful");
        } catch (final Exception e) {
            final String msg = "Error connecting to Salesforce: " + e.getMessage();
            ServiceHelper.stopAndShutdownServices(session, httpClient);
            throw new RuntimeException(msg, e);
        }
    }

    protected RestClient getRestClient() {
        if (restClient != null) {
            return restClient;
        }
        try {
            login();
            restClient = new DefaultRestClient(httpClient, version, session, new SalesforceLoginConfig());
            // remember to start the active client object
            ((DefaultRestClient) restClient).start();

            return restClient;
        } catch (final Exception e) {
            final String msg = "Error connecting to Salesforce: " + e.getMessage();
            disconnectFromSalesforce(restClient);
            throw new RuntimeException(msg, e);
        }
    }

    protected PubSubApiClient getPubSubApiClient() {
        if (pubSubApiClient != null) {
            return pubSubApiClient;
        }
        pubSubApiClient = new PubSubApiClient(session, new SalesforceLoginConfig(), pubSubHost, pubSubPort, 0, 0);
        pubSubApiClient.start();
        return pubSubApiClient;
    }

    private SalesforceHttpClient createHttpClient() throws Exception {
        final SalesforceHttpClient httpClient;

        CamelContext camelContext = new DefaultCamelContext();

        // set ssl context parameters
        try {
            final SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
            sslContextFactory.setSslContext(sslContextParameters.createSSLContext(camelContext));

            SecurityUtils.adaptToIBMCipherNames(sslContextFactory);

            httpClient = new SalesforceHttpClient(sslContextFactory);
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Error creating default SSL context: " + e.getMessage(), e);
        }

        // default settings
        httpClient.setConnectTimeout(DEFAULT_TIMEOUT);
        httpClient.setTimeout(DEFAULT_TIMEOUT);

        // enable redirects, no need for a RedirectListener class in Jetty 9
        httpClient.setFollowRedirects(true);

        // set HTTP client parameters
        if (httpClientProperties != null && !httpClientProperties.isEmpty()) {
            try {
                PropertyBindingSupport.bindProperties(camelContext, httpClient, new HashMap<>(httpClientProperties));
            } catch (final Exception e) {
                throw new RuntimeException("Error setting HTTP client properties: " + e.getMessage(), e);
            }
        }

        // wait for 1 second longer than the HTTP client response timeout
        responseTimeout = httpClient.getTimeout() + 1000L;

        // set http proxy settings
        // set HTTP proxy settings
        if (httpProxyHost != null && httpProxyPort != null) {
            final Origin.Address proxyAddress = new Origin.Address(httpProxyHost, httpProxyPort);
            ProxyConfiguration.Proxy proxy;
            if (isHttpProxySocks4) {
                proxy = new Socks4Proxy(proxyAddress, isHttpProxySecure);
            } else {
                proxy = new HttpProxy(proxyAddress, isHttpProxySecure);
            }
            if (httpProxyIncludedAddresses != null && !httpProxyIncludedAddresses.isEmpty()) {
                proxy.getIncludedAddresses().addAll(httpProxyIncludedAddresses);
            }
            if (httpProxyExcludedAddresses != null && !httpProxyExcludedAddresses.isEmpty()) {
                proxy.getExcludedAddresses().addAll(httpProxyExcludedAddresses);
            }
            httpClient.getProxyConfiguration().addProxy(proxy);
        }
        if (httpProxyUsername != null && httpProxyPassword != null) {
            StringHelper.notEmpty(httpProxyAuthUri, "httpProxyAuthUri");
            StringHelper.notEmpty(httpProxyRealm, "httpProxyRealm");

            final Authentication authentication;
            if (httpProxyUseDigestAuth) {
                authentication = new DigestAuthentication(
                        URI.create(httpProxyAuthUri), httpProxyRealm, httpProxyUsername, httpProxyPassword);
            } else {
                authentication = new BasicAuthentication(
                        URI.create(httpProxyAuthUri), httpProxyRealm, httpProxyUsername, httpProxyPassword);
            }
            httpClient.getAuthenticationStore().addAuthentication(authentication);
        }

        // set session before calling start()
        final SalesforceSession session = new SalesforceSession(
                new DefaultCamelContext(), httpClient, httpClient.getTimeout(),
                new SalesforceLoginConfig(loginUrl, clientId, clientSecret, userName, password, false));
        httpClient.setSession(session);

        try {
            httpClient.start();
        } catch (final Exception e) {
            throw new RuntimeException("Error creating HTTP client: " + e.getMessage(), e);
        }

        return httpClient;
    }

    private void disconnectFromSalesforce(final RestClient restClient) {
        if (restClient == null) {
            return;
        }

        try {
            final SalesforceHttpClient httpClient = (SalesforceHttpClient) ((DefaultRestClient) restClient).getHttpClient();
            ServiceHelper.stopAndShutdownServices(restClient, httpClient.getSession(), httpClient);
        } catch (final Exception e) {
            getLog().error("Error stopping Salesforce HTTP client", e);
        }
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public void setHttpClientProperties(Map<String, Object> httpClientProperties) {
        this.httpClientProperties = httpClientProperties;
    }

    public void setHttpProxyAuthUri(String httpProxyAuthUri) {
        this.httpProxyAuthUri = httpProxyAuthUri;
    }

    public void setHttpProxyExcludedAddresses(Set<String> httpProxyExcludedAddresses) {
        this.httpProxyExcludedAddresses = httpProxyExcludedAddresses;
    }

    public void setHttpProxyHost(String httpProxyHost) {
        this.httpProxyHost = httpProxyHost;
    }

    public void setHttpProxyIncludedAddresses(Set<String> httpProxyIncludedAddresses) {
        this.httpProxyIncludedAddresses = httpProxyIncludedAddresses;
    }

    public void setHttpProxyPassword(String httpProxyPassword) {
        this.httpProxyPassword = httpProxyPassword;
    }

    public void setHttpProxyPort(Integer httpProxyPort) {
        this.httpProxyPort = httpProxyPort;
    }

    public void setHttpProxyRealm(String httpProxyRealm) {
        this.httpProxyRealm = httpProxyRealm;
    }

    public void setHttpProxyUseDigestAuth(boolean httpProxyUseDigestAuth) {
        this.httpProxyUseDigestAuth = httpProxyUseDigestAuth;
    }

    public void setHttpProxyUsername(String httpProxyUsername) {
        this.httpProxyUsername = httpProxyUsername;
    }

    public void setHttpProxySecure(boolean httpProxySecure) {
        isHttpProxySecure = httpProxySecure;
    }

    public void setHttpProxySocks4(boolean httpProxySocks4) {
        isHttpProxySocks4 = httpProxySocks4;
    }

    public void setLoginUrl(String loginUrl) {
        this.loginUrl = loginUrl;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setPubSubHost(String pubSubHost) {
        this.pubSubHost = pubSubHost;
    }

    public void setPubSubPort(int pubSubPort) {
        this.pubSubPort = pubSubPort;
    }

    protected abstract void executeWithClient() throws Exception;

    protected abstract Logger getLog();

    public void setup() {
    }
}
