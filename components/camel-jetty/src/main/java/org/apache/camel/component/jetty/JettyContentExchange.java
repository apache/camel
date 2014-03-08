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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.eclipse.jetty.client.ContentExchange;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.io.Buffer;
import org.eclipse.jetty.io.ByteArrayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jetty specific exchange which keeps track of the the request and response.
 *
 * @version 
 */
public class JettyContentExchange extends ContentExchange {

    private static final Logger LOG = LoggerFactory.getLogger(JettyContentExchange.class);

    private final Map<String, String> headers = new LinkedHashMap<String, String>();
    private volatile Exchange exchange;
    private volatile AsyncCallback callback;
    private volatile JettyHttpBinding jettyBinding;
    private volatile HttpClient client;
    private final CountDownLatch done = new CountDownLatch(1);

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
        String k = name.toString();
        String v = value.toString();
        LOG.trace("onResponseHeader {} -> {}", k, v);

        // need to remove quotes from charset which can be returned by some http servers
        if (Exchange.CONTENT_TYPE.equalsIgnoreCase(k)) {
            String charset = ObjectHelper.after(v, "charset=");
            if (charset != null) {
                // there may be another parameter as well, we only want the charset parameter
                String extra = "";
                if (charset.contains(";")) {
                    extra = ObjectHelper.after(charset, ";");
                    charset = ObjectHelper.before(charset, ";");
                }
                charset = charset.trim();
                String s = StringHelper.removeLeadingAndEndingQuotes(charset);
                if (!charset.equals(s)) {
                    v = ObjectHelper.before(v, "charset=") + "charset=" + s;
                    LOG.debug("Removed quotes from charset in " + Exchange.CONTENT_TYPE + " from {} to {}", charset, s);
                    // add extra parameters
                    if (extra != null) {
                        v = v + ";" + extra;
                    }
                    // use a new buffer to adjust the value
                    value = new ByteArrayBuffer.CaseInsensitive(v);
                }
            }
        }

        super.onResponseHeader(name, value);
        headers.put(k, v);
    }

    @Override
    protected void onRequestComplete() throws IOException {
        LOG.trace("onRequestComplete");
        
        closeRequestContentSource();
    }

    @Override
    protected void onResponseComplete() throws IOException {
        LOG.trace("onResponseComplete");

        try {
            super.onResponseComplete();
        } finally {
            doTaskCompleted();
        }
    }

    @Override
    protected void onExpire() {
        LOG.trace("onExpire");

        try {
            super.onExpire();
        } finally {
            // need to close the request input stream
            closeRequestContentSource();
            doTaskCompleted();
        }
    }

    @Override
    protected void onException(Throwable ex) {
        LOG.trace("onException {}", ex);

        try {
            super.onException(ex);
        } finally {
            // need to close the request input stream
            closeRequestContentSource();
            doTaskCompleted(ex);
        }
    }

    @Override
    protected void onConnectionFailed(Throwable ex) {
        LOG.trace("onConnectionFailed {}", ex);

        try {
            super.onConnectionFailed(ex);
        } finally {
            // need to close the request input stream
            closeRequestContentSource();
            doTaskCompleted(ex);
        }
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        // must return the content as raw bytes
        return super.getResponseContentBytes();
    }

    public String getUrl() {
        String params = getRequestFields().getStringField(HttpHeaders.CONTENT_ENCODING);
        return getScheme() + "://" + getAddress().toString() + getRequestURI() + (params != null ? "?" + params : "");
    }
    
    protected void closeRequestContentSource() {
        // close the input stream when its not needed anymore
        InputStream is = getRequestContentSource();
        if (is != null) {
            IOHelper.close(is, "RequestContentSource", LOG);
        }
    }

    protected void doTaskCompleted() {
        // make sure to lower the latch
        done.countDown();

        if (callback == null) {
            // this is only for the async callback
            return;
        }

        int exchangeState = getStatus();

        if (LOG.isDebugEnabled()) {
            LOG.debug("TaskComplete with state {} for url: {}", exchangeState, getUrl());
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
            } else {
                // some kind of other error
                if (exchange.getException() != null) {
                    exchange.setException(new CamelExchangeException("JettyClient failed with state " + exchangeState, exchange, exchange.getException()));
                }
            }
        } finally {
            // now invoke callback to indicate we are done async
            callback.done(false);
        }
    }

    protected void doTaskCompleted(Throwable ex) {
        try {
            // some kind of other error
            exchange.setException(new CamelExchangeException("JettyClient failed cause by: " + ex.getMessage(), exchange, ex));
        } finally {
            // make sure to lower the latch
            done.countDown();
        }

        if (callback != null) {
            // now invoke callback to indicate we are done async
            callback.done(false);
        }
    }

}
