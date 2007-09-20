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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.*;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.nio.DefaultServerIOEventDispatch;
import org.apache.http.impl.nio.reactor.SSLServerIOEventDispatch;
import org.apache.http.nio.*;
import org.apache.http.nio.entity.ContentOutputStream;
import org.apache.http.nio.util.ContentOutputBuffer;
import org.apache.http.nio.protocol.EventListener;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.ListeningIOReactor;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.*;
import org.apache.http.util.EncodingUtils;
import org.apache.http.util.concurrent.ThreadFactory;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SelectionKey;

/**
 * Created by IntelliJ IDEA.
 * User: gnodet
 * Date: Sep 7, 2007
 * Time: 8:15:54 PM
 * To change this template use File | Settings | File Templates.
 */
public class JhcConsumer extends DefaultConsumer<JhcExchange> {

    private static Log LOG = LogFactory.getLog(JhcConsumer.class);

    private int nbThreads = 2;
    private ListeningIOReactor ioReactor;
    private ThreadFactory threadFactory;
    private Thread runner;

    public JhcConsumer(JhcEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    public JhcEndpoint getEndpoint() {
        return (JhcEndpoint) super.getEndpoint();
    }

    protected void doStart() throws Exception {
        super.doStart();
        final SocketAddress addr = new InetSocketAddress(getEndpoint().getPort());
        HttpParams params = getEndpoint().getParams();
        ioReactor = new DefaultListeningIOReactor(nbThreads, threadFactory, params);

        final IOEventDispatch ioEventDispatch;
        if ("https".equals(getEndpoint().getProtocol())) {
            SSLContext sslContext = null; // TODO
            ioEventDispatch = new SSLServerIOEventDispatch(new MyHandler(params), sslContext, params);
        } else {
            ioEventDispatch = new DefaultServerIOEventDispatch(new MyHandler(params), params);
        }
        runner = new Thread() {
            public void run() {
                try {
                    ioReactor.listen(addr);
                    ioReactor.execute(ioEventDispatch);
                } catch (InterruptedIOException ex) {
                    LOG.info("Interrupted");
                } catch (IOException e) {
                    LOG.warn("I/O error: " + e.getMessage());
                }
                LOG.debug("Shutdown");
            }
        };
        runner.start();
    }

    protected void doStop() throws Exception {
        LOG.debug("Stopping");
        ioReactor.shutdown();
        LOG.debug("Waiting runner");
        runner.join();
        LOG.debug("Stopped");
        super.doStop();
    }

    class MyHttpRequestHandler implements HttpRequestHandler {

        public void handle(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
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


    class MyHandler extends AsyncBufferingHttpServiceHandler {
        public MyHandler(HttpParams params) {
            super(params);
        }
        protected void asyncProcessRequest(final HttpRequest request, final HttpContext context, final AsyncBufferingHttpServiceHandler.AsyncHandler handler) {
            final Exchange exchange = getEndpoint().createExchange();
            exchange.getIn().setHeader("http.uri", request.getRequestLine().getUri());
            if (request instanceof HttpEntityEnclosingRequest) {
                exchange.getIn().setBody(((HttpEntityEnclosingRequest) request).getEntity());
            }
            getAsyncProcessor().process(exchange, new AsyncCallback() {
                public void done(boolean doneSynchronously) {
                    LOG.debug("handleExchange");
                    // create the default response to this request
                    HttpVersion httpVersion = request.getRequestLine().getHttpVersion();
                    HttpResponse response = responseFactory.newHttpResponse(httpVersion, HttpStatus.SC_OK, context);
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
    }

}
