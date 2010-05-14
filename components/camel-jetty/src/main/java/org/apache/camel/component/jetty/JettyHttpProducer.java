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
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.Message;
import org.apache.camel.component.http.HttpMethods;
import org.apache.camel.component.http.helper.HttpProducerHelper;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.io.ByteArrayBuffer;

/**
 * @version $Revision$
 */
public class JettyHttpProducer extends DefaultProducer implements AsyncProcessor {
    private static final transient Log LOG = LogFactory.getLog(JettyHttpProducer.class);
    private final HttpClient client;
    private JettyHttpBinding binding;

    public JettyHttpProducer(Endpoint endpoint, HttpClient client) {
        super(endpoint);
        this.client = client;
    }

    @Override
    public JettyHttpEndpoint getEndpoint() {
        return (JettyHttpEndpoint) super.getEndpoint();
    }

    public void process(Exchange exchange) throws Exception {
        HttpClient client = getEndpoint().getClient();

        JettyContentExchange httpExchange = createHttpExchange(exchange);
        sendSynchronous(exchange, client, httpExchange);
    }

    public boolean process(Exchange exchange, final AsyncCallback callback) {
        HttpClient client = getEndpoint().getClient();

        try {
            JettyContentExchange httpExchange = createHttpExchange(exchange);
            sendAsynchronous(exchange, client, httpExchange, callback);
        } catch (Exception e) {
            exchange.setException(e);
        }

        // we should continue processing this asynchronously
        return false;
    }

    protected void sendAsynchronous(final Exchange exchange, final HttpClient client, final JettyContentExchange httpExchange,
                                    final AsyncCallback callback) throws IOException {

        // set the callback for the async mode
        httpExchange.setCallback(callback);

        doSendExchange(client, httpExchange);

        // the callback will handle all the response handling logic
    }

    protected void sendSynchronous(Exchange exchange, HttpClient client, JettyContentExchange httpExchange) throws Exception {
        doSendExchange(client, httpExchange);

        // we send synchronous so wait for it to be done
        // must use our own lock detection as Jettys waitForDone will wait forever in case of connection issues
        try {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Waiting for HTTP exchange to be done");
            }
            int exchangeState = httpExchange.waitForDoneOrFailure();
            if (LOG.isTraceEnabled()) {
                LOG.trace("HTTP exchange is done with state " + exchangeState);
            }
            if (exchangeState == HttpExchange.STATUS_COMPLETED) {
                // process the response as the state is ok
                getBinding().populateResponse(exchange, httpExchange);
            } else if (exchangeState == HttpExchange.STATUS_EXPIRED) {
                // we did timeout
                throw new ExchangeTimedOutException(exchange, client.getTimeout());
            } else {
                // some kind of other error
                if (exchange.getException() != null) {
                    throw exchange.getException();
                } else {
                    exchange.setException(new CamelExchangeException("JettyClient failed with state " + exchangeState, exchange));
                }
            }
        } catch (InterruptedException e) {
            // are we shutting down?
            if (LOG.isDebugEnabled()) {
                LOG.debug("Interrupted waiting for async reply, are we stopping? " + (isStopping() || isStopped()));
            }
            exchange.setException(e);
        }
    }

    protected JettyContentExchange createHttpExchange(Exchange exchange) throws Exception {
        String url = HttpProducerHelper.createURL(exchange, getEndpoint());
        HttpMethods methodToUse = HttpProducerHelper.createMethod(exchange, getEndpoint(), exchange.getIn().getBody() != null);
        String method = methodToUse.createMethod(url).getName();

        JettyContentExchange httpExchange = new JettyContentExchange(exchange, getBinding(), client);
        httpExchange.setMethod(method);
        httpExchange.setURL(url);

        // set query parameters
        doSetQueryParameters(exchange, httpExchange);

        // if we post then set data
        if (HttpMethods.POST.equals(methodToUse)) {

            String contentType = ExchangeHelper.getContentType(exchange);
            if (contentType != null) {
                httpExchange.setRequestContentType(contentType);
            }

            // try with String at first
            String data = exchange.getIn().getBody(String.class);
            if (data != null) {
                String charset = exchange.getProperty(Exchange.CHARSET_NAME, String.class);
                if (charset != null) {
                    httpExchange.setRequestContent(new ByteArrayBuffer(data, charset));
                } else {
                    httpExchange.setRequestContent(new ByteArrayBuffer(data));
                }
            } else {
                // then fallback to input stream
                InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, exchange.getIn().getBody());
                httpExchange.setRequestContentSource(is);
            }
        }

        // and copy headers from IN message
        Message in = exchange.getIn();
        HeaderFilterStrategy strategy = getEndpoint().getHeaderFilterStrategy();

        // propagate headers as HTTP headers
        for (String headerName : in.getHeaders().keySet()) {
            String headerValue = in.getHeader(headerName, String.class);
            if (strategy != null && !strategy.applyFilterToCamelHeaders(headerName, headerValue, exchange)) {
                httpExchange.addRequestHeader(headerName, headerValue);
            }
        }

        return httpExchange;
    }

    @SuppressWarnings("unchecked")
    private void doSetQueryParameters(Exchange exchange, JettyContentExchange httpExchange) throws URISyntaxException {
        // is a query string provided in the endpoint URI or in a header (header
        // overrules endpoint)
        String queryString = exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class);
        if (queryString == null) {
            queryString = getEndpoint().getHttpUri().getQuery();
        }

        if (ObjectHelper.isEmpty(queryString)) {
            return;
        }

        // okay we need to add the query string to the URI so we need to juggle a bit with the parameters
        String uri = httpExchange.getURI();

        Map parameters = URISupport.parseParameters(new URI(uri));
        parameters.putAll(URISupport.parseQuery(queryString));

        if (uri.contains("?")) {
            uri = ObjectHelper.before(uri, "?");
        }
        if (!parameters.isEmpty()) {
            uri = uri + "?" + URISupport.createQueryString(parameters);
            httpExchange.setURI(uri);
        }
    }

    protected static void doSendExchange(HttpClient client, JettyContentExchange httpExchange) throws IOException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Sending HTTP request to: " + httpExchange.getUrl());
        }
        client.send(httpExchange);
    }

    public JettyHttpBinding getBinding() {
        return binding;
    }

    public void setBinding(JettyHttpBinding binding) {
        this.binding = binding;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        client.start();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        client.stop();
    }
}