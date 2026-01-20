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
package org.apache.camel.component.netty.http;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NettyHttpGetWithParamTest extends BaseNettyTestSupport {

    private final String serverUri = "netty-http:http://localhost:" + getPort() + "/myservice";
    private final MyParamsProcessor processor = new MyParamsProcessor();

    @Test
    public void testHttpGetWithParamsViaURI() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");
        mock.expectedHeaderReceived("one", "eins");
        mock.expectedHeaderReceived("two", "zwei");

        template.requestBody(serverUri + "?one=uno&two=dos", (Object) null);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testHttpGetWithParamsViaHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");
        mock.expectedHeaderReceived("one", "eins");
        mock.expectedHeaderReceived("two", "zwei");

        template.requestBodyAndHeader(serverUri, null, Exchange.HTTP_QUERY, "one=uno&two=dos");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(serverUri).process(processor).to("mock:result");
            }
        };
    }

    private static class MyParamsProcessor implements Processor {
        @Override
        public void process(Exchange exchange) {
            NettyHttpMessage message = exchange.getIn(NettyHttpMessage.class);
            assertNotNull(message.getHttpRequest());

            String uri = message.getHttpRequest().uri();
            assertTrue(uri.endsWith("one=uno&two=dos"));

            exchange.getMessage().setBody("Bye World");
            exchange.getMessage().setHeader("one", "eins");
            exchange.getMessage().setHeader("two", "zwei");
        }
    }

}
