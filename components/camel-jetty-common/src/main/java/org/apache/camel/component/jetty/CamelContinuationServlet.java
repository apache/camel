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
package org.apache.camel.component.jetty;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.http.common.CamelServlet;
import org.apache.camel.http.common.HttpCommonEndpoint;
import org.apache.camel.http.common.HttpConstants;
import org.apache.camel.http.common.HttpConsumer;
import org.apache.camel.http.common.HttpHelper;
import org.apache.camel.http.common.HttpMessage;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationSupport;

/**
 * Servlet which leverage <a href="http://wiki.eclipse.org/Jetty/Feature/Continuations">Jetty Continuations</a>.
 *
 * @version 
 */
public class CamelContinuationServlet extends CamelServlet {

    static final String EXCHANGE_ATTRIBUTE_NAME = "CamelExchange";
    static final String EXCHANGE_ATTRIBUTE_ID = "CamelExchangeId";

    private static final long serialVersionUID = 1L;
    // we must remember expired exchanges as Jetty will initiate a new continuation when we send
    // back the error when timeout occurred, and thus in the async callback we cannot check the
    // continuation if it was previously expired. So that's why we have our own map for that
    private final Map<String, String> expiredExchanges = new ConcurrentHashMap<String, String>();

    @Override
    protected void doService(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        log.trace("Service: {}", request);

        // is there a consumer registered for the request.
        HttpConsumer consumer = getServletResolveConsumerStrategy().resolve(request, getConsumers());
        if (consumer == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // figure out if continuation is enabled and what timeout to use
        boolean useContinuation = false;
        Long continuationTimeout = null;
        HttpCommonEndpoint endpoint = consumer.getEndpoint();
        if (endpoint instanceof JettyHttpEndpoint) {
            JettyHttpEndpoint jettyEndpoint = (JettyHttpEndpoint) endpoint;
            Boolean epUseContinuation = jettyEndpoint.getUseContinuation();
            Long epContinuationTimeout = jettyEndpoint.getContinuationTimeout();
            if (epUseContinuation != null) {
                useContinuation = epUseContinuation;
            } else {
                useContinuation = jettyEndpoint.getComponent().isUseContinuation();
            }
            if (epContinuationTimeout != null) {
                continuationTimeout = epContinuationTimeout;
            } else {
                continuationTimeout = jettyEndpoint.getComponent().getContinuationTimeout();
            }
        }
        if (useContinuation) {
            log.trace("Start request with continuation timeout of {}", continuationTimeout != null ? continuationTimeout : "jetty default");
        } else {
            log.trace("Usage of continuation is disabled, either by component or endpoint configuration, fallback to normal servlet processing instead");
            super.doService(request, response);
            return;
        }

        // if its an OPTIONS request then return which method is allowed
        if ("OPTIONS".equals(request.getMethod()) && !consumer.isOptionsEnabled()) {
            String s;
            if (consumer.getEndpoint().getHttpMethodRestrict() != null) {
                s = "OPTIONS," + consumer.getEndpoint().getHttpMethodRestrict();
            } else {
                // allow them all
                s = "GET,HEAD,POST,PUT,DELETE,TRACE,OPTIONS,CONNECT,PATCH";
            }
            response.addHeader("Allow", s);
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        if (consumer.getEndpoint().getHttpMethodRestrict() != null) {
            Iterator<?> it = ObjectHelper.createIterable(consumer.getEndpoint().getHttpMethodRestrict()).iterator();
            boolean match = false;
            while (it.hasNext()) {
                String method = it.next().toString();
                if (method.equalsIgnoreCase(request.getMethod())) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }
        }

        if ("TRACE".equals(request.getMethod()) && !consumer.isTraceEnabled()) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        // we do not support java serialized objects unless explicit enabled
        String contentType = request.getContentType();
        if (HttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT.equals(contentType) && !consumer.getEndpoint().getComponent().isAllowJavaSerializedObject()) {
            response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }
        
        final Exchange result = (Exchange) request.getAttribute(EXCHANGE_ATTRIBUTE_NAME);
        if (result == null) {
            // no asynchronous result so leverage continuation
            final Continuation continuation = ContinuationSupport.getContinuation(request);
            if (continuation.isInitial() && continuationTimeout != null) {
                // set timeout on initial
                continuation.setTimeout(continuationTimeout);
            }

            // are we suspended and a request is dispatched initially?
            if (consumer.isSuspended() && continuation.isInitial()) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            }

            if (continuation.isExpired()) {
                String id = (String) continuation.getAttribute(EXCHANGE_ATTRIBUTE_ID);
                // remember this id as expired
                expiredExchanges.put(id, id);
                log.warn("Continuation expired of exchangeId: {}", id);
                consumer.getBinding().doWriteExceptionResponse(new TimeoutException(), response);
                return;
            }

            // a new request so create an exchange
            final Exchange exchange = consumer.getEndpoint().createExchange(ExchangePattern.InOut);

            if (consumer.getEndpoint().isBridgeEndpoint()) {
                exchange.setProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.TRUE);
                exchange.setProperty(Exchange.SKIP_WWW_FORM_URLENCODED, Boolean.TRUE);
            }
            if (consumer.getEndpoint().isDisableStreamCache()) {
                exchange.setProperty(Exchange.DISABLE_HTTP_STREAM_CACHE, Boolean.TRUE);
            }
            
            HttpHelper.setCharsetFromContentType(request.getContentType(), exchange);
            
            exchange.setIn(new HttpMessage(exchange, consumer.getEndpoint(), request, response));
            // set context path as header
            String contextPath = consumer.getEndpoint().getPath();
            exchange.getIn().setHeader("CamelServletContextPath", contextPath);

            updateHttpPath(exchange, contextPath);

            if (log.isTraceEnabled()) {
                log.trace("Suspending continuation of exchangeId: {}", exchange.getExchangeId());
            }
            continuation.setAttribute(EXCHANGE_ATTRIBUTE_ID, exchange.getExchangeId());

            // we want to handle the UoW
            try {
                consumer.createUoW(exchange);
            } catch (Exception e) {
                log.error("Error processing request", e);
                throw new ServletException(e);
            }

            // must suspend before we process the exchange
            continuation.suspend();

            ClassLoader oldTccl = overrideTccl(exchange);

            if (log.isTraceEnabled()) {
                log.trace("Processing request for exchangeId: {}", exchange.getExchangeId());
            }
            // use the asynchronous API to process the exchange
            
            consumer.getAsyncProcessor().process(exchange, new AsyncCallback() {
                public void done(boolean doneSync) {
                    // check if the exchange id is already expired
                    boolean expired = expiredExchanges.remove(exchange.getExchangeId()) != null;
                    if (!expired) {
                        if (log.isTraceEnabled()) {
                            log.trace("Resuming continuation of exchangeId: {}", exchange.getExchangeId());
                        }
                        // resume processing after both, sync and async callbacks
                        continuation.setAttribute(EXCHANGE_ATTRIBUTE_NAME, exchange);
                        continuation.resume();
                    } else {
                        log.warn("Cannot resume expired continuation of exchangeId: {}", exchange.getExchangeId());
                    }
                }
            });

            if (oldTccl != null) {
                restoreTccl(exchange, oldTccl);
            }
            
            // return to let Jetty continuation to work as it will resubmit and invoke the service
            // method again when its resumed
            return;
        }

        try {
            // now lets output to the response
            if (log.isTraceEnabled()) {
                log.trace("Resumed continuation and writing response for exchangeId: {}", result.getExchangeId());
            }
            Integer bs = consumer.getEndpoint().getResponseBufferSize();
            if (bs != null) {
                log.trace("Using response buffer size: {}", bs);
                response.setBufferSize(bs);
            }
            consumer.getBinding().writeResponse(result, response);
        } catch (IOException e) {
            log.error("Error processing request", e);
            throw e;
        } catch (Exception e) {
            log.error("Error processing request", e);
            throw new ServletException(e);
        } finally {
            consumer.doneUoW(result);
        }
    }

    private void updateHttpPath(Exchange exchange, String contextPath) {
        String httpPath = (String) exchange.getIn().getHeader(Exchange.HTTP_PATH);
        // encode context path in case it contains unsafe chars, because HTTP_PATH isn't decoded at this moment
        String encodedContextPath = UnsafeUriCharactersEncoder.encodeHttpURI(contextPath);

        // here we just remove the CamelServletContextPath part from the HTTP_PATH
        if (contextPath != null && httpPath.startsWith(encodedContextPath)) {
            exchange.getIn().setHeader(Exchange.HTTP_PATH, httpPath.substring(encodedContextPath.length()));
        }
    }

    @Override
    public void destroy() {
        expiredExchanges.clear();
        super.destroy();
    }

}
