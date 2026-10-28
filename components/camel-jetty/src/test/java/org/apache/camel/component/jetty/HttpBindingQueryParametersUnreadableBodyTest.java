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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A form that is larger than Jetty's maxFormContentSize (200000 bytes by default), or a multipart request with the
 * multipart filter disabled, makes Jetty fail to read the request parameters. The query parameters must still be
 * mapped.
 */
public class HttpBindingQueryParametersUnreadableBodyTest extends BaseJettyTest {

    // larger than the default maxFormContentSize of Jetty
    private static final String LARGE_VALUE = "x".repeat(250_000);

    @Test
    public void queryParametersOfLargeFormAreMapped() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:form");
        mock.expectedMessageCount(1);

        int status = post("?id=42&tag=a&tag=b&text=hello+world", "name=camel&data=" + LARGE_VALUE);

        mock.assertIsSatisfied();
        assertEquals(200, status);
        Message in = mock.getReceivedExchanges().get(0).getIn();
        assertEquals("42", in.getHeader("id"));
        assertEquals(List.of("a", "b"), in.getHeader("tag"));
        assertEquals("hello world", in.getHeader("text"));
        // the form is mapped from the body as before
        assertEquals("camel", in.getHeader("name"));
        assertEquals(LARGE_VALUE.length(), in.getHeader("data", String.class).length());
    }

    @Test
    public void queryParametersOfLargeFormAreFiltered() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:form");
        mock.expectedMessageCount(1);

        int status = post("?id=42&CamelHttpResponseCode=500", "data=" + LARGE_VALUE);

        mock.assertIsSatisfied();
        assertEquals(200, status);
        Message in = mock.getReceivedExchanges().get(0).getIn();
        assertEquals("42", in.getHeader("id"));
        assertFalse(in.getHeaders().containsKey(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    public void invalidQueryOfLargeFormIsNotMapped() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:form");
        mock.expectedMessageCount(1);

        int status = post("?id=%E9", "data=" + LARGE_VALUE);

        mock.assertIsSatisfied();
        assertEquals(200, status);
        assertNull(mock.getReceivedExchanges().get(0).getIn().getHeader("id"));
    }

    @Test
    public void queryParametersOfMultipartWithoutFilterAreMapped() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:multipart");
        mock.expectedMessageCount(1);

        String body = "--boundary\r\nContent-Disposition: form-data; name=\"name\"\r\n\r\ncamel\r\n--boundary--\r\n";
        int status = send("/multipart?id=42", "multipart/form-data; boundary=boundary", body);

        mock.assertIsSatisfied();
        assertEquals(200, status);
        Message in = mock.getReceivedExchanges().get(0).getIn();
        assertEquals("42", in.getHeader("id"));
        // the multipart body is left to the route, as before
        assertEquals(body, in.getBody(String.class));
    }

    private int post(String query, String form) throws Exception {
        return send("/form" + query, "application/x-www-form-urlencoded", form);
    }

    private int send(String pathAndQuery, String contentType, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + getPort() + pathAndQuery))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("jetty:http://localhost:{{port}}/form")
                        .to("mock:form")
                        .removeHeaders("*")
                        .setBody(constant("OK"));
                from("jetty:http://localhost:{{port}}/multipart?enableMultipartFilter=false")
                        .convertBodyTo(String.class)
                        .to("mock:multipart")
                        .removeHeaders("*")
                        .setBody(constant("OK"));
            }
        };
    }
}
