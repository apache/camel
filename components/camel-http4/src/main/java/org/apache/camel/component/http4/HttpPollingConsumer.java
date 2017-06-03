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
package org.apache.camel.component.http4;

import java.io.IOException;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ServicePoolAware;
import org.apache.camel.http.common.HttpHelper;
import org.apache.camel.impl.PollingConsumerSupport;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.util.EntityUtils;

/**
 * A polling HTTP consumer which by default performs a GET
 *
 * @version 
 */
public class HttpPollingConsumer extends PollingConsumerSupport implements ServicePoolAware {
    private final HttpEndpoint endpoint;
    private HttpClient httpClient;

    public HttpPollingConsumer(HttpEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        this.httpClient = endpoint.getHttpClient();
    }

    public Exchange receive() {
        return doReceive(-1);
    }

    public Exchange receive(long timeout) {
        return doReceive((int) timeout);
    }

    public Exchange receiveNoWait() {
        return doReceive(-1);
    }

    protected Exchange doReceive(int timeout) {
        Exchange exchange = endpoint.createExchange();
        HttpRequestBase method = createMethod(exchange);
        HttpClientContext httpClientContext = new HttpClientContext();

        // set optional timeout in millis
        if (timeout > 0) {
            RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(timeout).build();
            httpClientContext.setRequestConfig(requestConfig);
        }

        HttpEntity responeEntity = null;
        try {
            // execute request
            HttpResponse response = httpClient.execute(method, httpClientContext);
            int responseCode = response.getStatusLine().getStatusCode();
            responeEntity = response.getEntity();
            Object body = HttpHelper.readResponseBodyFromInputStream(responeEntity.getContent(), exchange);

            // lets store the result in the output message.
            Message message = exchange.getOut();
            message.setBody(body);

            // lets set the headers
            Header[] headers = response.getAllHeaders();
            HeaderFilterStrategy strategy = endpoint.getHeaderFilterStrategy();
            for (Header header : headers) {
                String name = header.getName();
                // mapping the content-type
                if (name.toLowerCase().equals("content-type")) {
                    name = Exchange.CONTENT_TYPE;
                }
                String value = header.getValue();
                if (strategy != null && !strategy.applyFilterToExternalHeaders(name, value, exchange)) {
                    message.setHeader(name, value);
                }
            }
            message.setHeader(Exchange.HTTP_RESPONSE_CODE, responseCode);
            if (response.getStatusLine() != null) {
                message.setHeader(Exchange.HTTP_RESPONSE_TEXT, response.getStatusLine().getReasonPhrase());
            }

            return exchange;
        } catch (IOException e) {
            throw new RuntimeCamelException(e);
        } finally {
            if (responeEntity != null) {
                try {
                    EntityUtils.consume(responeEntity);
                } catch (IOException e) {
                    // nothing what we can do
                }
            }
        }
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

    protected HttpRequestBase createMethod(Exchange exchange) {
        String uri = HttpHelper.createURL(exchange, endpoint);
        return new HttpGet(uri);
    }

    protected void doStart() throws Exception {
    }

    protected void doStop() throws Exception {
    }
}