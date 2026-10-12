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
package org.apache.camel.component.jetty;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A request with Content-Encoding: gzip and no body: the consumer must not wait for a body that does not come, also not
 * with disableStreamCache=true, where the route gets the stream of the request.
 */
class JettyGzipRequestEmptyBodyTest extends BaseJettyTest {

    @Test
    void contentLengthZero() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        String response = send("Content-Length: 0\r\n", "");

        assertTrue(response.startsWith("HTTP/1.1 200"), "Expected 200 OK but was: " + response);
        mock.assertIsSatisfied();
        assertNull(mock.getReceivedExchanges().get(0).getMessage().getBody());
    }

    @Test
    void chunkedWithoutData() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        // the last chunk only
        String response = send("Transfer-Encoding: chunked\r\n", "0\r\n\r\n");

        assertTrue(response.startsWith("HTTP/1.1 200"), "Expected 200 OK but was: " + response);
        mock.assertIsSatisfied();
        assertEquals("", mock.getReceivedExchanges().get(0).getMessage().getBody());
    }

    private String send(String lengthHeader, String body) throws Exception {
        try (Socket socket = new Socket("localhost", getPort())) {
            // a consumer that waits for a body makes the read of the response fail
            socket.setSoTimeout(20000);
            OutputStream out = socket.getOutputStream();
            out.write(("POST /gzip HTTP/1.1\r\n"
                       + "Host: localhost:" + getPort() + "\r\n"
                       + "Content-Type: text/plain; charset=UTF-8\r\n"
                       + "Content-Encoding: gzip\r\n"
                       + lengthHeader
                       + "Connection: close\r\n"
                       + "\r\n"
                       + body)
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();

            InputStream in = socket.getInputStream();
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = in.read()) != -1 && !sb.toString().endsWith("\r\n\r\n")) {
                sb.append((char) c);
            }
            return sb.toString();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jetty:http://localhost:{{port}}/gzip?disableStreamCache=true")
                        .convertBodyTo(String.class, "UTF-8")
                        .to("mock:result")
                        .removeHeaders("*")
                        .setBody(constant("ok"));
            }
        };
    }
}
