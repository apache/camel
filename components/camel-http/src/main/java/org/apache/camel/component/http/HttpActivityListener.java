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
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;

/**
 * Listener when HTTP requests and responses are sent and received.
 */
public interface HttpActivityListener {

    /**
     * HTTP request is about to be sent
     *
     * @param source   the http producer that are used
     * @param exchange the current exchange
     * @param host     the host where the request is sent to
     * @param request  the http request
     * @param entity   the http data
     */
    void onRequestSubmitted(Object source, Exchange exchange, HttpHost host, HttpRequest request, HttpEntity entity);

    /**
     * HTTP response received
     *
     * @param source   the http producer that are used
     * @param exchange the current exchange
     * @param host     the host where the response was received from
     * @param response the http response
     * @param entity   the http data
     * @param elapsed  time in millis before the response was received after sending
     */
    void onResponseReceived(
            Object source, Exchange exchange, HttpHost host, HttpResponse response, HttpEntity entity, long elapsed);

}
