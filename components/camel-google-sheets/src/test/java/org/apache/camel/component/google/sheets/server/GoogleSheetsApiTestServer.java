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
package org.apache.camel.component.google.sheets.server;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import com.consol.citrus.Citrus;
import com.consol.citrus.dsl.runner.DefaultTestRunner;
import com.consol.citrus.dsl.runner.TestRunner;
import com.consol.citrus.exceptions.CitrusRuntimeException;
import com.consol.citrus.http.server.HttpServer;
import com.consol.citrus.http.server.HttpServerBuilder;
import com.consol.citrus.http.servlet.GzipHttpServletResponseWrapper;
import com.consol.citrus.http.servlet.RequestCachingServletFilter;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.DefaultOAuth2RefreshToken;
import org.springframework.security.oauth2.provider.AuthorizationRequest;
import org.springframework.security.oauth2.provider.ClientDetails;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationManager;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationProcessingFilter;
import org.springframework.security.oauth2.provider.client.BaseClientDetails;
import org.springframework.security.oauth2.provider.client.InMemoryClientDetailsService;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.store.InMemoryTokenStore;
import org.springframework.web.filter.OncePerRequestFilter;

public final class GoogleSheetsApiTestServer {

    private static Citrus citrus = Citrus.newInstance();

    private final HttpServer httpServer;
    private TestRunner runner;

    /**
     * Prevent direct instantiation.
     */
    private GoogleSheetsApiTestServer(HttpServer httpServer) {
        this.httpServer = httpServer;
    }

    /**
     * Initialize new test run.
     */
    public void init() {
        runner = new DefaultTestRunner(citrus.getApplicationContext(), citrus.createTestContext());
    }

    /**
     * Stop and reset current test run if any.
     */
    public void reset() {
        if (runner != null) {
            runner.purgeEndpoints(action -> action.endpoint(httpServer));
            runner.stop();
        }
    }

    /**
     * Obtains the httpServer.
     * 
     * @return
     */
    public HttpServer getHttpServer() {
        return httpServer;
    }

    public void afterPropertiesSet() throws Exception {
        httpServer.afterPropertiesSet();
    }

    public TestRunner getRunner() {
        return runner;
    }

    /**
     * Builder builds server instance from given http server builder adding more
     * setting options in fluent builder pattern style.
     */
    public static class Builder {
        private final HttpServerBuilder serverBuilder;

        private Path keyStorePath;
        private String keyStorePassword;
        private int securePort = 8443;

        private String basePath = "";

        private String clientId;
        private String clientSecret;

        private String accessToken;
        private String refreshToken;

        public Builder(HttpServerBuilder serverBuilder) {
            this.serverBuilder = serverBuilder;
        }

        public Builder securePort(int securePort) {
            this.securePort = securePort;
            return this;
        }

        public Builder keyStorePath(Path keyStorePath) {
            this.keyStorePath = keyStorePath;
            return this;
        }

        public Builder keyStorePassword(String keyStorePass) {
            this.keyStorePassword = keyStorePass;
            return this;
        }

