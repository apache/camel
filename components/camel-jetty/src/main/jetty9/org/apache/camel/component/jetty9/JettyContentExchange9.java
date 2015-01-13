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
package org.apache.camel.component.jetty9;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.component.jetty.JettyContentExchange;
import org.apache.camel.component.jetty.JettyHttpBinding;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.client.util.InputStreamContentProvider;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jetty specific exchange which keeps track of the the request and response.
 *
 * @version 
 */
public class JettyContentExchange9 implements JettyContentExchange {

    private static final Logger LOG = LoggerFactory.getLogger(JettyContentExchange9.class);

    private volatile Exchange exchange;
    private volatile AsyncCallback callback;
    private volatile JettyHttpBinding jettyBinding;
    private volatile HttpClient client;
    private final CountDownLatch done = new CountDownLatch(1);
    private Request request;
    private Response response;
    private byte[] responseContent;

    private String requestContentType;

    private boolean supportRedirect;

    public JettyContentExchange9(Exchange exchange, JettyHttpBinding jettyBinding, 
                                final HttpClient client) {
        super(); // keep headers by default
        this.exchange = exchange;
        this.jettyBinding = jettyBinding;
        this.client = client;
    }

    /* (non-Javadoc)
     * @see org.apache.camel.component.jetty.JettyContentExchangeI#setCallback(org.apache.camel.AsyncCallback)
     */
    public void setCallback(AsyncCallback callback) {
        this.callback = callback;
    }

    protected void onRequestComplete() {
        LOG.trace("onRequestComplete");
        closeRequestContentSource();
    }

    protected void onResponseComplete(Result result, byte[] content, String contentType) {
        LOG.trace("onResponseComplete");
        done.countDown();
        this.response = result.getResponse();
        this.responseContent = content;
        if (callback == null) {
            // this is only for the async callback
            return;
        }
        try {
            jettyBinding.populateResponse(exchange, this);
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            callback.done(false);
        }
    }

    protected void onExpire() {
        LOG.trace("onExpire");

        // need to close the request input stream
        closeRequestContentSource();
        doTaskCompleted(new ExchangeTimedOutException(exchange, client.getConnectTimeout()));
    }

    protected void onException(Throwable ex) {
        LOG.trace("onException {}", ex);

        // need to close the request input stream
        closeRequestContentSource();
        doTaskCompleted(ex);
    }

    protected void onConnectionFailed(Throwable ex) {
        LOG.trace("onConnectionFailed {}", ex);

        // need to close the request input stream
        closeRequestContentSource();
        doTaskCompleted(ex);
    }

    /* (non-Javadoc)
     * @see org.apache.camel.component.jetty.JettyContentExchangeI#getBody()
     */
    public byte[] getBody() {
        // must return the content as raw bytes
        return getResponseContentBytes();
    }

