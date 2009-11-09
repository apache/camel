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
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.http.HttpMethods;
import org.apache.camel.component.http.helper.HttpProducerHelper;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mortbay.jetty.client.HttpClient;

/**
 * @version $Revision$
 */
public class JettyHttpProducer extends DefaultProducer implements AsyncProcessor {
    private static final transient Log LOG = LogFactory.getLog(JettyHttpProducer.class);
    private final HttpClient client;

    // TODO: support that bridge option
    // TODO: more unit tests

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
    public void process(Exchange exchange, AsyncCallback callback) throws Exception {
        HttpClient client = getEndpoint().getClient();

        JettyContentExchange httpExchange = createHttpExchange(exchange);
        sendAsynchronous(exchange, client, httpExchange, callback);
    }

    protected void sendSynchronous(Exchange exchange, HttpClient client, JettyContentExchange httpExchange) throws IOException {
        // set the body with the message holder
        exchange.setOut(new JettyHttpMessage(exchange, httpExchange, getEndpoint().isThrowExceptionOnFailure()));

        doSendExchange(client, httpExchange);
    }

    protected void sendAsynchronous(final Exchange exchange, final HttpClient client, final JettyContentExchange httpExchange,
                                    final AsyncCallback callback) throws IOException {

        // TODO: Use something that marks it as async routed
        exchange.setProperty("CamelSendAsync", Boolean.TRUE);
        httpExchange.setCallback(callback);
        httpExchange.setExchange(exchange);

        // set the body with the message holder
        exchange.setOut(new JettyHttpMessage(exchange, httpExchange, getEndpoint().isThrowExceptionOnFailure()));

        doSendExchange(client, httpExchange);
    }

    protected void doSendExchange(HttpClient client, JettyContentExchange httpExchange) throws IOException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Sending HTTP request to: " + httpExchange.getUrl());
        }
        client.send(httpExchange);
    }

    protected JettyContentExchange createHttpExchange(Exchange exchange) throws Exception {
        String url = HttpProducerHelper.createURL(exchange, getEndpoint());
        HttpMethods methodToUse = HttpProducerHelper.createMethod(exchange, getEndpoint(), exchange.getIn().getBody() != null);
        String method = methodToUse.createMethod(url).getName();

        JettyContentExchange httpExchange = new JettyContentExchange();
        httpExchange.setMethod(method);
        httpExchange.setURL(url);

        doSetQueryParameters(exchange, httpExchange);

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

        Map<String, String> parameters = URISupport.parseQuery(queryString);
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            httpExchange.setRequestHeader(entry.getKey(), entry.getValue());
        }
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
