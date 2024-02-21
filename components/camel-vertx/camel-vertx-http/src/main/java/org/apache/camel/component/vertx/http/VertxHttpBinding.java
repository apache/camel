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
package org.apache.camel.component.vertx.http;

import io.vertx.core.AsyncResult;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import org.apache.camel.Exchange;
import org.apache.camel.spi.HeaderFilterStrategy;

public interface VertxHttpBinding {

    /**
     * Prepares a {@link HttpRequest} by setting up the required host, port & part details specified on the endpoint
     * configuration
     */
    HttpRequest<Buffer> prepareHttpRequest(VertxHttpEndpoint endpoint, Exchange exchange) throws Exception;

    /**
     * Populates request headers on the {@link HttpRequest} using the supplied {@link HeaderFilterStrategy}
     */
    void populateRequestHeaders(Exchange exchange, HttpRequest<Buffer> request, HeaderFilterStrategy strategy);

    /**
     * Handles the {@link HttpResponse} returned from the HTTP endpoint invocation
     */
    void handleResponse(VertxHttpEndpoint endpoint, Exchange exchange, AsyncResult<HttpResponse<Buffer>> response)
            throws Exception;

    /**
     * Populates response headers on the exchange from the {@link HttpResponse} using the supplied
     * {@link HeaderFilterStrategy}
     */
    void populateResponseHeaders(Exchange exchange, HttpResponse<Buffer> response, HeaderFilterStrategy headerFilterStrategy);

    /**
     * Processes the received {@link Buffer} response body in the {@link HttpResponse}
     */
    Object processResponseBody(
            VertxHttpEndpoint endpoint, Exchange exchange, HttpResponse<Buffer> result, boolean exceptionOnly)
            throws Exception;

    /**
     * Handles failures returned in the {@link HttpResponse}
     */
    Throwable handleResponseFailure(VertxHttpEndpoint endpoint, Exchange exchange, HttpResponse<Buffer> result)
            throws Exception;

}
