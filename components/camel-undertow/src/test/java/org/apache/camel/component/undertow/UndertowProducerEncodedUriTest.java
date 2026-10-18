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
package org.apache.camel.component.undertow;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The undertow producer must not write a request path, query or header value that it cannot put on the wire as it is: a
 * percent-encoded CR/LF in {@code CamelHttpPath} or {@code CamelHttpQuery}, or a CR/LF in a header value, is refused
 * rather than written into the request line or a header line. An ordinary query is still sent.
 */
class UndertowProducerEncodedUriTest extends BaseUndertowTest {

    @Test
    void rejectsEncodedCrlfInQuery() {
        Exchange out = template.request("undertow:http://localhost:{{port}}/echo",
                e -> e.getIn().setHeader(Exchange.HTTP_QUERY, "q=x%0D%0AX-Injected:%201"));
        assertRejected(out, "request target");
    }

    @Test
    void rejectsEncodedCrlfInPath() {
        Exchange out = template.request("undertow:http://localhost:{{port}}/echo",
                e -> e.getIn().setHeader(Exchange.HTTP_PATH, "a%0D%0Ab"));
        assertRejected(out, "request target");
    }

    @Test
    void rejectsNarrowingCharacterInQuery() {
        // U+010A is above 0xFF; the client would narrow it to the single byte 0x0A (LF). It is refused before that.
        Exchange out = template.request("undertow:http://localhost:{{port}}/echo",
                e -> e.getIn().setHeader(Exchange.HTTP_QUERY, "q=Ċ"));
        assertRejected(out, "request target");
    }

    @Test
    void rejectsHeaderValueWithCrlf() {
        Exchange out = template.request("undertow:http://localhost:{{port}}/echo",
                e -> e.getIn().setHeader("MyHeader", "value\r\nX-Injected: 1"));
        assertRejected(out, "HTTP header");
    }

    @Test
    void rejectsCrlfInPreservedHostHeader() {
        // with preserveHostHeader the caller's Host header is written verbatim, so it is validated too
        Exchange out = template.request("undertow:http://localhost:{{port}}/echo?preserveHostHeader=true",
                e -> e.getIn().setHeader("Host", "localhost\r\nX-Injected: 1"));
        assertRejected(out, "Host header");
    }

    @Test
    void sendsAnOrdinaryQuery() {
        String out = template.requestBodyAndHeader("undertow:http://localhost:{{port}}/echo",
                null, Exchange.HTTP_QUERY, "q=hello", String.class);
        assertEquals("q=hello", out);
    }

    @Test
    void sendsAHeaderWithTabAndLatin1() {
        // a tab, a space and a Latin-1 (obs-text) character do not change the request framing, so they are NOT refused
        Exchange out = template.request("undertow:http://localhost:{{port}}/echo",
                e -> e.getIn().setHeader("MyHeader", "café\tau lait"));
        assertFalse(out.isFailed(), "a header value with a tab and a Latin-1 character must not be refused");
    }

    private static void assertRejected(Exchange out, String expectedInMessage) {
        assertTrue(out.isFailed(), "the exchange should have failed");
        Throwable cause = out.getException();
        assertNotNull(cause);
        boolean found = false;
        while (cause != null) {
            if (cause instanceof IllegalArgumentException && cause.getMessage() != null
                    && cause.getMessage().contains(expectedInMessage)) {
                found = true;
                break;
            }
            cause = cause.getCause();
        }
        assertTrue(found, "expected an IllegalArgumentException mentioning '" + expectedInMessage + "'");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:http://localhost:{{port}}/echo")
                        .setBody(simple("${header.CamelHttpQuery}"));
            }
        };
    }
}
