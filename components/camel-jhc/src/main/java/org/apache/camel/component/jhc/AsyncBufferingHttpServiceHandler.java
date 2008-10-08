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

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;

/**
 * Created by IntelliJ IDEA.
 * User: gnodet
 * Date: Sep 11, 2007
 * Time: 6:57:34 PM
 * To change this template use File | Settings | File Templates.
 */
public class AsyncBufferingHttpServiceHandler extends BufferingHttpServiceHandler {


    public AsyncBufferingHttpServiceHandler(final HttpParams params) {
        super(createDefaultProcessor(),
              new DefaultHttpResponseFactory(),
              new DefaultConnectionReuseStrategy(),
              params);
    }

    public AsyncBufferingHttpServiceHandler(final HttpProcessor httpProcessor,
                                            final HttpResponseFactory responseFactory,
                                            final ConnectionReuseStrategy connStrategy,
                                            final HttpParams params) {
        super(httpProcessor, responseFactory, connStrategy, params);
    }

    public AsyncBufferingHttpServiceHandler(final HttpProcessor httpProcessor,
                                            final HttpResponseFactory responseFactory,
                                            final ConnectionReuseStrategy connStrategy,
                                            final ByteBufferAllocator allocator,
                                            final HttpParams params) {
        super(httpProcessor, responseFactory, connStrategy, allocator, params);
    }

    protected static HttpProcessor createDefaultProcessor() {
        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new ResponseDate());
        httpproc.addInterceptor(new ResponseServer());
        httpproc.addInterceptor(new ResponseContent());
        httpproc.addInterceptor(new ResponseConnControl());
        return httpproc;
    }

    protected void processRequest(
            final NHttpServerConnection conn,
            final HttpRequest request) throws IOException, HttpException {

        HttpContext context = conn.getContext();
        ProtocolVersion ver = request.getRequestLine().getProtocolVersion();

        if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
            // Downgrade protocol version if greater than HTTP/1.1
            ver = HttpVersion.HTTP_1_1;
        }


        context.setAttribute(ExecutionContext.HTTP_REQUEST, request);
        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);

        try {

            this.httpProcessor.process(request, context);

            HttpRequestHandler handler = null;
            if (handlerResolver != null) {
                String requestURI = request.getRequestLine().getUri();
                handler = handlerResolver.lookup(requestURI);
            }
            if (handler != null) {
                if (handler instanceof AsyncHttpRequestHandler) {
                    ((AsyncHttpRequestHandler)handler).handle(request, context, new AsyncResponseHandler() {
                        public void sendResponse(HttpResponse response) throws IOException, HttpException {
                            try {
                                AsyncBufferingHttpServiceHandler.this.sendResponse(conn, response);
                            } catch (HttpException ex) {
                                response = AsyncBufferingHttpServiceHandler.this.responseFactory.newHttpResponse(
                                    HttpVersion.HTTP_1_0, HttpStatus.SC_INTERNAL_SERVER_ERROR, conn.getContext());
                                response.setParams(AsyncBufferingHttpServiceHandler.this.params);
                                AsyncBufferingHttpServiceHandler.this.handleException(ex, response);
                                AsyncBufferingHttpServiceHandler.this.sendResponse(conn, response);
                            }
                        }
                    });
                } else { // just hanlder the request with sync request handler
                    HttpResponse response = this.responseFactory.newHttpResponse(
                        ver, HttpStatus.SC_OK, conn.getContext());
                    response.setParams(this.params);
                    context.setAttribute(ExecutionContext.HTTP_RESPONSE, response);
                    handler.handle(request, response, context);
                    sendResponse(conn, response);
                }
            } else {
                // add the default handler here
                HttpResponse response = this.responseFactory.newHttpResponse(
                    ver, HttpStatus.SC_OK, conn.getContext());
                response.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
            }
        } catch (HttpException ex) {
            HttpResponse response = this.responseFactory.newHttpResponse(
                HttpVersion.HTTP_1_0, HttpStatus.SC_INTERNAL_SERVER_ERROR, context);
            response.setParams(this.params);
            handleException(ex, response);
            sendResponse(conn, response);
        }
    }
}
