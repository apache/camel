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
package org.apache.camel.component.servlet;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A request with an empty charset parameter in the Content-Type has no charset, so the exchange charset is not set to
 * an empty name and the body is read with the default charset (UTF-8).
 */
class ServletEmptyCharsetParameterTest extends ServletCamelRouterTestSupport {

    private static final String TEXT = "Grüße aus Köln";

    // the embedded Undertow fails itself on a charset= at the very end of the header, so it is followed by a parameter
    @ParameterizedTest
    @ValueSource(strings = {
            "text/plain; charset=; format=flowed", "text/plain; charset=\"\"", "text/plain; charset= ;format=flowed" })
    void requestWithEmptyCharsetParameter(String contentType) throws Exception {
        WebRequest req = new PostMethodWebRequest(
                contextUrl + "/services/echo", new ByteArrayInputStream(TEXT.getBytes(StandardCharsets.UTF_8)), contentType);
        WebResponse response = query(req, false);

        assertEquals(200, response.getResponseCode(), response.getText(StandardCharsets.UTF_8));
        assertEquals("Hello " + TEXT, response.getText(StandardCharsets.UTF_8));
    }

    @Test
    void formWithEmptyCharsetParameter() throws Exception {
        WebRequest req = new PostMethodWebRequest(
                contextUrl + "/services/form",
                new ByteArrayInputStream("greeting=Gr%C3%BC%C3%9Fe+aus+K%C3%B6ln".getBytes(StandardCharsets.US_ASCII)),
                "application/x-www-form-urlencoded; charset=\"\"");
        WebResponse response = query(req, false);

        assertEquals(200, response.getResponseCode(), response.getText(StandardCharsets.UTF_8));
        assertEquals(TEXT, response.getText(StandardCharsets.UTF_8));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("servlet:/echo")
                        .convertBodyTo(String.class)
                        .setBody(simple("Hello ${body}"));

                from("servlet:/form")
                        .setBody(header("greeting"));
            }
        };
    }
}
