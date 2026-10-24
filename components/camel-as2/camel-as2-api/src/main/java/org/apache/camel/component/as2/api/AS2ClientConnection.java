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
package org.apache.camel.component.as2.api;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.apache.camel.component.as2.api.io.AS2BHttpClientConnection;
import org.apache.camel.component.as2.api.protocol.RequestAS2;
import org.apache.camel.component.as2.api.protocol.RequestMDN;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.LeaseRequest;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessorBuilder;
import org.apache.hc.core5.http.protocol.RequestConnControl;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestDate;
import org.apache.hc.core5.http.protocol.RequestExpectContinue;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

public class AS2ClientConnection {

    private static final int RETRIEVE_CONNECTION_TIMEOUT_SECONDS = 5;

    private HttpHost targetHost;
    private HttpRoute route;
    private String proxyAuthorization;
    private HttpProcessor httpProcessor;
    private String as2Version;
    private String userAgent;
    private String clientFqdn;
    private int connectionTimeoutMilliseconds;
    private PoolingHttpClientConnectionManager connectionPoolManager;
    private ConnectionKeepAliveStrategy connectionKeepAliveStrategy;

    public AS2ClientConnection(String as2Version, String userAgent, String clientFqdn, String targetHostName,
                               Integer targetPortNumber, Duration socketTimeout, Duration connectionTimeout,
                               Integer connectionPoolMaxSize, Duration connectionPoolTtl,
                               SSLContext sslContext, HostnameVerifier hostnameVerifier) throws IOException {
        this(as2Version, userAgent, clientFqdn, targetHostName, targetPortNumber, socketTimeout, connectionTimeout,
             connectionPoolMaxSize, connectionPoolTtl, sslContext, hostnameVerifier, false);
    }

    public AS2ClientConnection(String as2Version, String userAgent, String clientFqdn, String targetHostName,
                               Integer targetPortNumber, Duration socketTimeout, Duration connectionTimeout,
                               Integer connectionPoolMaxSize, Duration connectionPoolTtl,
                               SSLContext sslContext, HostnameVerifier hostnameVerifier,
                               boolean expectContinue) throws IOException {
        this(as2Version, userAgent, clientFqdn, targetHostName, targetPortNumber, socketTimeout, connectionTimeout,
             connectionPoolMaxSize, connectionPoolTtl, sslContext, hostnameVerifier, expectContinue, null, null, null, null);
    }

