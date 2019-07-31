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
import org.junit.Test;

public class UndertowHeaderTest extends BaseUndertowTest {

    @Test
    public void testHttpHeaders() throws Exception {
        getMockEndpoint("mock:input").expectedMessageCount(1);
        getMockEndpoint("mock:input").expectedHeaderReceived("param", "true");
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_URL, "http://localhost:" + getPort() + "/headers");
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_URI, "/headers");
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_QUERY, "param=true");
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_PATH, "");

        String out = template.requestBody("http://localhost:" + getPort() + "/headers?param=true", null, String.class);
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHttpHeadersPost() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_METHOD, "POST");
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_URL, "http://localhost:" + getPort() + "/headers");
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_URI, "/headers");
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_QUERY, "");
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_PATH, "");

        String out = template.requestBody("http://localhost:" + getPort() + "/headers", "Hello World", String.class);
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHttpPathHeader() throws Exception {
        getMockEndpoint("mock:input").expectedMessageCount(1);
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_PATH, "/headers");

        String out = template.requestBody("http://localhost:" + getPort() + "/hello/headers", null, String.class);
        assertEquals("Hello World", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("undertow:http://localhost:{{port}}/headers")
                    .to("mock:input")
                    .transform().constant("Bye World");

                from("undertow:http://localhost:{{port}}/hello?matchOnUriPrefix=true")
                    .to("mock:input")
                    .transform().constant("Hello World");
            }
        };
    }

}
