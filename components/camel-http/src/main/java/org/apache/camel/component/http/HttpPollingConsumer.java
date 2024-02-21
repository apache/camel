/*
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
package org.apache.camel.component.http;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.http.common.HttpHelper;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.PollingConsumerSupport;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * A polling HTTP consumer which by default performs a GET
 */
public class HttpPollingConsumer extends PollingConsumerSupport {
    private final HttpEndpoint endpoint;
    private HttpClient httpClient;
    private final HttpContext httpContext;

    public HttpPollingConsumer(HttpEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        this.httpContext = endpoint.getHttpContext();
        this.httpClient = endpoint.getHttpClient();
    }

    @Override
    public HttpEndpoint getEndpoint() {
        return (HttpEndpoint) super.getEndpoint();
    }

    @Override
    public Exchange receive() {
        return doReceive(-1);
    }

    @Override
    public Exchange receive(long timeout) {
        return doReceive((int) timeout);
    }

    @Override
    public Exchange receiveNoWait() {
        return doReceive(-1);
    }

    protected Exchange doReceive(int timeout) {
        Exchange exchange = endpoint.createExchange();
        HttpUriRequest method = createMethod(exchange);
        HttpClientContext httpClientContext = new HttpClientContext();

        // set optional timeout in millis
        if (timeout > 0) {
            RequestConfig requestConfig = RequestConfig.custom().setResponseTimeout(timeout, TimeUnit.MILLISECONDS).build();
            httpClientContext.setRequestConfig(requestConfig);
        }

        HttpEntity responseEntity = null;
        try {
            // execute request
            responseEntity = executeMethod(
                    method, httpClientContext,
                    response -> {
                        int responseCode = response.getCode();
                        HttpEntity entity = response.getEntity();
                        Object body = HttpHelper.cacheResponseBodyFromInputStream(entity.getContent(), exchange);

                        // lets store the result in the output message.
                        Message message = exchange.getMessage();
                        message.setBody(body);

                        // lets set the headers
                        Header[] headers = response.getHeaders();
                        HeaderFilterStrategy strategy = endpoint.getHeaderFilterStrategy();
                        for (Header header : headers) {
                            String name = header.getName();
                            // mapping the content-type
                            if (name.equalsIgnoreCase("content-type")) {
                                name = Exchange.CONTENT_TYPE;
                            }
                            String value = header.getValue();
                            if (strategy != null && !strategy.applyFilterToExternalHeaders(name, value, exchange)) {
                                message.setHeader(name, value);
                            }
                        }
                        message.setHeader(HttpConstants.HTTP_RESPONSE_CODE, responseCode);
                        if (response.getReasonPhrase() != null) {
                            message.setHeader(HttpConstants.HTTP_RESPONSE_TEXT, response.getReasonPhrase());
                        }
                        return entity;
                    });

            return exchange;
        } catch (IOException e) {
            throw new RuntimeCamelException(e);
        } finally {
            if (responseEntity != null) {
                try {
                    EntityUtils.consume(responseEntity);
                } catch (IOException e) {
                    // nothing what we can do
                }
            }
        }
    }

    /**
     * Strategy when executing the method (calling the remote server).
     *
     * @param  httpRequest the http Request to execute
     * @return             the response
     * @throws IOException can be thrown
     */
    protected <T> T executeMethod(
            HttpUriRequest httpRequest, HttpClientContext httpClientContext, HttpClientResponseHandler<T> handler)
            throws IOException {
        if (httpContext != null) {
            httpClientContext = new HttpClientContext(httpContext);
        }
        return httpClient.execute(httpRequest, httpClientContext, handler);
    }

    // Properties
    //-------------------------------------------------------------------------

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected HttpUriRequest createMethod(Exchange exchange) {
        String uri = HttpHelper.createURL(exchange, endpoint);
        return new HttpGet(uri);
    }

    @Override
    protected void doStart() throws Exception {
    }

    @Override
    protected void doStop() throws Exception {
    }
}
