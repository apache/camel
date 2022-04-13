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

import java.net.URI;

import io.vertx.core.AsyncResult;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VertxHttpCustomBindingTest extends VertxHttpTestSupport {

    @Test
    public void testCustomVertxHttpBinding() {
        String result = template.requestBody(getProducerUri(), null, String.class);
        assertEquals("Hello World", result);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        VertxHttpComponent component = new VertxHttpComponent();
        component.setVertxHttpBinding(new VertxHttpBinding() {
            @Override
            public HttpRequest<Buffer> prepareHttpRequest(VertxHttpEndpoint endpoint, Exchange exchange) {
                VertxHttpConfiguration configuration = endpoint.getConfiguration();
                URI httpURI = configuration.getHttpUri();
                WebClient webClient = endpoint.getWebClient();
                return webClient.request(HttpMethod.PATCH, httpURI.getPort(), httpURI.getHost(), "/overridden");
            }

            @Override
            public void populateRequestHeaders(Exchange exchange, HttpRequest<Buffer> request, HeaderFilterStrategy strategy) {
                // Noop
            }

            @Override
            public void populateResponseHeaders(
                    Exchange exchange, HttpResponse<Buffer> response, HeaderFilterStrategy headerFilterStrategy) {
                // Noop
            }

            @Override
            public Object processResponseBody(
                    VertxHttpEndpoint endpoint, Exchange exchange, HttpResponse<Buffer> result, boolean exceptionOnly) {
                return null;
            }

            @Override
            public Throwable handleResponseFailure(VertxHttpEndpoint endpoint, Exchange exchange, HttpResponse<Buffer> result) {
                return null;
            }

            @Override
            public void handleResponse(
                    VertxHttpEndpoint endpoint, Exchange exchange, AsyncResult<HttpResponse<Buffer>> response) {
                if (response.succeeded()) {
                    HttpResponse<Buffer> result = response.result();
                    exchange.getMessage().setBody(result.bodyAsString());
                }
            }
        });

        CamelContext camelContext = super.createCamelContext();
        camelContext.addComponent("vertx-http", component);
        return camelContext;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getTestServerUri() + "/overridden")
                        .setBody(constant("Hello World"));
            }
        };
    }
}
