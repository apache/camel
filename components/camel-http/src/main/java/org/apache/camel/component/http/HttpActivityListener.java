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

import org.apache.camel.Exchange;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHost;

/**
 * Listener when HTTP requests and responses are sent and received.
 */
public interface HttpActivityListener {

    /**
     * HTTP request is about to be sent
     *
     * @param source   the http producer that are used
     * @param exchange the current exchange
     * @param httpHost the host the request is sent to
     * @param request  the http request
     */
    void onRequestSubmitted(Object source, Exchange exchange, HttpHost httpHost, ClassicHttpRequest request);

    /**
     * HTTP response received
     *
     * @param source   the http producer that are used
     * @param exchange the current exchange
     * @param httpHost the host the request is received from
     * @param response the http response
     * @param elapsed  time in millis before the response was received after sending
     */
    void onResponseReceived(Object source, Exchange exchange, HttpHost httpHost, ClassicHttpResponse response, long elapsed);

}
