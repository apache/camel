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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.http.common.HttpMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit test to verify that we can have URI options for external system (endpoint is lenient)
 */
public class JettyHttpGetWithParamTest extends BaseJettyTest {

    private final String serverUri = "http://localhost:" + getPort() + "/myservice";
    private final MyParamsProcessor processor = new MyParamsProcessor();

    @Test
    public void testHttpGetWithParamsViaURI() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");
        mock.expectedHeaderReceived("one", "eins");
        mock.expectedHeaderReceived("two", "zwei");

        template.requestBody(serverUri + "?one=uno&two=dos", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testHttpGetWithParamsViaHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");
        mock.expectedHeaderReceived("one", "eins");
        mock.expectedHeaderReceived("two", "zwei");

        template.requestBodyAndHeader(serverUri, "Hello World", Exchange.HTTP_QUERY, "one=uno&two=dos");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testHttpGetFromOtherRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");
        mock.expectedHeaderReceived("one", "eins");
        mock.expectedHeaderReceived("two", "zwei");

        template.requestBodyAndHeader("direct:start", "Hello World", "parameters", "one=uno&two=dos");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("jetty:" + serverUri).process(processor).to("mock:result");
                from("direct:start").setHeader(Exchange.HTTP_METHOD, constant("GET"))
                        .setHeader(Exchange.HTTP_URI, simple(serverUri + "?${in.headers.parameters}"))
                        .to("http://example");
            }
        };
    }

    private static class MyParamsProcessor implements Processor {
        @Override
        public void process(Exchange exchange) {
            HttpMessage message = (HttpMessage) exchange.getIn();
            assertNotNull(message.getRequest());
            assertEquals("uno", message.getRequest().getParameter("one"));
            assertEquals("dos", message.getRequest().getParameter("two"));

            exchange.getMessage().setBody("Bye World");
            exchange.getMessage().setHeader("one", "eins");
            exchange.getMessage().setHeader("two", "zwei");
        }
    }
}
