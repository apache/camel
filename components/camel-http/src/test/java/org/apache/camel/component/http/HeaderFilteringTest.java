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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.Producer;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.ContentType.APPLICATION_JSON;
import static org.apache.camel.component.http.HttpMethods.POST;
import static org.apache.hc.core5.http.HttpHeaders.HOST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class HeaderFilteringTest {

    private static final String BODY = "{\"example\":\"json\"}";

    private int port;

    private HttpServer server;

    @Test
    public void shouldFilterIncomingHttpHeadersInProducer() throws Exception {
        final DefaultCamelContext context = new DefaultCamelContext();
        context.start();

        final HttpComponent http = context.getComponent("http", HttpComponent.class);

        final Producer producer = http.createProducer(context, "http://localhost:" + port, POST.name(), "/test", null, null,
                APPLICATION_JSON.getMimeType(), APPLICATION_JSON.getMimeType(), new RestConfiguration(),
                Collections.emptyMap());

        final DefaultExchange exchange = new DefaultExchange(context);
        final DefaultMessage in = new DefaultMessage(context);
        in.setHeader(HOST, "www.not-localhost.io");
        in.setBody(BODY);
        exchange.setIn(in);

        producer.start();
        try {
            producer.process(exchange);
        } catch (final HttpOperationFailedException e) {
            fail(e.getMessage() + "\n%s", e.getResponseBody());
        }
        producer.stop();
        context.stop();
    }

    @BeforeEach
    public void startHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/test", this::handleTest);
        server.start();

        port = server.getAddress().getPort();
    }

    @AfterEach
    public void stopHttpServer() {
        server.stop(0);
    }

    private void handleTest(final HttpExchange exchange) throws IOException {
        try (final OutputStream responseBody = exchange.getResponseBody()) {
            try {
                assertThat(exchange.getRequestBody())
                        .hasSameContentAs(new ByteArrayInputStream(BODY.getBytes(StandardCharsets.UTF_8)));
                assertThat(exchange.getRequestHeaders()).containsEntry(HOST,
                        Collections.singletonList("localhost:" + port));

                exchange.sendResponseHeaders(200, 0);
            } catch (final AssertionError error) {
                final StringWriter out = new StringWriter();
                error.printStackTrace(new PrintWriter(out));

                final String failure = out.toString();
                final byte[] failureBytes = failure.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, failureBytes.length);
                responseBody.write(failureBytes);
            }
        }
    }
}
