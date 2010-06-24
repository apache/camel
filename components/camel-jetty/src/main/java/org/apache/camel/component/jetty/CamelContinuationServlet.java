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
 * Currently not in use.
 *
 * @version $Revision$
 */
public class CamelContinuationServlet extends CamelServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            // Is there a consumer registered for the request.
            HttpConsumer consumer = resolve(request);
            if (consumer == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // are we suspended?
            if (consumer.isSuspended()) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            }

            final Continuation continuation = ContinuationSupport.getContinuation(request);
            if (continuation.isInitial()) {

                // a new request so create an exchange
                final Exchange exchange = new DefaultExchange(consumer.getEndpoint(), ExchangePattern.InOut);
                if (consumer.getEndpoint().isBridgeEndpoint()) {
                    exchange.setProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.TRUE);
                }
                if (consumer.getEndpoint().isDisableStreamCache()) {
                    exchange.setProperty(Exchange.DISABLE_HTTP_STREAM_CACHE, Boolean.TRUE);
                }
                exchange.setIn(new HttpMessage(exchange, request, response));

                // use the asynchronous API to process the exchange
                boolean sync = consumer.getAsyncProcessor().process(exchange, new AsyncCallback() {
                    public void done(boolean doneSync) {
                        // we only have to handle async completion
                        if (doneSync) {
                            return;
                        }

                        // we should resume the continuation now that we are done asynchronously
                        if (log.isTraceEnabled()) {
                            log.trace("Resuming continuation of exchangeId: " + exchange.getExchangeId());
                        }
                        continuation.setAttribute("CamelExchange", exchange);
                        continuation.resume();
                    }
                });

                if (!sync) {
                    // wait for the exchange to get processed.
                    // this might block until it completes or it might return via an exception and
                    // then this method is re-invoked once the the exchange has finished processing
                    if (log.isTraceEnabled()) {
                        log.trace("Suspending continuation of exchangeId: " + exchange.getExchangeId());
                    }
                    continuation.suspend(response);
                    return;
                }

                // now lets output to the response
                if (log.isTraceEnabled()) {
                    log.trace("Writing response of exchangeId: " + exchange.getExchangeId());
                }
                consumer.getBinding().writeResponse(exchange, response);
                return;
            }

            if (continuation.isResumed()) {
                Exchange exchange = (Exchange) continuation.getAttribute("CamelExchange");
                if (log.isTraceEnabled()) {
                    log.trace("Resuming continuation of exchangeId: " + exchange.getExchangeId());
                }

                // now lets output to the response
                if (log.isTraceEnabled()) {
                    log.trace("Writing response of exchangeId: " + exchange.getExchangeId());
                }
                consumer.getBinding().writeResponse(exchange, response);
                return;
            }
        } catch (Exception e) {
            log.error("Error processing request", e);
            throw new ServletException(e);
        }
    }

}
