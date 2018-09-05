/**
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class UndertowProducerTest extends BaseUndertowTest {

    @Test
    public void testHttpSimple() throws Exception {
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");

        String out = template.requestBody("undertow:http://localhost:{{port}}/foo", null, String.class);
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHttpSimpleWithQuery() throws Exception {
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");
        getMockEndpoint("mock:input").expectedHeaderReceived("name", "me");

        String out = template.requestBody("undertow:http://localhost:{{port}}/foo?name=me", null, String.class);
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHttpSimpleWithExchangeHttpQuery() throws Exception {
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");
        getMockEndpoint("mock:input").expectedHeaderReceived("name", "me");

        String out = template.requestBodyAndHeader("undertow:http://localhost:{{port}}/foo", null, Exchange.HTTP_QUERY, "name=me", String.class);
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHttpSimpleHeader() throws Exception {
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_METHOD, "POST");

        String out = template.requestBodyAndHeader("undertow:http://localhost:{{port}}/foo", null, Exchange.HTTP_METHOD, "POST", String.class);
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHttpSimpleHeaderAndBody() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_METHOD, "POST");

        String out = template.requestBodyAndHeader("undertow:http://localhost:{{port}}/foo", "Hello World", Exchange.HTTP_METHOD, "POST", String.class);
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHttpInputStream() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_METHOD, "POST");

        String out = template.requestBodyAndHeader("undertow:http://localhost:{{port2}}/bar", "Hello World", Exchange.HTTP_METHOD, "POST", String.class);
        assertEquals("This is the InputStream", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            private InputStream is = new ByteArrayInputStream("This is the InputStream".getBytes());
            @Override
            public void configure() throws Exception {
                from("undertow:http://localhost:{{port}}/foo")
                    .to("mock:input")
                    .transform().constant("Bye World");

                from("undertow:http://localhost:{{port2}}/bar")
                    .to("mock:input")
                    .transform().constant(is);
            }
        };
    }
}
