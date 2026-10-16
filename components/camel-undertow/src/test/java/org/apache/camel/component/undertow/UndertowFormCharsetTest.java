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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Form fields are decoded as UTF-8 when the request does not declare a charset, as a browser sends them.
 */
class UndertowFormCharsetTest extends BaseUndertowTest {

    private static final String TEXT = "Grüße";
    private static final String BOUNDARY = "r27boundary";

    @Test
    void testUrlEncodedFormWithoutCharsetIsUtf8() throws Exception {
        Message received = post("application/x-www-form-urlencoded", "name=Gr%C3%BC%C3%9Fe".getBytes(UTF_8));

        assertEquals(TEXT, received.getHeader("name"));
        assertEquals(TEXT, received.getBody(Map.class).get("name"));
    }

    @Test
    void testMultipartTextFieldWithoutCharsetIsUtf8() throws Exception {
        String multipart = "--" + BOUNDARY + "\r\n"
                           + "Content-Disposition: form-data; name=\"name\"\r\n\r\n"
                           + TEXT + "\r\n"
                           + "--" + BOUNDARY + "--\r\n";
        Message received = post("multipart/form-data; boundary=" + BOUNDARY, multipart.getBytes(UTF_8));

        assertEquals(TEXT, received.getHeader("name"));
        assertEquals(TEXT, received.getBody(Map.class).get("name"));
    }

    @Test
    void testUrlEncodedFormKeepsDeclaredCharset() throws Exception {
        // a charset that the request declares is still used to decode the percent-encoded bytes
        Message received = post("application/x-www-form-urlencoded; charset=ISO-8859-1",
                "name=Gr%FC%DFe".getBytes(UTF_8));

        assertEquals(TEXT, received.getHeader("name"));
    }

    @Test
    void testUrlEncodedIso88591FormWithoutCharsetGetsReplacementCharacters() throws Exception {
        // the caveat of the change: ISO-8859-1 data that does not declare its charset is not valid UTF-8
        Message received = post("application/x-www-form-urlencoded", "name=Gr%FC%DFe".getBytes(UTF_8));

        assertEquals("Gr\uFFFD\uFFFDe", received.getHeader("name"));
    }

    private Message post(String contentType, byte[] body) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:form");
        mock.reset();
        mock.expectedMessageCount(1);

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + getPort() + "/form"))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        MockEndpoint.assertIsSatisfied(context);
        return mock.getExchanges().get(0).getIn();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:http://localhost:{{port}}/form")
                        .to("mock:form")
                        .setBody(constant("OK"));
            }
        };
    }
}
