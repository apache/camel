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
package org.apache.camel.component.box.internal;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;

import com.box.boxjavalibv2.BoxClient;
import com.box.boxjavalibv2.BoxConnectionManagerBuilder;
import com.box.boxjavalibv2.BoxRESTClient;
import com.box.boxjavalibv2.authorization.IAuthFlowUI;
import com.box.boxjavalibv2.authorization.IAuthSecureStorage;
import com.box.restclientv2.IBoxRESTClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.box.BoxConfiguration;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to work with {@link BoxClient}.
 */
public final class BoxClientHelper {

    private static final Logger LOG = LoggerFactory.getLogger(BoxClientHelper.class);

    private BoxClientHelper() {
    }

    // create BoxClient using provided configuration
    @SuppressWarnings("deprecation")
    public static CachedBoxClient createBoxClient(final BoxConfiguration configuration) {

        final String clientId = configuration.getClientId();
        final String clientSecret = configuration.getClientSecret();

        final IAuthSecureStorage authSecureStorage = configuration.getAuthSecureStorage();
        final String userName = configuration.getUserName();
        final String userPassword = configuration.getUserPassword();

        if ((authSecureStorage == null && ObjectHelper.isEmpty(userPassword))
                || ObjectHelper.isEmpty(userName) || ObjectHelper.isEmpty(clientId) || ObjectHelper.isEmpty(clientSecret)) {
            throw new IllegalArgumentException(
                    "Missing one or more required properties "
                            + "clientId, clientSecret, userName and either authSecureStorage or userPassword");
        }
        LOG.debug("Creating BoxClient for login:{}, client_id:{} ...", userName, clientId);

        // if set, use configured connection manager builder
        final BoxConnectionManagerBuilder connectionManagerBuilder = configuration.getConnectionManagerBuilder();
        final BoxConnectionManagerBuilder connectionManager = connectionManagerBuilder != null
                ? connectionManagerBuilder : new BoxConnectionManagerBuilder();

        // create REST client for BoxClient
        final ClientConnectionManager[] clientConnectionManager = new ClientConnectionManager[1];
        final IBoxRESTClient restClient = new BoxRESTClient(connectionManager.build()) {
            @Override
            public HttpClient getRawHttpClient() {
                final HttpClient httpClient = super.getRawHttpClient();
                clientConnectionManager[0] = httpClient.getConnectionManager();
                final SchemeRegistry schemeRegistry = clientConnectionManager[0].getSchemeRegistry();
                SSLContextParameters sslContextParameters = configuration.getSslContextParameters();
                if (sslContextParameters == null) {
                    sslContextParameters = new SSLContextParameters();
                }
                final Map<String, Object> configParams = configuration.getHttpParams();
                boolean useSocksProxy = false;
                HttpHost proxyHost = null;
                if (configParams != null && !configParams.isEmpty()) {
                    final Boolean socksProxy = (Boolean) configParams.get("http.route.socks-proxy");
                    if (socksProxy != null && socksProxy) {
                        useSocksProxy = true;
                        proxyHost = (HttpHost) configParams.get(ConnRoutePNames.DEFAULT_PROXY);
                    }
                    // set custom HTTP params
                    LOG.debug("Setting {} HTTP Params", configParams.size());

                    final HttpParams httpParams = httpClient.getParams();
                    for (Map.Entry<String, Object> param : configParams.entrySet()) {
                        // don't add proxy params if socks
                        if (!(useSocksProxy && (param.getKey().equals("http.route.socks-proxy") || param.getKey().equals(ConnRoutePNames.DEFAULT_PROXY)))) {
                            httpParams.setParameter(param.getKey(), param.getValue());
                        }
                    }

                }
                SSLContext sslContext;
                try {
                    sslContext = sslContextParameters.createSSLContext();
                } catch (IOException e) {
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                } catch (GeneralSecurityException e) {
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                }
                SSLSocketFactory socketFactory;
                if (useSocksProxy) {
                    socketFactory = new SocksSSLSocketFactory(sslContext, proxyHost);
                } else {
                    socketFactory = new SSLSocketFactory(sslContext, SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
                }
                schemeRegistry.register(new Scheme("https", socketFactory, 443));

                return httpClient;
            }
        };
        final BoxClient boxClient = new BoxClient(clientId, clientSecret, null, null,
                restClient, configuration.getBoxConfig());

        // enable OAuth auto-refresh
        boxClient.setAutoRefreshOAuth(true);

        // wrap the configured storage in a caching storage
        final CachingSecureStorage storage = new CachingSecureStorage(authSecureStorage);

        // set up a listener to notify secure storage and user provided listener, store it in configuration!
        final OAuthHelperListener listener = new OAuthHelperListener(storage, configuration.getRefreshListener());
        boxClient.addOAuthRefreshListener(listener);

        final CachedBoxClient cachedBoxClient = new CachedBoxClient(boxClient, userName, clientId, storage, listener, clientConnectionManager);
        LOG.debug("BoxClient created {}", cachedBoxClient);
        return cachedBoxClient;
    }

    public static void getOAuthToken(BoxConfiguration configuration, CachedBoxClient cachedBoxClient) throws Exception {

        final BoxClient boxClient = cachedBoxClient.getBoxClient();
        synchronized (boxClient) {
            if (boxClient.isAuthenticated()) {
                return;
            }

            LOG.debug("Getting OAuth token for {}...", cachedBoxClient);

            final IAuthSecureStorage authSecureStorage = cachedBoxClient.getSecureStorage();
            if (authSecureStorage != null && authSecureStorage.getAuth() != null) {

                LOG.debug("Using secure storage for {}", cachedBoxClient);
                // authenticate using stored refresh token
                boxClient.authenticateFromSecureStorage(authSecureStorage);
            } else {

                LOG.debug("Using OAuth {}", cachedBoxClient);
                // authorize App for user, and create OAuth token with refresh token
                final IAuthFlowUI authFlowUI = new LoginAuthFlowUI(configuration, boxClient);
                final CountDownLatch latch = new CountDownLatch(1);
                final LoginAuthFlowListener listener = new LoginAuthFlowListener(latch);
                boxClient.authenticate(authFlowUI, true, listener);

                // wait for login to finish or timeout
                if (!latch.await(configuration.getLoginTimeout(), TimeUnit.SECONDS)) {
                    if (!boxClient.isAuthenticated()) {
                        throw new RuntimeCamelException(String.format("Login timeout for %s", cachedBoxClient));
                    }
                }
                final Exception ex = listener.getException();
                if (ex != null) {
                    throw new RuntimeCamelException(String.format("Login error for %s: %s",
                            cachedBoxClient, ex.getMessage()), ex);
                }
            }

            LOG.debug("OAuth token created for {}", cachedBoxClient);
            // notify the cached client listener for the first time, since BoxClient doesn't!!!
            cachedBoxClient.getListener().onRefresh(boxClient.getAuthData());
        }
    }

    public static void closeIdleConnections(CachedBoxClient cachedBoxClient) {
        @SuppressWarnings("deprecation")
        final ClientConnectionManager connectionManager = cachedBoxClient.getClientConnectionManager();
        if (connectionManager != null) {
            // close all idle connections
            connectionManager.closeIdleConnections(1, TimeUnit.MILLISECONDS);
        }
    }

    public static void shutdownBoxClient(BoxConfiguration configuration, CachedBoxClient cachedBoxClient) throws Exception {

        final BoxClient boxClient = cachedBoxClient.getBoxClient();
        synchronized (boxClient) {

            LOG.debug("Shutting down {} ...", cachedBoxClient);
            try {
                // revoke token if requested
                if (configuration.isRevokeOnShutdown()) {
                    revokeOAuthToken(configuration, cachedBoxClient);
                }
            } finally {

                boxClient.setConnectionOpen(false);
                // close connections in the underlying HttpClient
                @SuppressWarnings("deprecation")
                final ClientConnectionManager connectionManager = cachedBoxClient.getClientConnectionManager();
                if (connectionManager != null) {
                    LOG.debug("Closing connections for {}", cachedBoxClient);

                    connectionManager.shutdown();
                } else {
                    LOG.debug("ConnectionManager not created for {}", cachedBoxClient);
                }
            }
            LOG.debug("Shutdown successful for {}", cachedBoxClient);
        }
    }

    private static void revokeOAuthToken(BoxConfiguration configuration, CachedBoxClient cachedBoxClient) throws Exception {

        final BoxClient boxClient = cachedBoxClient.getBoxClient();
        synchronized (boxClient) {

            if (boxClient.isAuthenticated()) {

                LOG.debug("Revoking OAuth refresh token for {}", cachedBoxClient);

                // revoke OAuth token
                boxClient.getOAuthManager().revokeOAuth(boxClient.getAuthData().getAccessToken(),
                        configuration.getClientId(), configuration.getClientSecret());

                // notify the OAuthListener of revoked token
                cachedBoxClient.getListener().onRefresh(null);
                // mark auth data revoked
                boxClient.getOAuthDataController().setOAuthData(null);
            }
        }
    }

    static class SocksSSLSocketFactory extends SSLSocketFactory {
        HttpHost proxyHost;

        public SocksSSLSocketFactory(SSLContext sslContext, HttpHost proxyHost) {
            super(sslContext, SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
            this.proxyHost = proxyHost;
        }

        @Override
        public Socket createSocket(final HttpContext context) throws IOException {
            InetSocketAddress socksaddr = new InetSocketAddress(proxyHost.getHostName(), proxyHost.getPort());
            Proxy proxy = new Proxy(Proxy.Type.SOCKS, socksaddr);
            return new Socket(proxy);
        }

    }
}
