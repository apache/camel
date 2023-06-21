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
package org.apache.camel.component.salesforce;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.camel.component.salesforce.internal.client.SalesforceHttpRequest;
import org.apache.camel.component.salesforce.internal.client.SalesforceSecurityHandler;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpConversation;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.ProtocolHandler;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * Custom Salesforce HTTP Client that creates {@link SalesforceHttpRequest} requests.
 */
public class SalesforceHttpClient extends HttpClient {

    // default total request timeout in msecs
    static final long DEFAULT_TIMEOUT = 60000;

    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int DEFAULT_MAX_CONTENT_LENGTH = 4 * 1024 * 1024;
    private final CamelContext camelContext;

    private SalesforceSession session;
    private int maxRetries = DEFAULT_MAX_RETRIES;
    private int maxContentLength = DEFAULT_MAX_CONTENT_LENGTH;
    private long timeout = DEFAULT_TIMEOUT;

    private final Method addProtocolHandlerMethod;
    private final Method getProtocolHandlersMethod;

    private final ExecutorService workerPool;

    public SalesforceHttpClient() {
        this(null);
    }

    public SalesforceHttpClient(SslContextFactory.Client sslContextFactory) {
        this(null, Executors.newCachedThreadPool(), sslContextFactory);
    }

    public SalesforceHttpClient(CamelContext context, ExecutorService workerPool, SslContextFactory.Client sslContextFactory) {
        super(new HttpClientTransportOverHTTP(newConnector(sslContextFactory)));
        this.workerPool = workerPool;
        this.camelContext = context;

        // Jetty 9.3, as opposed to 9.2 the way to add ProtocolHandler to
        // HttpClient changed in 9.2 HttpClient::getProtocolHandlers returned
        // List<ProtocolHandler, in 9.3 onward it returns ProtocolHandlers
        // instance, this enables us to work with user supplied Jetty (>= 9.2)
        try {
            final Class<?> getProtocolHandlersType = HttpClient.class.getMethod("getProtocolHandlers").getReturnType();
            final boolean isJetty92 = List.class.equals(getProtocolHandlersType);
            if (isJetty92) {
                addProtocolHandlerMethod = List.class.getMethod("add", Object.class);
            } else {
                addProtocolHandlerMethod = getProtocolHandlersType.getMethod("put", ProtocolHandler.class);
            }

            getProtocolHandlersMethod = HttpClient.class.getMethod("getProtocolHandlers");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "Found no method of adding SalesforceSecurityHandler as ProtocolHandler to Jetty HttpClient. You need Jetty 9.2 or newer on the classpath.");
        }
    }

    private static ClientConnector newConnector(SslContextFactory.Client sslContextFactory) {
        ClientConnector connector = new ClientConnector();
        connector.setSslContextFactory(sslContextFactory);
        return connector;
    }

    @Override
    public HttpRequest newHttpRequest(HttpConversation conversation, URI uri) {
        final SalesforceHttpRequest request = new SalesforceHttpRequest(this, conversation, uri);
        request.timeout(timeout, TimeUnit.MILLISECONDS);
        return request;
    }

    @Override
    public Request copyRequest(HttpRequest oldRequest, URI newURI) {
        return super.copyRequest(oldRequest, newURI);
    }

    @Override
    protected void doStart() throws Exception {
        if (getSession() == null) {
            throw new IllegalStateException("Missing SalesforceSession in property session!");
        }

        // compensate for Jetty 9.2 vs 9.3 API change
        final Object protocolHandlers = getProtocolHandlersMethod.invoke(this);
        addProtocolHandlerMethod.invoke(protocolHandlers, new SalesforceSecurityHandler(this));

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (workerPool != null) {
            if (camelContext != null) {
                camelContext.getExecutorServiceManager().shutdownGraceful(workerPool);
            } else {
                workerPool.shutdown();
            }
        }
        super.doStop();
    }

    public SalesforceSession getSession() {
        return session;
    }

    public void setSession(SalesforceSession session) {
        this.session = session;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public void setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public ExecutorService getWorkerPool() {
        return workerPool;
    }
}
