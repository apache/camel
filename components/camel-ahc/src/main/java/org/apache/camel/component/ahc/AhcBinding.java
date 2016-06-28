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
package org.apache.camel.component.ahc;

import java.io.ByteArrayOutputStream;

import org.apache.camel.Exchange;
import org.asynchttpclient.HttpResponseHeaders;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.Request;

/**
 * Binding from Camel to/from {@link com.ning.http.client.AsyncHttpClient}
 */
public interface AhcBinding {

    /**
     * Prepares the AHC {@link Request} to be send.
     *
     * @param endpoint the endpoint
     * @param exchange the exchange
     * @return the request to send using the {@link com.ning.http.client.AsyncHttpClient}
     * @throws Exception is thrown if error occurred preparing the request
     */
    Request prepareRequest(AhcEndpoint endpoint, Exchange exchange) throws Exception;

    /**
     * Callback from the {@link com.ning.http.client.AsyncHttpClient} when an exception occurred sending the request.
     *
     * @param endpoint the endpoint
     * @param exchange the exchange
     * @param t        the thrown exception
     * @throws Exception is thrown if error occurred in the callback
     */
    void onThrowable(AhcEndpoint endpoint, Exchange exchange, Throwable t) throws Exception;

    /**
     * Callback from the {@link com.ning.http.client.AsyncHttpClient} when the HTTP response status was received
     *
     * @param endpoint       the endpoint
     * @param exchange       the exchange
     * @param responseStatus the HTTP response status
     * @throws Exception is thrown if error occurred in the callback
     */
    void onStatusReceived(AhcEndpoint endpoint, Exchange exchange, HttpResponseStatus responseStatus) throws Exception;

    /**
     * Callback from the {@link com.ning.http.client.AsyncHttpClient} when the HTTP headers was received
     *
     * @param endpoint the endpoint
     * @param exchange the exchange
     * @param headers  the HTTP headers
     * @throws Exception is thrown if error occurred in the callback
     */
    void onHeadersReceived(AhcEndpoint endpoint, Exchange exchange, HttpResponseHeaders headers) throws Exception;

    /**
     * Callback from the {@link com.ning.http.client.AsyncHttpClient} when complete and all the response has been received.
     *
     *
     * @param endpoint      the endpoint
     * @param exchange      the exchange
     * @param url           the url requested
     * @param os            output stream with the HTTP response body
     * @param contentLength length of the response body
     * @param statusCode    the http response code
     * @param statusText    the http status text
     * @throws Exception is thrown if error occurred in the callback
     */
    void onComplete(AhcEndpoint endpoint, Exchange exchange, String url, ByteArrayOutputStream os, int contentLength,
                    int statusCode, String statusText) throws Exception;
}