    public AS2ClientConnection(String as2Version, String userAgent, String clientFqdn, String targetHostName,
                               Integer targetPortNumber, Duration socketTimeout, Duration connectionTimeout,
                               Integer connectionPoolMaxSize, Duration connectionPoolTtl,
                               SSLContext sslContext, HostnameVerifier hostnameVerifier,
                               boolean expectContinue, String proxyHost, Integer proxyPort,
                               String proxyAuthUsername, String proxyAuthPassword) throws IOException {

        this.as2Version = ObjectHelper.notNull(as2Version, "as2Version");
        this.userAgent = ObjectHelper.notNull(userAgent, "userAgent");
        this.clientFqdn = ObjectHelper.notNull(clientFqdn, "clientFqdn");
        this.targetHost = new HttpHost(
                sslContext != null ? "https" : "http",
                ObjectHelper.notNull(targetHostName, "targetHostName"),
                ObjectHelper.notNull(targetPortNumber, "targetPortNumber"));
        if (proxyHost != null) {
            // the connection to the proxy itself is plain http, whatever the target scheme: an https target is
            // reached through a CONNECT tunnel
            HttpHost proxy = new HttpHost("http", proxyHost, ObjectHelper.notNull(proxyPort, "proxyPort"));
            this.route = new HttpRoute(targetHost, null, proxy, sslContext != null);
            if (proxyAuthUsername != null && proxyAuthPassword != null) {
                this.proxyAuthorization = "Basic " + Base64.getEncoder().encodeToString(
                        (proxyAuthUsername + ":" + proxyAuthPassword).getBytes(StandardCharsets.UTF_8));
            } else if (proxyAuthUsername != null || proxyAuthPassword != null) {
                // one credential alone is a configuration mistake: report it now rather than as a 407 on the first send
                throw new IllegalArgumentException("proxyAuthUsername and proxyAuthPassword must be set together");
            }
        } else {
            this.route = new HttpRoute(targetHost);
        }
        ObjectHelper.notNull(socketTimeout, "socketTimeout");
        this.connectionTimeoutMilliseconds = (int) ObjectHelper.notNull(connectionTimeout, "connectionTimeout").toMillis();
        ObjectHelper.notNull(connectionPoolMaxSize, "connectionPoolMaxSize");
        ObjectHelper.notNull(connectionPoolTtl, "connectionPoolTtl");

        // Build Processor
        HttpProcessorBuilder processorBuilder = HttpProcessorBuilder.create()
                .add(new RequestAS2(as2Version, clientFqdn))
                .add(new RequestMDN())
                .add(new RequestTargetHost())
                .add(new RequestUserAgent(this.userAgent))
                .add(new RequestDate())
                .add(new RequestContent(true))
                .add(new RequestConnControl());
        if (expectContinue) {
            processorBuilder.add(new RequestExpectContinue());
        }
        httpProcessor = processorBuilder.build();

        final Http1Config h1Config = Http1Config.custom().setBufferSize(8 * 1024).build();

        HttpConnectionFactory<ManagedHttpClientConnection> connFactory
                = new ManagedHttpClientConnectionFactory(h1Config, null, null) {
                    @Override
                    public ManagedHttpClientConnection createConnection(final Socket socket) throws IOException {
                        ManagedHttpClientConnection mc = super.createConnection(socket);
                        return new AS2BHttpClientConnection(mc);
                    }
                };

        PoolingHttpClientConnectionManagerBuilder builder = PoolingHttpClientConnectionManagerBuilder.create()
                .setConnectionFactory(connFactory);

        if (sslContext != null) {
            TlsSocketStrategy tlsStrategy;
            if (hostnameVerifier == null) {
                tlsStrategy = new DefaultClientTlsStrategy(sslContext);
            } else {
                tlsStrategy = new DefaultClientTlsStrategy(sslContext, HostnameVerificationPolicy.CLIENT, hostnameVerifier);
            }
            builder.setTlsSocketStrategy(tlsStrategy);
        }

        connectionPoolManager = builder.build();
        connectionPoolManager.setMaxTotal(connectionPoolMaxSize);
        connectionPoolManager.setDefaultSocketConfig(
                SocketConfig.copy(SocketConfig.DEFAULT)
                        .setSoTimeout(Timeout.ofSeconds(socketTimeout.getSeconds()))
                        .build());
        connectionPoolManager.setDefaultConnectionConfig(
                ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(connectionTimeoutMilliseconds))
                        .build());

        connectionKeepAliveStrategy = (response, context) -> {
            TimeValue ttl = TimeValue.of(connectionPoolTtl);
            for (Header h : response.getHeaders()) {
                if (AS2Header.KEEP_ALIVE.equalsIgnoreCase(h.getName())) {
                    HeaderElement headerElement = MessageSupport.parseElements(h).get(0);
                    if (headerElement.getValue() != null && "timeout".equalsIgnoreCase(headerElement.getName())) {
                        ttl = TimeValue.ofSeconds(Long.parseLong(headerElement.getValue()));
                    }
                }
            }
            return ttl;
        };

