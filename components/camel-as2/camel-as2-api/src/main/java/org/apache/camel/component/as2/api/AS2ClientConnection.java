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
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.apache.camel.component.as2.api.io.AS2BHttpClientConnection;
import org.apache.camel.component.as2.api.protocol.RequestAS2;
import org.apache.camel.component.as2.api.protocol.RequestMDN;
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
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.message.MessageSupport;
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
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

public class AS2ClientConnection {

    private static final int RETRIEVE_CONNECTION_TIMEOUT_SECONDS = 5;

    private HttpHost targetHost;
    private HttpProcessor httpProcessor;
    private String as2Version;
    private String userAgent;
    private String clientFqdn;
    private int connectionTimeoutMilliseconds;
    private PoolingHttpClientConnectionManager connectionPoolManager;
    private ConnectionKeepAliveStrategy connectionKeepAliveStrategy;

    public AS2ClientConnection(
            String as2Version,
            String userAgent,
            String clientFqdn,
            String targetHostName,
            Integer targetPortNumber,
            Duration socketTimeout,
            Duration connectionTimeout,
            Integer connectionPoolMaxSize,
            Duration connectionPoolTtl,
            SSLContext sslContext,
            HostnameVerifier hostnameVerifier)
            throws IOException {

        this.as2Version = ObjectHelper.notNull(as2Version, "as2Version");
        this.userAgent = ObjectHelper.notNull(userAgent, "userAgent");
        this.clientFqdn = ObjectHelper.notNull(clientFqdn, "clientFqdn");
        this.targetHost = new HttpHost(
                sslContext != null ? "https" : "http",
                ObjectHelper.notNull(targetHostName, "targetHostName"),
                ObjectHelper.notNull(targetPortNumber, "targetPortNumber"));
        ObjectHelper.notNull(socketTimeout, "socketTimeout");
        this.connectionTimeoutMilliseconds = (int)
                ObjectHelper.notNull(connectionTimeout, "connectionTimeout").toMillis();
        ObjectHelper.notNull(connectionPoolMaxSize, "connectionPoolMaxSize");
        ObjectHelper.notNull(connectionPoolTtl, "connectionPoolTtl");

        // Build Processor
        httpProcessor = HttpProcessorBuilder.create()
                .add(new RequestAS2(as2Version, clientFqdn))
                .add(new RequestMDN())
                .add(new RequestTargetHost())
                .add(new RequestUserAgent(this.userAgent))
                .add(new RequestDate())
                .add(new RequestContent(true))
                .add(new RequestConnControl())
                .add(new RequestExpectContinue())
                .build();

        final Http1Config h1Config =
                Http1Config.custom().setBufferSize(8 * 1024).build();

        HttpConnectionFactory<ManagedHttpClientConnection> connFactory =
                new ManagedHttpClientConnectionFactory(h1Config, null, null) {
                    @Override
                    public ManagedHttpClientConnection createConnection(final Socket socket) throws IOException {
                        ManagedHttpClientConnection mc = super.createConnection(socket);
                        return new AS2BHttpClientConnection(mc);
                    }
                };

        if (sslContext == null) {
            connectionPoolManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setConnectionFactory(connFactory)
                    .build();
        } else {
            SSLConnectionSocketFactory sslConnectionSocketFactory;
            if (hostnameVerifier == null) {
                sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext);
            } else {
                sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
            }
            Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.getSocketFactory())
                    .register("https", sslConnectionSocketFactory)
                    .build();
            connectionPoolManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry, connFactory);
        }
        connectionPoolManager.setMaxTotal(connectionPoolMaxSize);
        connectionPoolManager.setDefaultSocketConfig(SocketConfig.copy(SocketConfig.DEFAULT)
                .setSoTimeout(Timeout.ofSeconds(socketTimeout.getSeconds()))
                .build());
        connectionPoolManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectionTimeoutMilliseconds))
                .build());

        connectionKeepAliveStrategy = (response, context) -> {
            TimeValue ttl = TimeValue.of(connectionPoolTtl);
            for (Header h : response.getHeaders()) {
                if (AS2Header.CONNECTION.equalsIgnoreCase(h.getName())) {
                    if (AS2Header.CLOSE.equalsIgnoreCase(h.getValue())) {
                        ttl = TimeValue.NEG_ONE_MILLISECOND;
                    }
                }
                if (AS2Header.KEEP_ALIVE.equalsIgnoreCase(h.getName())) {
                    HeaderElement headerElement = MessageSupport.parse(h)[0];
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

        HttpRoute route = new HttpRoute(targetHost);

        request.setAuthority(new URIAuthority(targetHost));

        LeaseRequest leaseRequest =
                connectionPoolManager.lease(UUID.randomUUID().toString(), route, null);
        ConnectionEndpoint endpoint = leaseRequest.get(Timeout.ofSeconds(RETRIEVE_CONNECTION_TIMEOUT_SECONDS));
        if (!endpoint.isConnected()) {
            connectionPoolManager.connect(
                    endpoint, TimeValue.ofMilliseconds(connectionTimeoutMilliseconds), httpContext);
        }

        // Execute Request
        HttpRequestExecutor httpExecutor = new HttpRequestExecutor() {
            @Override
            public ClassicHttpResponse execute(
                    ClassicHttpRequest request, HttpClientConnection conn, HttpContext context)
                    throws IOException, HttpException {
                super.preProcess(request, httpProcessor, context);
                ClassicHttpResponse response = super.execute(request, conn, context);
                super.postProcess(response, httpProcessor, context);
                return response;
            }
        };

        HttpResponse response = endpoint.execute(UUID.randomUUID().toString(), request, httpExecutor, httpContext);
        connectionPoolManager.release(
                endpoint, null, connectionKeepAliveStrategy.getKeepAliveDuration(response, httpContext));

        return response;
    }

    private HttpConnection createTestConnection(
            SSLContext sslContext, HttpConnectionFactory<ManagedHttpClientConnection> connFactory) throws IOException {
        if (sslContext == null) {
            return connFactory.createConnection(new Socket(targetHost.getHostName(), targetHost.getPort()));
        } else {
            SSLSocketFactory factory = sslContext.getSocketFactory();
            return connFactory.createConnection(factory.createSocket(targetHost.getHostName(), targetHost.getPort()));
        }
    }
}
