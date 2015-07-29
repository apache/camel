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
package org.apache.camel.component.jetty.jettyproducer;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.http.common.HttpMessage;
import org.junit.Test;

/**
 * Unit test to verify that we can have URI options for external system (endpoint is lenient)
 */
public class JettyHttpProducerGetWithParamTest extends BaseJettyTest {

    private String serverUri = "jetty://http://localhost:" + getPort() + "/myservice";
    private MyParamsProcessor processor = new MyParamsProcessor();

    @Test
    public void testHttpGetWithParamsViaURI() throws Exception {
        // these tests does not run well on Windows
        if (isPlatform("windows")) {
            return;
        }

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");
        mock.expectedHeaderReceived("one", "eins");
        mock.expectedHeaderReceived("two", "zwei");

        // give Jetty time to startup properly
        Thread.sleep(1000);

        template.requestBody(serverUri + "?one=uno&two=dos", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHttpGetWithParamsViaHeader() throws Exception {
        // these tests does not run well on Windows
        if (isPlatform("windows")) {
            return;
        }

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");
        mock.expectedHeaderReceived("one", "eins");
        mock.expectedHeaderReceived("two", "zwei");

        // give Jetty time to startup properly
        Thread.sleep(1000);

        template.requestBodyAndHeader(serverUri, "Hello World", Exchange.HTTP_QUERY, "one=uno&two=dos");

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
            HttpMessage message = (HttpMessage)exchange.getIn();
            assertNotNull(message.getRequest());
            assertEquals("uno", message.getRequest().getParameter("one"));
            assertEquals("dos", message.getRequest().getParameter("two"));

            exchange.getOut().setBody("Bye World");
            exchange.getOut().setHeader("one", "eins");
            exchange.getOut().setHeader("two", "zwei");
        }
    }
}