        // Check if a connection can be established
        createTestConnection(sslContext, connFactory).close();

    }

    public String getAs2Version() {
        return as2Version;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getClientFqdn() {
        return clientFqdn;
    }

    public HttpResponse send(ClassicHttpRequest request, HttpCoreContext httpContext)
            throws HttpException, IOException, InterruptedException, ExecutionException, TimeoutException {

        request.setAuthority(new URIAuthority(targetHost));
        if (route.getProxyHost() != null && !route.isTunnelled()) {
            // a proxy forwarding plain http needs the absolute request-URI, and its credentials on every request
            request.setScheme(targetHost.getSchemeName());
            if (request instanceof BasicHttpRequest basicRequest) {
                basicRequest.setAbsoluteRequestUri(true);
            }
            if (proxyAuthorization != null) {
                request.setHeader(HttpHeaders.PROXY_AUTHORIZATION, proxyAuthorization);
            }
        }
        LeaseRequest leaseRequest = connectionPoolManager.lease(UUID.randomUUID().toString(), route, null);

        ConnectionEndpoint endpoint = null;
        HttpResponse response = null;
        try {
            endpoint = leaseRequest.get(Timeout.ofSeconds(RETRIEVE_CONNECTION_TIMEOUT_SECONDS));
            if (!endpoint.isConnected()) {
                try {
                    connectionPoolManager.connect(endpoint, TimeValue.ofMilliseconds(connectionTimeoutMilliseconds),
                            httpContext);
                    if (route.isTunnelled()) {
                        createTunnel(endpoint);
                        connectionPoolManager.upgrade(endpoint, httpContext);
                    }
                } catch (IOException | HttpException | RuntimeException e) {
                    // a connection without its tunnel or its TLS layer must not go back to the pool
                    endpoint.close(CloseMode.IMMEDIATE);
                    throw e;
                }
            }
            // Execute Request
            ConnectionEndpoint.RequestExecutor requestExecutor = new ConnectionEndpoint.RequestExecutor() {
                @Override
                public ClassicHttpResponse execute(ClassicHttpRequest request, HttpClientConnection conn, HttpContext context)
                        throws IOException, HttpException {
                    HttpRequestExecutor executor = new HttpRequestExecutor();
                    executor.preProcess(request, httpProcessor, context);
                    ClassicHttpResponse response = executor.execute(request, conn, context);
                    executor.postProcess(response, httpProcessor, context);
                    return response;
                }
            };
            response = endpoint.execute(UUID.randomUUID().toString(), request, requestExecutor, httpContext);
            if (route.getProxyHost() != null && !route.isTunnelled()
                    && response.getCode() == HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED) {
                // the proxy may keep the connection open with the body of its 407 unread: it must not be reused
                endpoint.close(CloseMode.IMMEDIATE);
                // only the proxy answers 407: the message has not reached the AS2 partner
                throw new HttpException(
                        "Proxy " + route.getProxyHost().toHostString() + " refused to forward the request to "
                                        + targetHost.toHostString() + ": " + new StatusLine(response));
            }
        } finally {
            if (endpoint != null) {
                // a null keep-alive makes the pool close the connection: a zero or negative one keeps it with no expiry
                TimeValue keepAlive = null;
                if (response != null && DefaultConnectionReuseStrategy.INSTANCE.keepAlive(request, response, httpContext)) {
                    // the partner allows the connection to be reused (no Connection: close, the response has a length)
                    keepAlive = connectionKeepAliveStrategy.getKeepAliveDuration(response, httpContext);
                }
                try {
                    connectionPoolManager.release(endpoint, null, keepAlive);
                } catch (Exception ignored) {
                    // ignore
                } finally {
                    IOHelper.close(endpoint);
                }
            }
        }
        return response;
    }

    /**
     * Asks the proxy to open a tunnel to the target. The proxy credentials go on the CONNECT request only, so they are
     * never seen by the AS2 partner at the other end of the tunnel.
     */
    private void createTunnel(ConnectionEndpoint endpoint) throws IOException, HttpException {
        ClassicHttpRequest connect = new BasicClassicHttpRequest(Method.CONNECT, targetHost, targetHost.toHostString());
        connect.setVersion(HttpVersion.HTTP_1_1);
        connect.setHeader(HttpHeaders.HOST, targetHost.toHostString());
        connect.setHeader(HttpHeaders.USER_AGENT, userAgent);
        if (proxyAuthorization != null) {
            connect.setHeader(HttpHeaders.PROXY_AUTHORIZATION, proxyAuthorization);
        }
        // a separate context, so the caller's context keeps the AS2 request and response
        ClassicHttpResponse response = endpoint.execute(UUID.randomUUID().toString(), connect,
                (request, conn, context) -> new HttpRequestExecutor().execute(request, conn, context),
                HttpCoreContext.create());
        int status = response.getCode();
        if (status < HttpStatus.SC_SUCCESS || status >= HttpStatus.SC_REDIRECTION) {
            throw new HttpException(
                    "Proxy " + route.getProxyHost().toHostString() + " refused to open a tunnel to "
                                    + targetHost.toHostString() + ": " + new StatusLine(response));
        }
    }

    private HttpConnection createTestConnection(
            SSLContext sslContext, HttpConnectionFactory<ManagedHttpClientConnection> connFactory)
            throws IOException {
        HttpHost proxy = route.getProxyHost();
        if (proxy != null) {
            // the target is only reached through the proxy once a request is sent, so check the proxy can be reached
            return connFactory.createConnection(new Socket(proxy.getHostName(), proxy.getPort()));
        }
        if (sslContext == null) {
            return connFactory.createConnection(new Socket(targetHost.getHostName(), targetHost.getPort()));
        } else {
            SSLSocketFactory factory = sslContext.getSocketFactory();
            return connFactory.createConnection(factory.createSocket(targetHost.getHostName(), targetHost.getPort()));
        }
    }
}
