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

import org.apache.http.*;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpParamsLinker;
import org.apache.http.protocol.*;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: gnodet
 * Date: Sep 11, 2007
 * Time: 6:57:34 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class AsyncBufferingHttpServiceHandler extends BufferingHttpServiceHandler {

    public interface AsyncHandler {

        void sendResponse(HttpResponse response) throws IOException, HttpException;

    }

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
        HttpVersion ver = request.getRequestLine().getHttpVersion();

        if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
            // Downgrade protocol version if greater than HTTP/1.1
            ver = HttpVersion.HTTP_1_1;
        }


        context.setAttribute(HttpExecutionContext.HTTP_REQUEST, request);
        context.setAttribute(HttpExecutionContext.HTTP_CONNECTION, conn);

        try {

            this.httpProcessor.process(request, context);

            HttpRequestHandler handler = null;
            if (this.handlerResolver != null) {
                String requestURI = request.getRequestLine().getUri();
                handler = this.handlerResolver.lookup(requestURI);
            }
            if (handler != null) {
                HttpResponse response = this.responseFactory.newHttpResponse(
                        ver,
                        HttpStatus.SC_OK,
                        conn.getContext());
                HttpParamsLinker.link(response, this.params);
                context.setAttribute(HttpExecutionContext.HTTP_RESPONSE, response);
                handler.handle(request, response, context);
                sendResponse(conn, response);
            } else {
                asyncProcessRequest(request, context, new AsyncHandler() {
                    public void sendResponse(HttpResponse response) throws IOException, HttpException {
                        try {
                            AsyncBufferingHttpServiceHandler.this.sendResponse(conn, response);
                        } catch (HttpException ex) {
                            response = AsyncBufferingHttpServiceHandler.this.responseFactory.newHttpResponse(
                                        HttpVersion.HTTP_1_0,
                                        HttpStatus.SC_INTERNAL_SERVER_ERROR,
                                        conn.getContext());
                            HttpParamsLinker.link(response, AsyncBufferingHttpServiceHandler.this.params);
                            AsyncBufferingHttpServiceHandler.this.handleException(ex, response);
                            AsyncBufferingHttpServiceHandler.this.sendResponse(conn, response);
                        }
                    }
                });
            }

        } catch (HttpException ex) {
            HttpResponse response = this.responseFactory.newHttpResponse(
                        HttpVersion.HTTP_1_0,
                        HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    context);
            HttpParamsLinker.link(response, this.params);
            handleException(ex, response);
            sendResponse(conn, response);
        }

    }

    protected abstract void asyncProcessRequest(HttpRequest requet, HttpContext context, AsyncHandler handler);

}
