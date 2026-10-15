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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpProducerMultiValueHeaderTest extends BaseHttpTest {

    private HttpServer localServer;

    @Override
    public void setupResources() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost").setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/myapp", (request, response, context) -> {
                    // reply with the values of the received header lines, separated by |
                    String body = Arrays.stream(request.getHeaders("test"))
                            .map(Header::getValue)
                            .collect(Collectors.joining("|"));
                    response.setEntity(new StringEntity(body, StandardCharsets.US_ASCII));
                    response.setCode(HttpStatus.SC_OK);
                }).create();
        localServer.start();
    }

    @Override
    public void cleanupResources() throws Exception {
        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void testListIsOneHeaderPerValue() {
        assertEquals("a|b|c", send(List.of("a", "b", "c")));
    }

    @Test
    public void testArrayIsOneHeaderPerValue() {
        assertEquals("a|b", send(new String[] { "a", "b" }));
    }

    @Test
    public void testStringWithCommaIsOneHeader() {
        assertEquals("a, b", send("a, b"));
    }

    @Test
    public void testNullElementsAreSkipped() {
        assertEquals("a|b", send(Arrays.asList("a", null, "b")));
    }

    @Test
    public void testSingleElementListIsOneHeader() {
        assertEquals("x", send(List.of("x")));
    }

    @Test
    public void testIntArrayIsOneHeaderAsBefore() {
        assertEquals("[1, 2]", send(new int[] { 1, 2 }));
    }

    @Test
    public void testIteratorIsOneHeaderAsBefore() {
        assertEquals("[a, b]", send(List.of("a", "b").iterator()));
    }

    @Test
    public void testByteArrayIsSentAsText() {
        assertEquals("abc", send("abc".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    public void testStreamIsOneHeaderAsBefore() {
        assertEquals("[a, b]", send(Stream.of("a", "b")));
    }

    @Test
    public void testCharArrayIsSentAsText() {
        assertEquals("ab", send(new char[] { 'a', 'b' }));
    }

    @Test
    public void testIterableIsOneHeaderAsBefore() {
        Iterable<String> iterable = () -> List.of("a", "b").iterator();
        assertEquals("[a, b]", send(iterable));
    }

    @Test
    public void testSingleElementIntArrayIsOneHeader() {
        assertEquals("7", send(new int[] { 7 }));
    }

    private String send(Object headerValue) {
        String uri = "http://localhost:" + localServer.getLocalPort() + "/myapp";
        return template.requestBodyAndHeader(uri, null, "test", headerValue, String.class);
    }
}
