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
import java.io.UnsupportedEncodingException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mortbay.io.Buffer;
import org.mortbay.jetty.HttpHeaders;
import org.mortbay.jetty.client.ContentExchange;
import org.mortbay.jetty.client.HttpClient;
import org.mortbay.jetty.client.HttpExchange;

/**
 * Jetty specific exchange which keeps track of the the request and response.
 *
 * @version $Revision$
 */
public class JettyContentExchange extends ContentExchange {

    private static final transient Log LOG = LogFactory.getLog(JettyContentExchange.class);

    private final Map<String, Object> headers = new LinkedHashMap<String, Object>();
    private volatile Exchange exchange;
    private volatile AsyncCallback callback;
    private volatile JettyHttpBinding jettyBinding;
    private volatile HttpClient client;

    public JettyContentExchange(Exchange exchange, JettyHttpBinding jettyBinding, HttpClient client) {
        super(true); // keep headers by default
        this.exchange = exchange;
        this.jettyBinding = jettyBinding;
        this.client = client;
    }

    public void setCallback(AsyncCallback callback) {
        this.callback = callback;
    }

    @Override
    protected void onResponseHeader(Buffer name, Buffer value) throws IOException {
        super.onResponseHeader(name, value);
        headers.put(name.toString(), value.toString());
    }

    @Override
    protected void onResponseComplete() {
        doTaskCompleted();
    }

    @Override
    protected void onExpire() {
        doTaskCompleted();
    }

    @Override
    protected void onException(Throwable ex) {
        doTaskCompleted(ex);
    }

    @Override
    protected void onConnectionFailed(Throwable ex) {
        doTaskCompleted(ex);
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public String getBody() throws UnsupportedEncodingException {
        return super.getResponseContent();
    }

    public String getUrl() {
        String params = getRequestFields().getStringField(HttpHeaders.CONTENT_ENCODING);
        return getScheme() + "//" + getAddress().toString() + getURI() + (params != null ? "?" + params : "");
    }

    protected void doTaskCompleted() {
        if (callback == null) {
            // this is only for the async callback
            return;
        }

        int exchangeState = getStatus();

        if (LOG.isDebugEnabled()) {
            LOG.debug("TaskComplete with state " + exchangeState + " for url: " + getUrl());
        }

        try {
            if (exchangeState == HttpExchange.STATUS_COMPLETED) {
                // process the response as the state is ok
                try {
                    jettyBinding.populateResponse(exchange, this);
                } catch (Exception e) {
                    exchange.setException(e);
                }
            } else if (exchangeState == HttpExchange.STATUS_EXPIRED) {
                // we did timeout
                exchange.setException(new ExchangeTimedOutException(exchange, client.getTimeout()));
            } else if (exchangeState == HttpExchange.STATUS_EXCEPTED) {
                // some kind of other error
                exchange.setException(new CamelExchangeException("JettyClient failed with state " + exchangeState, exchange));
            }
        } finally {
            // now invoke callback
            callback.onTaskCompleted(exchange);
        }
    }

    protected void doTaskCompleted(Throwable ex) {
        // some kind of other error
        exchange.setException(new CamelExchangeException("JettyClient failed cause by: " + ex.getMessage(), exchange, ex));
        callback.onTaskCompleted(exchange);
    }

}
