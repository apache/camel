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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mortbay.io.Buffer;
import org.mortbay.jetty.HttpHeaders;
import org.mortbay.jetty.client.ContentExchange;

/**
 * Jetty specific exchange which keeps track of the the request and response.
 *
 * @version $Revision$
 */
public class JettyContentExchange extends ContentExchange {

    private static final transient Log LOG = LogFactory.getLog(JettyContentExchange.class);

    private final Map<String, Object> headers = new LinkedHashMap<String, Object>();
    private CountDownLatch headersComplete = new CountDownLatch(1);
    private CountDownLatch bodyComplete = new CountDownLatch(1);
    private volatile boolean failed;
    private volatile Exchange exchange;
    private volatile AsyncCallback callback;

    public JettyContentExchange() {
        // keep headers by default
        super(true);
    }

    public void setExchange(Exchange exchange) {
        this.exchange = exchange;
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
    protected void onResponseHeaderComplete() throws IOException {
        headersComplete.countDown();
        if (LOG.isDebugEnabled()) {
            LOG.debug("onResponseHeader for " + getUrl());
        }
    }

    @Override
    protected void onResponseComplete() throws IOException {
        bodyComplete.countDown();

        if (LOG.isDebugEnabled()) {
            LOG.debug("onResponseComplete for " + getUrl());
        }

        if (callback != null && exchange != null) {
            // signal we are complete
            callback.onDataReceived(exchange);
        }
    }

    @Override
    protected void onResponseStatus(Buffer version, int status, Buffer reason) throws IOException {
        super.onResponseStatus(version, status, reason);
        failed = status != 200;
    }

    public boolean isHeadersComplete() {
        return headersComplete.getCount() == 0;
    }

    public boolean isBodyComplete() {
        return bodyComplete.getCount() == 0;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public String getBody() throws UnsupportedEncodingException {
        return super.getResponseContent();
    }

    public void waitForHeadersToComplete() throws InterruptedException {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Waiting for headers to complete for " + getUrl());
        }
        headersComplete.await();
    }

    public void waitForBodyToComplete() throws InterruptedException {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Waiting for body to complete for " + getUrl());
        }
        bodyComplete.await();
    }

    public boolean waitForBodyToComplete(long timeout, TimeUnit timeUnit) throws InterruptedException {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Waiting for body to complete for " + getUrl());
        }
        return bodyComplete.await(timeout, timeUnit);
    }

    public boolean isFailed() {
        return failed;
    }

    public String getUrl() {
        String params = getRequestFields().getStringField(HttpHeaders.CONTENT_ENCODING);
        return getScheme() + "//" + getAddress().toString() + getURI() + (params != null ? "?" + params : "");
    }
}
