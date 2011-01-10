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
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.http.CamelServlet;
import org.apache.camel.component.http.HttpConsumer;
import org.apache.camel.component.http.HttpMessage;
import org.apache.camel.impl.DefaultExchange;
import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationSupport;

/**
 * Servlet which leverage <a href="http://wiki.eclipse.org/Jetty/Feature/Continuations">Jetty Continuations</a>.
 *
 * @version $Revision$
 */
public class CamelContinuationServlet extends CamelServlet {

    static final String EXCHANGE_ATTRIBUTE_NAME = "CamelExchange";
    static final String EXCHANGE_ATTRIBUTE_ID = "CamelExchangeId";

    private static final long serialVersionUID = 1L;
    // jetty will by default use 30000 millis as default timeout
    private Long continuationTimeout;

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (log.isTraceEnabled()) {
            log.trace("Service: " + request);
        }

        // is there a consumer registered for the request.
        HttpConsumer consumer = resolve(request);
        if (consumer == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        final Exchange result = (Exchange) request.getAttribute(EXCHANGE_ATTRIBUTE_NAME);
        if (result == null) {
            // no asynchronous result so leverage continuation
            final Continuation continuation = ContinuationSupport.getContinuation(request);
            if (continuationTimeout != null) {
                continuation.setTimeout(continuationTimeout);
            }

            // are we suspended and a request is dispatched initially?
            if (consumer.isSuspended() && continuation.isInitial()) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            }

            if (continuation.isExpired()) {
                String id = (String) continuation.getAttribute(EXCHANGE_ATTRIBUTE_ID);
                log.warn("Continuation expired of exchangeId: " + id);
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            }

            // a new request so create an exchange
            final Exchange exchange = new DefaultExchange(consumer.getEndpoint(), ExchangePattern.InOut);
            if (consumer.getEndpoint().isBridgeEndpoint()) {
                exchange.setProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.TRUE);
            }
            if (consumer.getEndpoint().isDisableStreamCache()) {
                exchange.setProperty(Exchange.DISABLE_HTTP_STREAM_CACHE, Boolean.TRUE);
            }
            exchange.setIn(new HttpMessage(exchange, request, response));

            if (log.isTraceEnabled()) {
                log.trace("Suspending continuation of exchangeId: " + exchange.getExchangeId());
            }
            continuation.setAttribute(EXCHANGE_ATTRIBUTE_ID, exchange.getExchangeId());
            // must suspend before we process the exchange
            continuation.suspend();

            // use the asynchronous API to process the exchange
            consumer.getAsyncProcessor().process(exchange, new AsyncCallback() {
                public void done(boolean doneSync) {
                    if (log.isTraceEnabled()) {
                        log.trace("Resuming continuation of exchangeId: " + exchange.getExchangeId());
                    }
                    // resume processing after both, sync and async callbacks
                    continuation.setAttribute(EXCHANGE_ATTRIBUTE_NAME, exchange);
                    continuation.resume();
                }
            });

            // return to let Jetty continuation to work as it will resubmit and invoke the service
            // method again when its resumed
            return;
        }

        try {
            if (log.isTraceEnabled()) {
                log.trace("Resumed continuation and writing response for exchangeId: " + result.getExchangeId());
            }
            // now lets output to the response
            consumer.getBinding().writeResponse(result, response);
        } catch (IOException e) {
            log.error("Error processing request", e);
            throw e;
        }
    }

    public Long getContinuationTimeout() {
        return continuationTimeout;
    }

    public void setContinuationTimeout(Long continuationTimeout) {
        this.continuationTimeout = continuationTimeout;
    }
}
