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

    static final String EXCHANGE_ATTRIBUTE_NAME = "CamelExchange";

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

            final Continuation continuation = ContinuationSupport.getContinuation(request);

            // are we suspended and a request is dispatched initially?
            if (consumer.isSuspended() && continuation.isInitial()) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            }

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

                if (log.isTraceEnabled()) {
                    log.trace("Suspending continuation of exchangeId: " + exchange.getExchangeId());
                }
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
                return;
            }

            if (continuation.isResumed()) {
                // a re-dispatched request containing the processing result
                Exchange exchange = (Exchange) continuation.getAttribute(EXCHANGE_ATTRIBUTE_NAME);
                if (log.isTraceEnabled()) {
                    log.trace("Resuming continuation of exchangeId: " + exchange.getExchangeId());
                }

                // now lets output to the response
                if (log.isTraceEnabled()) {
                    log.trace("Writing response of exchangeId: " + exchange.getExchangeId());
                }
                consumer.getBinding().writeResponse(exchange, response);
            }
        } catch (IOException e) {
            log.error("Error processing request", e);
            throw e;
        } catch (Exception e) {
            log.error("Error processing request", e);
            throw new ServletException(e);
        }
    }

}
