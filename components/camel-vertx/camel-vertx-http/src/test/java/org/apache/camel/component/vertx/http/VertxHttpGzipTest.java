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

import io.undertow.io.IoCallback;
import io.undertow.predicate.Predicates;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.encoding.ContentEncodingRepository;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.encoding.GzipEncodingProvider;
import io.undertow.util.Headers;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.undertow.CamelUndertowHttpHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class VertxHttpGzipTest extends VertxHttpTestSupport {

    private volatile String message;

    @Test
    public void testCompressedResponse() {
        // Invoke the endpoint without compression support to get the raw gzipped response
        String result = template.requestBodyAndHeader(getProducerUri(), null, "Accept-Encoding", "gzip,deflate", String.class);
        // Result is compressed so the length should be less than the original message
        Assertions.assertTrue(result.length() < message.length());

        // Invoke the endpoint with compression support to get the raw gzipped response
        result = template.requestBodyAndHeader(getProducerUri() + "?useCompression=true", null, "Accept-Encoding",
                "gzip,deflate", String.class);
        // Result length should match the original message
        Assertions.assertEquals(message.length(), result.length());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getRegistry().bind("gzip", createGzipHandler());

                from(getTestServerUri() + "?handlers=gzip")
                        .to("log:end");
            }
        };
    }

    private CamelUndertowHttpHandler createGzipHandler() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            builder.append("Hello World");
        }
        message = builder.toString();

        HttpHandler handler = new EncodingHandler(
                new ContentEncodingRepository()
                        .addEncodingHandler("gzip", new GzipEncodingProvider(), 50, Predicates.parse("max-content-size[5]")))
                .setNext(new HttpHandler() {
                    @Override
                    public void handleRequest(final HttpServerExchange exchange) {
                        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, message.length() + "");
                        exchange.getResponseSender().send(message, IoCallback.END_EXCHANGE);
                    }
                });

        return new CamelUndertowHttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                handler.handleRequest(exchange);
            }

            @Override
            public void setNext(HttpHandler nextHandler) {
            }
        };
    }
}