        public Builder basePath(String basePath) {
            this.basePath = basePath;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder refreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public GoogleSheetsApiTestServer build() throws Exception {
            SslContextFactory sslContextFactory = new SslContextFactory(true);
            sslContextFactory.setKeyStorePath(keyStorePath.toAbsolutePath().toString());
            sslContextFactory.setKeyStorePassword(keyStorePassword);

            HttpConfiguration parent = new HttpConfiguration();
            parent.setSecureScheme("https");
            parent.setSecurePort(securePort);
            HttpConfiguration httpConfiguration = new HttpConfiguration(parent);
            httpConfiguration.setCustomizers(Collections.singletonList(new SecureRequestCustomizer()));

            ServerConnector sslConnector = new ServerConnector(new org.eclipse.jetty.server.Server(), new SslConnectionFactory(sslContextFactory, "http/1.1"),
                                                               new HttpConnectionFactory(httpConfiguration));
            sslConnector.setPort(securePort);

            serverBuilder.connector(sslConnector);

            Map<String, Filter> filterMap = new LinkedHashMap<>();
            filterMap.put("request-caching-filter", new RequestCachingServletFilter());
            filterMap.put("gzip-filter", new GzipServletFilter());
            filterMap.put("oauth2-filter", oauth2Filter());

            Map<String, String> filterMapings = new LinkedHashMap<>();
            filterMapings.put("oauth2-filter", "/" + Optional.ofNullable(basePath).map(path -> path + "/*").orElse("*"));
            serverBuilder.filterMappings(filterMapings);

            serverBuilder.filters(filterMap);

            serverBuilder.applicationContext(citrus.getApplicationContext());

            GoogleSheetsApiTestServer server = new GoogleSheetsApiTestServer(serverBuilder.build());
            server.afterPropertiesSet();
            return server;
        }

        private Filter oauth2Filter() {
            BaseClientDetails clientDetails = new BaseClientDetails();
            clientDetails.setClientId(clientId);
            clientDetails.setClientSecret(clientSecret);
            clientDetails.setAccessTokenValiditySeconds(3000);
            clientDetails.setAutoApproveScopes(Arrays.asList("read", "write"));
            clientDetails.setScope(Arrays.asList("read", "write"));
            clientDetails.setAuthorities(Arrays.asList(new SimpleGrantedAuthority("client_credentials"), new SimpleGrantedAuthority("authorization_code"),
                                                       new SimpleGrantedAuthority("password"), new SimpleGrantedAuthority("refresh_token")));

            OAuth2AuthenticationProcessingFilter filter = new OAuth2AuthenticationProcessingFilter();
            OAuth2AuthenticationManager oauth2AuthenticationManager = new OAuth2AuthenticationManager();

            InMemoryClientDetailsService clientDetailsService = new InMemoryClientDetailsService();
            Map<String, ClientDetails> clientDetailsStore = new HashMap<>();
            clientDetailsStore.put(clientId, clientDetails);
            clientDetailsService.setClientDetailsStore(clientDetailsStore);
            oauth2AuthenticationManager.setClientDetailsService(clientDetailsService);

            InMemoryTokenStore tokenStore = new InMemoryTokenStore();
            AuthorizationRequest authorizationRequest = new AuthorizationRequest();
            authorizationRequest.setClientId(clientDetails.getClientId());
            authorizationRequest.setAuthorities(clientDetails.getAuthorities());
            authorizationRequest.setApproved(true);

            OAuth2Authentication authentication = new OAuth2Authentication(authorizationRequest.createOAuth2Request(), null);

            tokenStore.storeAccessToken(new DefaultOAuth2AccessToken(accessToken), authentication);
            tokenStore.storeRefreshToken(new DefaultOAuth2RefreshToken(refreshToken), authentication);

            DefaultTokenServices tokenServices = new DefaultTokenServices();
            tokenServices.setTokenStore(tokenStore);
            tokenServices.setClientDetailsService(clientDetailsService);
            tokenServices.setSupportRefreshToken(true);
            oauth2AuthenticationManager.setTokenServices(tokenServices);

            filter.setAuthenticationManager(oauth2AuthenticationManager);
            return filter;
        }
    }

    private static class GzipServletFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
            HttpServletRequest filteredRequest = request;
            HttpServletResponse filteredResponse = response;

            String contentEncoding = request.getHeader(HttpHeaders.CONTENT_ENCODING);
            if (contentEncoding != null && contentEncoding.contains("gzip")) {
                filteredRequest = new GzipHttpServletRequestWrapper(request);
            }

            String acceptEncoding = request.getHeader(HttpHeaders.ACCEPT_ENCODING);
            if (acceptEncoding != null && acceptEncoding.contains("gzip")) {
                filteredResponse = new GzipHttpServletResponseWrapper(response);
            }

            filterChain.doFilter(filteredRequest, filteredResponse);

            if (filteredResponse instanceof GzipHttpServletResponseWrapper) {
                ((GzipHttpServletResponseWrapper)filteredResponse).finish();
            }
        }
    }

    private static class GzipHttpServletRequestWrapper extends HttpServletRequestWrapper {
        /**
         * Constructs a request adaptor wrapping the given request.
         *
         * @param request
         * @throws IllegalArgumentException if the request is null
         */
        public GzipHttpServletRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new GzipServletInputStream(getRequest());
        }

        /**
         * Gzip enabled servlet input stream.
         */
        private static class GzipServletInputStream extends ServletInputStream {
            private final GZIPInputStream gzipStream;

            /**
             * Default constructor using wrapped input stream.
             *
             * @param request
             * @throws IOException
             */
            public GzipServletInputStream(ServletRequest request) throws IOException {
                gzipStream = new GZIPInputStream(request.getInputStream());
            }

            @Override
            public boolean isFinished() {
                try {
                    return gzipStream.available() == 0;
                } catch (IOException e) {
                    throw new CitrusRuntimeException("Failed to check gzip intput stream availability", e);
                }
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(final ReadListener readListener) {
                throw new UnsupportedOperationException("Unsupported operation");
            }

            @Override
            public int read() {
                try {
                    return gzipStream.read();
                } catch (IOException e) {
                    throw new CitrusRuntimeException("Failed to read gzip input stream", e);
                }
            }

            @Override
            public int read(byte[] b) throws IOException {
                return gzipStream.read(b);
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return gzipStream.read(b, off, len);
            }
        }
    }
}
