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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultExchange;

/**
 * Represents a HTTP exchange which exposes the underlying HTTP abtractions via
 * {@link #getRequest()} and {@link #getResponse()}
 *
 * @version $Revision$
 */
public class HttpExchange extends DefaultExchange {
    private final HttpEndpoint endpoint;
    private HttpServletRequest request;
    private HttpServletResponse response;

    public HttpExchange(HttpEndpoint endpoint, ExchangePattern pattern) {
        super(endpoint.getCamelContext(), pattern);
        this.endpoint = endpoint;
    }

    public HttpExchange(HttpEndpoint endpoint, HttpServletRequest request, HttpServletResponse response) {
        this(endpoint, getPatternFromRequest(request));
        this.request = request;
        this.response = response;
        setIn(new HttpMessage(this, request));
    }

    /**
     * Returns the underlying Servlet request for inbound HTTP requests
     *
     * @return the underlying Servlet request for inbound HTTP requests
     */
    public HttpServletRequest getRequest() {
        return request;
    }

    /**
     * Returns the underlying Servlet response for inbound HTTP requests
     *
     * @return the underlying Servlet response for inbound HTTP requests
     */
    public HttpServletResponse getResponse() {
        return response;
    }

    public HttpEndpoint getEndpoint() {
        return endpoint;
    }

    protected static ExchangePattern getPatternFromRequest(HttpServletRequest request) {
        // TODO for now just default to InOut?
        return ExchangePattern.InOut;
    }
}
