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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A client that sends {@code Expect: 100-continue} waits for the interim response of the server before it sends the
 * request body, so no byte of the body has arrived when the consumer starts to read it.
 */
class JettyGzipRequestExpectContinueTest extends BaseJettyTest {

    private static final String TEXT = "Hello World, sent with Content-Encoding: gzip";

    @Test
    void gzipBodySentAfter100ContinueIsUncompressed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(TEXT);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(TEXT.getBytes(StandardCharsets.UTF_8));
        }

        String response = sendAfter100Continue(bytes.toByteArray());

        assertTrue(response.startsWith("HTTP/1.1 200"), "Expected 200 OK but was: " + response);
        mock.assertIsSatisfied();
    }

    @Test
    void bodyThatIsNotGzipSentAfter100ContinueIsNotPassedToTheRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        // Content-Encoding: gzip, but the body is plain text
        String response = sendAfter100Continue(TEXT.getBytes(StandardCharsets.UTF_8));

        assertTrue(response.startsWith("HTTP/1.1 500"), "Expected 500 but was: " + response);
        mock.assertIsSatisfied();
    }

    /**
     * Sends a POST with Content-Encoding: gzip and Expect: 100-continue, and the body only after the server asked for
     * it. Returns the status line and the headers of the final response.
     */
    private String sendAfter100Continue(byte[] body) throws Exception {
        try (Socket socket = new Socket("localhost", getPort())) {
            socket.setSoTimeout(20000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(("POST /gzip HTTP/1.1\r\n"
                       + "Host: localhost:" + getPort() + "\r\n"
                       + "Content-Type: text/plain; charset=UTF-8\r\n"
                       + "Content-Encoding: gzip\r\n"
                       + "Content-Length: " + body.length + "\r\n"
                       + "Expect: 100-continue\r\n"
                       + "Connection: close\r\n"
                       + "\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();

            String interim = readHead(in);
            assertTrue(interim.startsWith("HTTP/1.1 100"), "Expected 100 Continue but was: " + interim);
            out.write(body);
            out.flush();

            return readHead(in);
        }
    }

    /**
     * Reads the status line and the headers of a response, up to and including the empty line.
     */
    private static String readHead(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            sb.append((char) c);
            if (sb.length() >= 4 && sb.substring(sb.length() - 4).equals("\r\n\r\n")) {
                break;
            }
        }
        return sb.toString();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jetty:http://localhost:{{port}}/gzip")
                        .convertBodyTo(String.class, "UTF-8")
                        .to("mock:result")
                        .removeHeaders("*")
                        .setBody(constant("ok"));
            }
        };
    }
}
