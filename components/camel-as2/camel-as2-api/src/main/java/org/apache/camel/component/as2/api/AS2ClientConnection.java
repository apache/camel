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
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.apache.camel.component.as2.api.io.AS2BHttpClientConnection;
import org.apache.camel.component.as2.api.protocol.RequestAS2;
import org.apache.camel.component.as2.api.protocol.RequestMDN;
import org.apache.camel.util.ObjectHelper;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.HttpConnectionFactory;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpProcessorBuilder;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestDate;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;

public class AS2ClientConnection {

    private static final int RETRIEVE_FROM_CONNECTION_POOL_TIMEOUT_SECONDS = 5;

    private HttpHost targetHost;
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

        this.as2Version = ObjectHelper.notNull(as2Version, "as2Version");
        this.userAgent = ObjectHelper.notNull(userAgent, "userAgent");
        this.clientFqdn = ObjectHelper.notNull(clientFqdn, "clientFqdn");
        this.targetHost = new HttpHost(
                ObjectHelper.notNull(targetHostName, "targetHostName"),
                ObjectHelper.notNull(targetPortNumber, "targetPortNumber"),
                sslContext != null ? "https" : "http");
        ObjectHelper.notNull(socketTimeout, "socketTimeout");
        this.connectionTimeoutMilliseconds = (int) ObjectHelper.notNull(connectionTimeout, "connectionTimeout").toMillis();
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
                .add(new RequestExpectContinue(true)).build();

        HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection> connFactory
                = (route, config) -> new AS2BHttpClientConnection(UUID.randomUUID().toString(), 8 * 1024);

        if (sslContext == null) {
            connectionPoolManager = new PoolingHttpClientConnectionManager(connFactory);
        } else {
            SSLConnectionSocketFactory sslConnectionSocketFactory;
            if (hostnameVerifier == null) {
                sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext);
            } else {
                sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
            }
            Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create()
                    .register("http", PlainConnectionSocketFactory.getSocketFactory())
                    .register("https", sslConnectionSocketFactory)
                    .build();
            connectionPoolManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry, connFactory);
        }
        connectionPoolManager.setMaxTotal(connectionPoolMaxSize);
        connectionPoolManager.setSocketConfig(targetHost,
                SocketConfig.copy(SocketConfig.DEFAULT)
                        .setSoTimeout((int) socketTimeout.toMillis())
                        .build());

        connectionKeepAliveStrategy = (response, context) -> {
            int ttl = (int) connectionPoolTtl.toMillis();
            for (Header h : response.getAllHeaders()) {
                if (HTTP.CONN_DIRECTIVE.equalsIgnoreCase(h.getName())) {
                    if (HTTP.CONN_CLOSE.equalsIgnoreCase(h.getValue())) {
                        ttl = -1;
                    }
                }
                if (HTTP.CONN_KEEP_ALIVE.equalsIgnoreCase(h.getName())) {
                    HeaderElement headerElement = h.getElements()[0];
                    if (headerElement.getValue() != null && "timeout".equalsIgnoreCase(headerElement.getName())) {
                        ttl = Integer.parseInt(headerElement.getValue()) * 1000;
                    }
                }
            }
            return ttl;
        };

        // Check if a connection can be established
        try (AS2BHttpClientConnection testConnection = new AS2BHttpClientConnection("test", 8 * 1024)) {
            if (sslContext == null) {
                testConnection.bind(new Socket(targetHost.getHostName(), targetHost.getPort()));
            } else {
                SSLSocketFactory factory = sslContext.getSocketFactory();
                testConnection.bind(factory.createSocket(targetHost.getHostName(), targetHost.getPort()));
            }
        }

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

    public HttpResponse send(HttpRequest request, HttpCoreContext httpContext)
            throws HttpException, IOException, InterruptedException, ExecutionException {

        HttpRoute route = new HttpRoute(targetHost);

        httpContext.setTargetHost(targetHost);

        HttpClientConnection httpConnection = connectionPoolManager.requestConnection(route, null)
                .get(RETRIEVE_FROM_CONNECTION_POOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!httpConnection.isOpen()) {
            connectionPoolManager.connect(httpConnection, route, connectionTimeoutMilliseconds, httpContext);
        }

        // Execute Request
        HttpRequestExecutor httpexecutor = new HttpRequestExecutor();
        httpexecutor.preProcess(request, httpProcessor, httpContext);
        HttpResponse response = httpexecutor.execute(request, httpConnection, httpContext);
        httpexecutor.postProcess(response, httpProcessor, httpContext);
        connectionPoolManager.routeComplete(httpConnection, route, httpContext);
        connectionPoolManager.releaseConnection(httpConnection, null,
                connectionKeepAliveStrategy.getKeepAliveDuration(response, httpContext), TimeUnit.MILLISECONDS);

        return response;
    }

}
