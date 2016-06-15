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
package org.apache.camel.component.netty4.http;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Ignore;
import org.junit.Test;

public class NettyHttpGetWithParamTest extends BaseNettyTest {

    private String serverUri = "netty4-http:http://localhost:" + getPort() + "/myservice";
    private MyParamsProcessor processor = new MyParamsProcessor();

    @Test
    public void testHttpGetWithParamsViaURI() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");
        mock.expectedHeaderReceived("one", "eins");
        mock.expectedHeaderReceived("two", "zwei");

        template.requestBody(serverUri + "?one=uno&two=dos", (Object) null);

        assertMockEndpointsSatisfied();
    }

    @Test
    @Ignore("HTTP_QUERY not supported")
    public void testHttpGetWithParamsViaHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");
        mock.expectedHeaderReceived("one", "eins");
        mock.expectedHeaderReceived("two", "zwei");

        template.requestBodyAndHeader(serverUri, null, Exchange.HTTP_QUERY, "one=uno&two=dos");

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(serverUri).process(processor).to("mock:result");
            }
        };
    }

    private static class MyParamsProcessor implements Processor {
        public void process(Exchange exchange) throws Exception {
            NettyHttpMessage message = exchange.getIn(NettyHttpMessage.class);
            assertNotNull(message.getHttpRequest());

            String uri = message.getHttpRequest().uri();
            assertTrue(uri.endsWith("one=uno&two=dos"));

            exchange.getOut().setBody("Bye World");
            exchange.getOut().setHeader("one", "eins");
            exchange.getOut().setHeader("two", "zwei");
        }
    }

}
