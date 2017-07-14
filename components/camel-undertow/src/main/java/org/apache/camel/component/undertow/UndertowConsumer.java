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
package org.apache.camel.component.undertow;

import java.io.IOException;
import java.nio.ByteBuffer;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.EagerFormParsingHandler;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.MimeMappings;
import io.undertow.util.StatusCodes;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Undertow consumer which is also an Undertow HttpHandler implementation to handle incoming request.
 */
public class UndertowConsumer extends DefaultConsumer implements HttpHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UndertowConsumer.class);

    private HttpHandlerRegistrationInfo registrationInfo;

    public UndertowConsumer(UndertowEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public UndertowEndpoint getEndpoint() {
        return (UndertowEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        getEndpoint().getComponent().registerConsumer(this);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        getEndpoint().getComponent().unregisterConsumer(this);
    }

    public HttpHandlerRegistrationInfo getHttpHandlerRegistrationInfo() {
        if (registrationInfo == null) {
            UndertowEndpoint endpoint = getEndpoint();

            registrationInfo = new HttpHandlerRegistrationInfo();
            registrationInfo.setUri(endpoint.getHttpURI());
            registrationInfo.setMethodRestrict(endpoint.getHttpMethodRestrict());
            registrationInfo.setMatchOnUriPrefix(endpoint.getMatchOnUriPrefix());
        }
        return registrationInfo;
    }

    public HttpHandler getHttpHandler() {
        // allow for HTTP 1.1 continue
        return Handlers.httpContinueRead(
                // wrap with EagerFormParsingHandler to enable undertow form parsers
                new EagerFormParsingHandler().setNext(this));
    }

    @Override
    public void handleRequest(HttpServerExchange httpExchange) throws Exception {
        HttpString requestMethod = httpExchange.getRequestMethod();

        if (Methods.OPTIONS.equals(requestMethod) && !getEndpoint().isOptionsEnabled()) {
            String allowedMethods;
            if (getEndpoint().getHttpMethodRestrict() != null) {
                allowedMethods = getEndpoint().getHttpMethodRestrict();
                if (!allowedMethods.contains("OPTIONS")) {
                    allowedMethods = "OPTIONS," + allowedMethods;
                }
            } else {
                allowedMethods = "GET,HEAD,POST,PUT,DELETE,TRACE,OPTIONS,CONNECT,PATCH";
            }
            //return list of allowed methods in response headers
            httpExchange.setStatusCode(StatusCodes.OK);
            httpExchange.getResponseHeaders().put(ExchangeHeaders.CONTENT_LENGTH, 0);
            // do not include content-type as that would indicate to the caller that we can only do text/plain
            httpExchange.getResponseHeaders().put(Headers.ALLOW, allowedMethods);
            httpExchange.getResponseSender().close();
            return;
        }

        //perform blocking operation on exchange
        if (httpExchange.isInIoThread()) {
            httpExchange.dispatch(this);
            return;
        }

        //create new Exchange
        //binding is used to extract header and payload(if available)
        Exchange camelExchange = getEndpoint().createExchange(httpExchange);

        //Unit of Work to process the Exchange
        createUoW(camelExchange);
        try {
            getProcessor().process(camelExchange);
        } catch (Exception e) {
            getExceptionHandler().handleException(e);
        } finally {
            doneUoW(camelExchange);
        }

        Object body = getResponseBody(httpExchange, camelExchange);
        TypeConverter tc = getEndpoint().getCamelContext().getTypeConverter();

        if (body == null) {
            LOG.trace("No payload to send as reply for exchange: " + camelExchange);
            httpExchange.getResponseHeaders().put(ExchangeHeaders.CONTENT_TYPE, MimeMappings.DEFAULT_MIME_MAPPINGS.get("txt"));
            httpExchange.getResponseSender().send("No response available");
        } else {
            ByteBuffer bodyAsByteBuffer = tc.convertTo(ByteBuffer.class, body);
            httpExchange.getResponseSender().send(bodyAsByteBuffer);
        }
        httpExchange.getResponseSender().close();
    }

    private Object getResponseBody(HttpServerExchange httpExchange, Exchange camelExchange) throws IOException {
        Object result;
        if (camelExchange.hasOut()) {
            result = getEndpoint().getUndertowHttpBinding().toHttpResponse(httpExchange, camelExchange.getOut());
        } else {
            result = getEndpoint().getUndertowHttpBinding().toHttpResponse(httpExchange, camelExchange.getIn());
        }
        return result;
    }

}
