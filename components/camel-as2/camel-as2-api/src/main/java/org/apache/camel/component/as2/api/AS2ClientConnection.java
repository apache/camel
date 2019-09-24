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
package org.apache.camel.component.as2.api;

import org.apache.camel.component.as2.api.io.AS2BHttpClientConnection;
import org.apache.camel.component.as2.api.protocol.RequestAS2;
import org.apache.camel.component.as2.api.protocol.RequestMDN;
import org.apache.camel.component.as2.api.util.AS2HttpConnectionFactory;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.ConnectionRequest;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.HttpConnectionFactory;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeaderElementIterator;
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
import org.apache.http.util.Args;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class AS2ClientConnection {

    private HttpHost targetHost;
    private HttpRoute httpRoute;
    private HttpProcessor httpProcessor;
    private String as2Version;
    private String userAgent;
    private String clientFqdn;
    private PoolingHttpClientConnectionManager connectionManager;
    private ConnectionKeepAliveStrategy connectionKeepAliveStrategy;
    private IdleConnectionMonitorThread idleConnectionMonitorThread;

    public AS2ClientConnection(String as2Version, String userAgent, String clientFqdn, String targetHostName, Integer targetPortNumber) throws UnknownHostException, IOException {

        this.as2Version = Args.notNull(as2Version, "as2Version");
        this.userAgent = Args.notNull(userAgent, "userAgent");
        this.clientFqdn = Args.notNull(clientFqdn, "clientFqdn");
        this.targetHost = new HttpHost(Args.notNull(targetHostName, "targetHostName"), Args.notNull(targetPortNumber, "targetPortNumber"));
        this.connectionManager = new PoolingHttpClientConnectionManager(AS2HttpConnectionFactory.INSTANCE);
        this.connectionManager.setMaxTotal(3);
        this.connectionManager.setSocketConfig(targetHost, SocketConfig.custom().setSoTimeout(5000).build());
        this.connectionKeepAliveStrategy = (response, context) -> {
            HeaderElementIterator iterator = new BasicHeaderElementIterator(response.headerIterator(HTTP.CONN_KEEP_ALIVE));
            while (iterator.hasNext()) {
                HeaderElement headerElement = iterator.nextElement();
                String name = headerElement.getName();
                String value = headerElement.getValue();
                if (value != null && name.equalsIgnoreCase("timeout")) {
                    return Long.parseLong(value) * 1000;
                }
            }
            return 30000;
        };
        this.idleConnectionMonitorThread = new IdleConnectionMonitorThread(this.connectionManager);
        //idleConnectionMonitorThread.join(1000);

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

        // Create route
        this.httpRoute = new HttpRoute(new HttpHost(targetHost.getHostName(), targetHost.getPort()));

        HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection> httpConnectionFactory = new HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection>() {
            @Override
            public ManagedHttpClientConnection create(HttpRoute route, ConnectionConfig config) {
                return null;
            }
        };

        // Create Connection
//        new AS2BHttpClientConnection(8 * 1024);
        // httpConnection = new AS2BHttpClientConnection(8 * 1024);
        // httpConnection.bind(socket);
        idleConnectionMonitorThread.start();
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

    public HttpResponse send(HttpRequest request, HttpCoreContext httpContext) throws HttpException, IOException {

        // Connection pooling
        ConnectionRequest connectionRequest = connectionManager.requestConnection(httpRoute, null);
        HttpClientConnection httpClientConnection = null;
        try {
            httpClientConnection = connectionRequest.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new HttpException(e.getMessage(), e.getCause());
        } catch (ExecutionException e) {
            throw new HttpException(e.getMessage(), e.getCause());
        }

        connectionManager.connect(httpClientConnection, httpRoute, 5000, httpContext);
        connectionManager.routeComplete(httpClientConnection, httpRoute, httpContext);

        httpContext.setTargetHost(targetHost);

        // Execute Request
        HttpRequestExecutor httpexecutor = new HttpRequestExecutor();
        httpexecutor.preProcess(request, httpProcessor, httpContext);
        HttpResponse response = httpexecutor.execute(request, httpClientConnection, httpContext);
        httpexecutor.postProcess(response, httpProcessor, httpContext);

        connectionManager.releaseConnection(httpClientConnection, null, 1, TimeUnit.SECONDS);

        return response;
    }

    public void doShutdown() throws Exception {
        idleConnectionMonitorThread.shutdown();
        connectionManager.shutdown();
    }

    private class IdleConnectionMonitorThread extends Thread {

        private final HttpClientConnectionManager connectionManager;
        private volatile boolean shutdown;

        public IdleConnectionMonitorThread(PoolingHttpClientConnectionManager connectionManager) {
            super();
            this.connectionManager = connectionManager;
        }

        @Override
        public void run() {
            try {
                while (!shutdown) {
                    synchronized (this) {
                        wait(2500);
                        connectionManager.closeExpiredConnections();
                        connectionManager.closeIdleConnections(30, TimeUnit.SECONDS);
                    }
                }
            } catch (InterruptedException ex) {
                shutdown();
            }
        }

        public void shutdown() {
            shutdown = true;
            synchronized (this) {
                notifyAll();
            }
        }
    }

}