    /* (non-Javadoc)
     * @see org.apache.camel.component.jetty.JettyContentExchangeI#getUrl()
     */
    public String getUrl() {
        try {
            return this.request.getURI().toURL().toExternalForm();
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
    
    protected void closeRequestContentSource() {
        tryClose(this.request.getContent());
    }
    
    private void tryClose(Object obj) {
        if (obj instanceof Closeable) {
            try {
                ((Closeable)obj).close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    protected void doTaskCompleted(Throwable ex) {
        if (ex instanceof TimeoutException) {
            exchange.setException(new ExchangeTimedOutException(exchange, request.getTimeout()));
        } else {
            exchange.setException(new CamelExchangeException("JettyClient failed cause by: " + ex.getMessage(), exchange, ex));
        }
        done.countDown();

        if (callback != null) {
            // now invoke callback to indicate we are done async
            callback.done(false);
        }
    }

    /* (non-Javadoc)
     * @see org.apache.camel.component.jetty.JettyContentExchangeI#setRequestContentType(java.lang.String)
     */
    public void setRequestContentType(String contentType) {
        this.requestContentType = contentType;
    }

    /* (non-Javadoc)
     * @see org.apache.camel.component.jetty.JettyContentExchangeI#getResponseStatus()
     */
    public int getResponseStatus() {
        return this.response.getStatus();
    }

    /* (non-Javadoc)
     * @see org.apache.camel.component.jetty.JettyContentExchangeI#setMethod(java.lang.String)
     */
    public void setMethod(String method) {
        this.request.method(method);
    }
    
    /* (non-Javadoc)
     * @see org.apache.camel.component.jetty.JettyContentExchangeI#setTimeout(long)
     */
    public void setTimeout(long timeout) {
        this.request.timeout(timeout, TimeUnit.MILLISECONDS);
    }
    
    /* (non-Javadoc)
     * @see org.apache.camel.component.jetty.JettyContentExchangeI#setURL(java.lang.String)
     */
    public void setURL(String url) {
        this.request = client.newRequest(url);
    }

    /* (non-Javadoc)
     * @see org.apache.camel.component.jetty.JettyContentExchangeI#setRequestContent(byte[])
     */
    public void setRequestContent(byte[] byteArray) {
        this.request.content(new BytesContentProvider(byteArray), this.requestContentType);
    }

    /* (non-Javadoc)
     * @see org.apache.camel.component.jetty.JettyContentExchangeI#setRequestContent(java.lang.String, java.lang.String)
     */
    public void setRequestContent(String data, String charset) throws UnsupportedEncodingException {
        StringContentProvider cp = charset != null ? new StringContentProvider(data, charset) : new StringContentProvider(data);
        this.request.content(cp, this.requestContentType);
    }
    /* (non-Javadoc)
     * @see org.apache.camel.component.jetty.JettyContentExchangeI#setRequestContent(java.io.InputStream)
     */
    public void setRequestContent(InputStream ins) {
        this.request.content(new InputStreamContentProvider(ins), this.requestContentType);        
    }

    /* (non-Javadoc)
     * @see org.apache.camel.component.jetty.JettyContentExchangeI#addRequestHeader(java.lang.String, java.lang.String)
     */
    public void addRequestHeader(String key, String s) {
        this.request.header(key, s);
    }

    /* (non-Javadoc)
     * @see org.apache.camel.component.jetty.JettyContentExchangeI#send(org.eclipse.jetty.client.HttpClient)
     */
    public void send(HttpClient client) throws IOException {
        org.eclipse.jetty.client.api.Request.Listener listener = new Request.Listener.Adapter() {

            @Override
            public void onSuccess(Request request) {
                onRequestComplete();
            }

            @Override
            public void onFailure(Request request, Throwable failure) {
                onConnectionFailed(failure);
            }

        };
        BufferingResponseListener responseListener = new BufferingResponseListener() {

            @Override
            public void onComplete(Result result) {
                if (result.isFailed()) {
                    doTaskCompleted(result.getFailure());
                } else {
                    onResponseComplete(result, getContent(), getMediaType());
                }
            }
        };
        request.followRedirects(supportRedirect).listener(listener).send(responseListener);
    }

    protected void setResponse(Response response) {
        this.response = response;
    }

    /* (non-Javadoc)
     * @see org.apache.camel.component.jetty.JettyContentExchangeI#getResponseContentBytes()
     */
    public byte[] getResponseContentBytes() {
        return responseContent;
    }
    
    /* (non-Javadoc)
     * @see org.apache.camel.component.jetty.JettyContentExchangeI#getResponseHeaders()
     */
    public Map<String, Collection<String>> getResponseHeaders() {
        final HttpFields f = response.getHeaders();
        Map<String, Collection<String>> ret = new TreeMap<String, Collection<String>>(String.CASE_INSENSITIVE_ORDER);
        for (String n : f.getFieldNamesCollection()) {
            ret.put(n,  f.getValuesList(n));
        }
        return ret;
    }

    /* (non-Javadoc)
     * @see org.apache.camel.component.jetty.JettyContentExchangeI#setSupportRedirect(boolean)
     */
    public void setSupportRedirect(boolean supportRedirect) {
        this.supportRedirect = supportRedirect;
    }

}
