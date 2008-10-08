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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.protocol.EventListener;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

/**
 * Created by IntelliJ IDEA. User: gnodet Date: Sep 7, 2007 Time: 8:15:54 PM To
 * change this template use File | Settings | File Templates.
 */
public class JhcConsumer extends DefaultConsumer<JhcExchange> {

    private static final Log LOG = LogFactory.getLog(JhcConsumer.class);
    private JhcServerEngine engine;
    private MyHandler handler;

    public JhcConsumer(JhcEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        engine = JhcServerEngineFactory.getJhcServerEngine(endpoint.getParams(), endpoint.getPort(), endpoint
            .getProtocol());
        handler = new MyHandler(endpoint.getParams(), endpoint.getPath());

    }

    public JhcEndpoint getEndpoint() {
        return (JhcEndpoint)super.getEndpoint();
    }

    protected void doStart() throws Exception {
        super.doStart();
        engine.register(handler.getPath() + "*", handler);
        if (!engine.isStarted()) {
            engine.start();
        }
    }

    protected void doStop() throws Exception {
        engine.unregister(handler.getPath() + "*");
        if (engine.getReferenceCounter() == 0) {
            engine.stop();
        }
        super.doStop();
    }

    class MyHttpRequestHandler implements HttpRequestHandler {

        public void handle(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext)
            throws HttpException, IOException {
            LOG.debug("handle");
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

    class MyHandler implements AsyncHttpRequestHandler {
        private final HttpParams params;
        private final HttpResponseFactory responseFactory;
        private final String path;

        public MyHandler(HttpParams params, String path) {
            this(params, path, new DefaultHttpResponseFactory());
        }

        public MyHandler(HttpParams params, String path, HttpResponseFactory responseFactory) {
            this.params = params;
            this.path = path;
            this.responseFactory = responseFactory;
        }

        public String getPath() {
            return path;
        }

        public void handle(final HttpRequest request, final HttpContext context,
                           final AsyncResponseHandler handler) throws HttpException, IOException {
            final Exchange exchange = getEndpoint().createExchange();
            exchange.getIn().setHeader("http.uri", request.getRequestLine().getUri());
            if (request instanceof HttpEntityEnclosingRequest) {
                exchange.getIn().setBody(((HttpEntityEnclosingRequest)request).getEntity());
            }
            getAsyncProcessor().process(exchange, new AsyncCallback() {
                public void done(boolean doneSynchronously) {
                    LOG.debug("handleExchange");
                    // create the default response to this request
                    ProtocolVersion httpVersion = (HttpVersion)request.getRequestLine().getProtocolVersion();

                    HttpResponse response = responseFactory.newHttpResponse(
                        httpVersion, HttpStatus.SC_OK, context);
                    response.setParams(params);
                    HttpEntity entity = exchange.getOut().getBody(HttpEntity.class);
                    response.setEntity(entity);
                    response.setParams(getEndpoint().getParams());
                    try {
                        handler.sendResponse(response);
                    } catch (Exception e) {
                        LOG.info(e);
                    }
                }
            });
        }

        public void handle(HttpRequest request, HttpResponse response, HttpContext context)
            throws HttpException, IOException {
            // now we just handler the requset async, do nothing here
        }
    }
}
