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
package org.apache.camel.component.jhc;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.nio.DefaultClientIOEventDispatch;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.protocol.BufferingHttpClientHandler;
import org.apache.http.nio.protocol.EventListener;
import org.apache.http.nio.protocol.HttpRequestExecutionHandler;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.nio.reactor.SessionRequestCallback;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.util.concurrent.ThreadFactory;

public class JhcProducer extends DefaultProducer<JhcExchange> implements AsyncProcessor {
    public static final String HTTP_RESPONSE_CODE = "http.responseCode";
    // This should be a set of lower-case strings
    public static final Set<String> HEADERS_TO_SKIP = new HashSet<String>(Arrays.asList(
            "content-length", "content-type", HTTP_RESPONSE_CODE.toLowerCase()));

    private static final transient Log LOG = LogFactory.getLog(JhcProducer.class);

    private int nbThreads = 2;
    private ConnectingIOReactor ioReactor;
    private ThreadFactory threadFactory;
    private Thread runner;

    public JhcProducer(JhcEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public JhcEndpoint getEndpoint() {
        return (JhcEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        HttpParams params = getEndpoint().getParams();
        ioReactor = new DefaultConnectingIOReactor(nbThreads, threadFactory, params);
        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new RequestContent());
        httpproc.addInterceptor(new RequestTargetHost());
        httpproc.addInterceptor(new RequestConnControl());
        httpproc.addInterceptor(new RequestUserAgent());
        httpproc.addInterceptor(new RequestExpectContinue());
        BufferingHttpClientHandler handler = new BufferingHttpClientHandler(
                httpproc,
                new MyHttpRequestExecutionHandler(),
                new DefaultConnectionReuseStrategy(),
                params);
        handler.setEventListener(new EventLogger());
        final IOEventDispatch ioEventDispatch = new DefaultClientIOEventDispatch(handler, params);
        runner = new Thread(new Runnable() {
            public void run() {
                try {
                    ioReactor.execute(ioEventDispatch);
                } catch (InterruptedIOException ex) {
                    LOG.info("Interrupted");
                } catch (IOException e) {
                    LOG.warn("I/O error: " + e.getMessage());
                }
                LOG.debug("Shutdown");
            }

        });
        runner.start();
    }

    @Override
    protected void doStop() throws Exception {
        ioReactor.shutdown();
        runner.join();
        super.doStop();
    }

    public void process(Exchange exchange) throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("process: " + exchange);
        }
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("processAsync: " + exchange);
        }
        SocketAddress addr = new InetSocketAddress(getEndpoint().getHost(), getEndpoint().getPort());
        exchange.setProperty(AsyncCallback.class.getName(), callback);
        SessionRequest req = ioReactor.connect(addr, null, exchange, new MySessionRequestCallback());
        return false;
    }

    protected HttpRequest createRequest(Exchange exchange) {
        String uri = getEndpoint().getEndpointUri();
        HttpEntity entity = createEntity(exchange);
        HttpRequest req;
        if (entity == null) {
            req = new BasicHttpRequest("GET", getEndpoint().getPath());
        } else {
            req = new BasicHttpEntityEnclosingRequest("POST", getEndpoint().getPath());
            ((BasicHttpEntityEnclosingRequest)req).setEntity(entity);
        }

        // propagate headers as HTTP headers
        for (String headerName : exchange.getIn().getHeaders().keySet()) {
            String headerValue = exchange.getIn().getHeader(headerName, String.class);
            if (shouldHeaderBePropagated(headerName, headerValue)) {
                req.addHeader(headerName, headerValue);
            }
        }

        return req;
    }

    protected HttpEntity createEntity(Exchange exchange) {
        Message in = exchange.getIn();
        HttpEntity entity = in.getBody(HttpEntity.class);
        if (entity == null) {
            byte[] data = in.getBody(byte[].class);
            if (data == null) {
                return null;
            }
            entity = new ByteArrayEntity(data);
            String contentType = in.getHeader("Content-Type", String.class);
            if (contentType != null) {
                ((ByteArrayEntity) entity).setContentType(contentType);
            }
            String contentEncoding = in.getHeader("Content-Encoding", String.class);
            if (contentEncoding != null) {
                ((ByteArrayEntity) entity).setContentEncoding(contentEncoding);
            }
        }
        return entity;
    }

    // TODO Should somehow reference to HttpProducer as now it is copy/paste
    protected boolean shouldHeaderBePropagated(String headerName, String headerValue) {
        if (headerValue == null) {
            return false;
        }
        if (headerName.startsWith("org.apache.camel")) {
            return false;
        }
        if (HEADERS_TO_SKIP.contains(headerName.toLowerCase())) {
            return false;
        }
        return true;
    }

    static class MySessionRequestCallback implements SessionRequestCallback {

        public void completed(SessionRequest sessionRequest) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Completed");
            }
        }

        public void failed(SessionRequest sessionRequest) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Failed");
            }
        }

        public void timeout(SessionRequest sessionRequest) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Timeout");
            }
        }

        public void cancelled(SessionRequest sessionRequest) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Cancelled");
            }
        }
    }

    class MyHttpRequestExecutionHandler implements HttpRequestExecutionHandler {

        private static final String REQUEST_SENT       = "request-sent";
        private static final String RESPONSE_RECEIVED  = "response-received";

        public void initalizeContext(HttpContext httpContext, Object o) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Initialize context");
            }
            httpContext.setAttribute(Exchange.class.getName(), (Exchange) o);
        }

        public HttpRequest submitRequest(HttpContext httpContext) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Submit request: " + httpContext);
            }
            Object flag = httpContext.getAttribute(REQUEST_SENT);
            if (flag == null) {
                // Stick some object into the context
                httpContext.setAttribute(REQUEST_SENT, Boolean.TRUE);
                Exchange e = (Exchange) httpContext.getAttribute(Exchange.class.getName());
                return createRequest(e);
            } else {
                return null;
            }
        }

        public void handleResponse(HttpResponse httpResponse, HttpContext httpContext) throws IOException {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Handle response");
            }
            httpContext.setAttribute(RESPONSE_RECEIVED, Boolean.TRUE);
            Exchange e = (Exchange) httpContext.getAttribute(Exchange.class.getName());
            e.getOut().setBody(httpResponse.getEntity());
            for (Iterator it = httpResponse.headerIterator(); it.hasNext();) {
                Header h = (Header) it.next();
                e.getOut().setHeader(h.getName(), h.getValue());
            }
            e.getOut().setHeader(HTTP_RESPONSE_CODE, httpResponse.getStatusLine().getStatusCode());
            AsyncCallback callback = (AsyncCallback) e.removeProperty(AsyncCallback.class.getName());
            callback.done(false);
        }

        public void finalizeContext(HttpContext httpContext) {
        }
    }

    static class EventLogger implements EventListener {

        public void connectionOpen(final NHttpConnection conn) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Connection open: " + conn);
            }
        }

        public void connectionTimeout(final NHttpConnection conn) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Connection timed out: " + conn);
            }
        }

        public void connectionClosed(final NHttpConnection conn) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Connection closed: " + conn);
            }
        }

        public void fatalIOException(final IOException ex, final NHttpConnection conn) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("I/O error: " + ex.getMessage());
            }
        }

        public void fatalProtocolException(final HttpException ex, final NHttpConnection conn) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("HTTP error: " + ex.getMessage());
            }
        }

    }

}
