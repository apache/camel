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

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static javax.servlet.http.HttpServletResponse.SC_OK;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.StreamCache;
import org.apache.camel.component.jetty.JettyContentExchange;
import org.apache.camel.component.jetty.JettyHttpBinding;
import org.apache.camel.converter.stream.OutputStreamBuilder;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.client.util.InputStreamContentProvider;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Jetty specific exchange which keeps track of the the request and response.
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

    public void init(Exchange exchange, JettyHttpBinding jettyBinding,
                     final HttpClient client, AsyncCallback callback) {
        this.exchange = exchange;
        this.jettyBinding = jettyBinding;
        this.client = client;
        this.callback = callback;
    }

    protected void onRequestComplete() {
        LOG.trace("onRequestComplete");
        closeRequestContentSource();
    }

    protected void onResponseComplete(Result result, byte[] content) {
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

    public byte[] getBody() {
        // must return the content as raw bytes
        return getResponseContentBytes();
    }

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
                ((Closeable) obj).close();
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

    public void setRequestContentType(String contentType) {
        this.requestContentType = contentType;
    }

    public int getResponseStatus() {
        return this.response.getStatus();
    }

    public void setMethod(String method) {
        this.request.method(method);
    }

    public void setTimeout(long timeout) {
        this.request.timeout(timeout, TimeUnit.MILLISECONDS);
    }

    public void setURL(String url) {
        this.request = client.newRequest(url);
    }

    public void setRequestContent(byte[] byteArray) {
        this.request.content(new BytesContentProvider(byteArray), this.requestContentType);
    }

    public void setRequestContent(String data, String charset) throws UnsupportedEncodingException {
        StringContentProvider cp = charset != null ? new StringContentProvider(data, charset) : new StringContentProvider(data);
        this.request.content(cp, this.requestContentType);
    }

    public void setRequestContent(InputStream ins) {
        this.request.content(new InputStreamContentProvider(ins), this.requestContentType);
    }

    public void setRequestContent(InputStream ins, int contentLength) {
        this.request.content(new CamelInputStreamContentProvider(ins, contentLength), this.requestContentType);
    }

    public void addRequestHeader(String key, String s) {
        this.request.header(key, s);
    }

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

        InputStreamResponseListener responseListener = new InputStreamResponseListener() {
            OutputStreamBuilder osb = OutputStreamBuilder.withExchange(exchange);

            @Override
            public void onContent(Response response, ByteBuffer content, Callback callback) {
                byte[] buffer = new byte[content.limit()];
                content.get(buffer);
                try {
                    osb.write(buffer);
                    callback.succeeded();
                } catch (IOException e) {
                    callback.failed(e);
                }
            }

            @Override
            public void onComplete(Result result) {
                if (result.isFailed()) {
                    doTaskCompleted(result.getFailure());
                } else {
                    try {
                        Object content = osb.build();
                        if (content instanceof byte[]) {
                            onResponseComplete(result, (byte[]) content);
                        } else {
                            StreamCache cos = (StreamCache) content;
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            cos.writeTo(baos);
                            onResponseComplete(result, baos.toByteArray());
                        }
                    } catch (IOException e) {
                        doTaskCompleted(e);
                    }
                }
            }
        };
        request.followRedirects(supportRedirect).listener(listener).send(responseListener);
    }

    protected void setResponse(Response response) {
        this.response = response;
    }

    public byte[] getResponseContentBytes() {
        return responseContent;
    }

    private Map<String, Collection<String>> getFieldsAsMap(HttpFields fields) {
        final Map<String, Collection<String>> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String name : getFieldNamesCollection(fields)) {
            result.put(name, fields.getValuesList(name));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Collection<String> getFieldNamesCollection(HttpFields fields) {
        try {
            return fields.getFieldNamesCollection();
        } catch (NoSuchMethodError e) {
            try {
                // In newer versions of Jetty the return type has been changed to Set.
                // This causes problems at byte-code level. Try recovering.
                Method reflGetFieldNamesCollection = HttpFields.class.getMethod("getFieldNamesCollection");
                Object result = reflGetFieldNamesCollection.invoke(fields);
                return (Collection<String>) result;
            } catch (Exception reflectionException) {
                // Suppress, throwing the original exception
                throw e;
            }
        }
    }

    public Map<String, Collection<String>> getRequestHeaders() {
        return getFieldsAsMap(request.getHeaders());
    }

    public Map<String, Collection<String>> getResponseHeaders() {
        return getFieldsAsMap(response.getHeaders());
    }

    @Override
    public void setSupportRedirect(boolean supportRedirect) {
        this.supportRedirect = supportRedirect;
    }

}
