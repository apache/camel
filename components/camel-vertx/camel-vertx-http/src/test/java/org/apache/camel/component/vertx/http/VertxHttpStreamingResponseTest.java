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

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.codec.BodyCodec;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VertxHttpStreamingResponseTest extends VertxHttpTestSupport {

    private static final String MESSAGE = "Streaming response content";

    @Test
    public void testStreamingResponseToFile() {
        VertxHttpComponent component = context.getComponent("vertx-http", VertxHttpComponent.class);
        Vertx vertx = component.getVertx();

        String path = "target/streaming.txt";
        AsyncFile file = vertx.fileSystem().openBlocking(path, new OpenOptions());

        VertxHttpBinding binding = new DefaultVertxHttpBinding() {
            @Override
            public HttpRequest<Buffer> prepareHttpRequest(VertxHttpEndpoint endpoint, Exchange exchange) throws Exception {
                HttpRequest<Buffer> request = super.prepareHttpRequest(endpoint, exchange);
                request.as(BodyCodec.pipe(file));
                return request;
            }
        };

        component.setVertxHttpBinding(binding);

        try {
            template.request(getProducerUri(), null);
            Buffer buffer = vertx.fileSystem().readFileBlocking(path);
            assertEquals(MESSAGE, buffer.toString());
        } finally {
            vertx.fileSystem().deleteBlocking(path);
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getTestServerUri())
                        .setBody().constant(MESSAGE);
            }
        };
    }
}
