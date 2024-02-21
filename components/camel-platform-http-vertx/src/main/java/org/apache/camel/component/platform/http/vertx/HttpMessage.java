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
package org.apache.camel.component.platform.http.vertx;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.util.ObjectHelper;

public class HttpMessage extends DefaultMessage {

    private HttpServerRequest request;
    private HttpServerResponse response;

    public HttpMessage(Exchange exchange, HttpServerRequest request, HttpServerResponse response) {
        super(exchange);
        init(exchange, request, response);
    }

    public void init(Exchange exchange, HttpServerRequest request, HttpServerResponse response) {
        setExchange(exchange);
        this.request = request;
        this.response = response;
    }

    @Override
    public void reset() {
        super.reset();
        request = null;
        response = null;
    }

    public HttpServerRequest getRequest() {
        return request;
    }

    public HttpServerResponse getResponse() {
        return response;
    }

    @Override
    public HttpMessage newInstance() {
        return new HttpMessage(getExchange(), request, response);
    }

    @Override
    public String toString() {
        // do not use toString on HTTP message
        return "HttpMessage@" + ObjectHelper.getIdentityHashCode(this);
    }
}
