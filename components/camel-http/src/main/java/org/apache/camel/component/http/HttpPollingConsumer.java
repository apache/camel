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
package org.apache.camel.component.http;

import java.io.IOException;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ServicePoolAware;
import org.apache.camel.http.common.HttpHelper;
import org.apache.camel.impl.PollingConsumerSupport;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;

/**
 * A polling HTTP consumer which by default performs a GET
 *
 */
public class HttpPollingConsumer extends PollingConsumerSupport implements ServicePoolAware {
    private final HttpEndpoint endpoint;
    private HttpClient httpClient;

    public HttpPollingConsumer(HttpEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        this.httpClient = endpoint.createHttpClient();
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
        HttpMethod method = createMethod(exchange);

        // set optional timeout in millis
        if (timeout > 0) {
            method.getParams().setSoTimeout(timeout);
        }

        try {
            // execute request
            int responseCode = httpClient.executeMethod(method);

            Object body = HttpHelper.readResponseBodyFromInputStream(method.getResponseBodyAsStream(), exchange);

            // lets store the result in the output message.
            Message message = exchange.getOut();
            message.setBody(body);

            // lets set the headers
            Header[] headers = method.getResponseHeaders();
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
            message.setHeader(Exchange.HTTP_RESPONSE_TEXT, method.getStatusText());

            return exchange;
        } catch (IOException e) {
            throw new RuntimeCamelException(e);
        } finally {
            method.releaseConnection();
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

    protected HttpMethod createMethod(Exchange exchange) {
        String uri = HttpHelper.createURL(exchange, endpoint);
        return new GetMethod(uri);
    }

    protected void doStart() throws Exception {
    }

    protected void doStop() throws Exception {
    }
}
