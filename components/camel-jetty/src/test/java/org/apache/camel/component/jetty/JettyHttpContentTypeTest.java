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

import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JettyHttpContentTypeTest extends BaseJettyTest {

    private static final String CHARSET = StandardCharsets.ISO_8859_1.name();

    @Test
    public void testContentType() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.CONTENT_TYPE, "text/plain; charset=" + CHARSET);
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_CHARACTER_ENCODING, CHARSET);
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_URL, "http://127.0.0.1:" + getPort() + "/foo");
        getMockEndpoint("mock:input").expectedPropertyReceived(Exchange.CHARSET_NAME, CHARSET);

        byte[] data = "Hello World".getBytes(StandardCharsets.ISO_8859_1);
        String out = template.requestBodyAndHeader("http://127.0.0.1:{{port}}/foo", data, "content-type",
                "text/plain; charset=\"" + CHARSET + "\"", String.class);
        assertEquals("Bye World", out);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testContentTypeWithAction() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.CONTENT_TYPE,
                "text/plain; charset=" + CHARSET + "; action=\"http://somewhere.com/foo\"");
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_CHARACTER_ENCODING, "ISO-8859-1");
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_URL, "http://127.0.0.1:" + getPort() + "/foo");
        getMockEndpoint("mock:input").expectedPropertyReceived(Exchange.CHARSET_NAME, "ISO-8859-1");

        byte[] data = "Hello World".getBytes(StandardCharsets.ISO_8859_1);
        String out = template.requestBodyAndHeader("http://127.0.0.1:{{port}}/foo", data, "content-type",
                "text/plain;charset=\"" + CHARSET + "\";action=\"http://somewhere.com/foo\"", String.class);
        assertEquals("Bye World", out);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jetty:http://127.0.0.1:{{port}}/foo").to("mock:input").transform().constant("Bye World");
            }
        };
    }

}
