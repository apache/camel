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
package org.apache.camel.maven;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.SalesforceHttpClient;
import org.apache.camel.component.salesforce.SalesforceLoginConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.internal.PayloadFormat;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.camel.component.salesforce.internal.client.DefaultRestClient;
import org.apache.camel.component.salesforce.internal.client.RestClient;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.Origin;
import org.eclipse.jetty.client.ProxyConfiguration;
import org.eclipse.jetty.client.Socks4Proxy;
import org.eclipse.jetty.client.api.Authentication;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.client.util.DigestAuthentication;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * Base class for any Salesforce MOJO.
 */
abstract class AbstractSalesforceMojo extends AbstractMojo {

    // default connect and call timeout
    private static final int DEFAULT_TIMEOUT = 60000;

    /**
     * Salesforce client id.
     */
    @Parameter(property = "camelSalesforce.clientId", required = true)
    String clientId;

    /**
     * Salesforce client secret.
     */
    @Parameter(property = "camelSalesforce.clientSecret", required = true)
    String clientSecret;

    /**
     * HTTP client properties.
     */
    @Parameter
    Map<String, Object> httpClientProperties;

    /**
     * Proxy authentication URI.
     */
    @Parameter(property = "camelSalesforce.httpProxyAuthUri")
    String httpProxyAuthUri;

    /**
     * Addresses to NOT Proxy.
     */
    @Parameter(property = "camelSalesforce.httpProxyExcludedAddresses")
    Set<String> httpProxyExcludedAddresses;

    /**
     * HTTP Proxy host.
     */
    @Parameter(property = "camelSalesforce.httpProxyHost")
    String httpProxyHost;

    /**
     * Addresses to Proxy.
     */
    @Parameter(property = "camelSalesforce.httpProxyIncludedAddresses")
    Set<String> httpProxyIncludedAddresses;

    /**
     * Proxy authentication password.
     */
    @Parameter(property = "camelSalesforce.httpProxyPassword")
    String httpProxyPassword;

    /**
     * HTTP Proxy port.
     */
    @Parameter(property = "camelSalesforce.httpProxyPort")
    Integer httpProxyPort;

    /**
     * Proxy authentication realm.
     */
    @Parameter(property = "camelSalesforce.httpProxyRealm")
    String httpProxyRealm;

    /**
     * Proxy uses Digest authentication.
     */
    @Parameter(property = "camelSalesforce.httpProxyUseDigestAuth")
    boolean httpProxyUseDigestAuth;

    /**
     * Proxy authentication username.
     */
    @Parameter(property = "camelSalesforce.httpProxyUsername")
    String httpProxyUsername;

    /**
     * Is HTTP Proxy secure, i.e. using secure sockets, true by default.
     */
    @Parameter(property = "camelSalesforce.isHttpProxySecure")
    boolean isHttpProxySecure = true;

    /**
     * Is it a SOCKS4 Proxy?
     */
    @Parameter(property = "camelSalesforce.isHttpProxySocks4")
    boolean isHttpProxySocks4;

    /**
     * Salesforce login URL, defaults to https://login.salesforce.com.
     */
    @Parameter(property = "camelSalesforce.loginUrl", defaultValue = SalesforceLoginConfig.DEFAULT_LOGIN_URL)
    String loginUrl;

    /**
     * Salesforce password.
     */
    @Parameter(property = "camelSalesforce.password", required = true)
    String password;

    /**
     * SSL Context parameters.
     */
    @Parameter(property = "camelSalesforce.sslContextParameters")
    final SSLContextParameters sslContextParameters = new SSLContextParameters();

    /**
     * Salesforce username.
     */
    @Parameter(property = "camelSalesforce.userName", required = true)
    String userName;

    /**
     * Salesforce API version.
     */
    @Parameter(property = "camelSalesforce.version", defaultValue = SalesforceEndpointConfig.DEFAULT_VERSION)
    String version;

    private long responseTimeout;

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        final RestClient restClient = connectToSalesforce();
        try {
            executeWithClient(restClient);
        } finally {
            disconnectFromSalesforce(restClient);
        }
    }

    public long getResponseTimeout() {
        return responseTimeout;
    }

    private RestClient connectToSalesforce() throws MojoExecutionException {
        RestClient restClient = null;
        try {
            final SalesforceHttpClient httpClient = createHttpClient();

            // connect to Salesforce
            getLog().info("Logging in to Salesforce");
            final SalesforceSession session = httpClient.getSession();
            try {
                session.login(null);
            } catch (final SalesforceException e) {
                final String msg = "Salesforce login error " + e.getMessage();
                throw new MojoExecutionException(msg, e);
            }
            getLog().info("Salesforce login successful");

            // create rest client

            restClient = new DefaultRestClient(httpClient, version, PayloadFormat.JSON, session);
            // remember to start the active client object
            ((DefaultRestClient) restClient).start();

            return restClient;
        } catch (final Exception e) {
            final String msg = "Error connecting to Salesforce: " + e.getMessage();
            disconnectFromSalesforce(restClient);
            throw new MojoExecutionException(msg, e);
        }
    }

    private SalesforceHttpClient createHttpClient() throws MojoExecutionException {
        final SalesforceHttpClient httpClient;

        // set ssl context parameters
        try {
            final SslContextFactory sslContextFactory = new SslContextFactory();
            sslContextFactory.setSslContext(sslContextParameters.createSSLContext(new DefaultCamelContext()));

            httpClient = new SalesforceHttpClient(sslContextFactory);
        } catch (final GeneralSecurityException e) {
            throw new MojoExecutionException("Error creating default SSL context: " + e.getMessage(), e);
        } catch (final IOException e) {
            throw new MojoExecutionException("Error creating default SSL context: " + e.getMessage(), e);
        }

        // default settings
        httpClient.setConnectTimeout(DEFAULT_TIMEOUT);
        httpClient.setTimeout(DEFAULT_TIMEOUT);

        // enable redirects, no need for a RedirectListener class in Jetty 9
        httpClient.setFollowRedirects(true);

        // set HTTP client parameters
        if (httpClientProperties != null && !httpClientProperties.isEmpty()) {
            try {
                IntrospectionSupport.setProperties(httpClient, new HashMap<>(httpClientProperties));
            } catch (final Exception e) {
                throw new MojoExecutionException("Error setting HTTP client properties: " + e.getMessage(), e);
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
            httpClient.getProxyConfiguration().getProxies().add(proxy);
        }
        if (httpProxyUsername != null && httpProxyPassword != null) {
            StringHelper.notEmpty(httpProxyAuthUri, "httpProxyAuthUri");
            StringHelper.notEmpty(httpProxyRealm, "httpProxyRealm");

            final Authentication authentication;
            if (httpProxyUseDigestAuth) {
                authentication = new DigestAuthentication(URI.create(httpProxyAuthUri), httpProxyRealm,
                    httpProxyUsername, httpProxyPassword);
            } else {
                authentication = new BasicAuthentication(URI.create(httpProxyAuthUri), httpProxyRealm,
                    httpProxyUsername, httpProxyPassword);
            }
            httpClient.getAuthenticationStore().addAuthentication(authentication);
        }

        // set session before calling start()
        final SalesforceSession session = new SalesforceSession(new DefaultCamelContext(), httpClient,
            httpClient.getTimeout(),
            new SalesforceLoginConfig(loginUrl, clientId, clientSecret, userName, password, false));
        httpClient.setSession(session);

        try {
            httpClient.start();
        } catch (final Exception e) {
            throw new MojoExecutionException("Error creating HTTP client: " + e.getMessage(), e);
        }

        return httpClient;
    }

    private void disconnectFromSalesforce(final RestClient restClient) {
        if (restClient == null) {
            return;
        }

        try {
            final SalesforceHttpClient httpClient = (SalesforceHttpClient) ((DefaultRestClient) restClient)
                .getHttpClient();
            ServiceHelper.stopAndShutdownServices(restClient, httpClient.getSession(), httpClient);
        } catch (final Exception e) {
            getLog().error("Error stopping Salesforce HTTP client", e);
        }
    }

    protected abstract void executeWithClient(RestClient client) throws MojoExecutionException;
}
