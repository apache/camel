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
package org.apache.camel.component.jetty;

import junit.framework.Assert;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpExchange;
import org.apache.camel.component.http.HttpProducer;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test to verify that we can have URI options for external system (endpoint is lenient)
 */
public class JettyHttpGetWithParamTest extends ContextTestSupport {

    private String serverUri = "http://localhost:5432/myservice";
    private MyParamsProcessor processor = new MyParamsProcessor();

    public void testHttpGetWithParamsViaURI() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");
        mock.expectedHeaderReceived("one", "einz");
        mock.expectedHeaderReceived("two", "twei");

        template.sendBody(serverUri + "?one=uno&two=dos", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testHttpGetWithParamsViaHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");
        mock.expectedHeaderReceived("one", "einz");
        mock.expectedHeaderReceived("two", "twei");

        template.sendBodyAndHeader(serverUri, "Hello World", HttpProducer.QUERY, "one=uno&two=dos");

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("jetty:" + serverUri).process(processor).to("mock:result");
            }
        };
    }

    private class MyParamsProcessor implements Processor {
        public void process(Exchange exchange) throws Exception {
            HttpExchange http = (HttpExchange) exchange;
            Assert.assertNotNull(http.getRequest());
            Assert.assertEquals("uno", http.getRequest().getParameter("one"));
            Assert.assertEquals("dos", http.getRequest().getParameter("two"));

            exchange.getOut(true).setBody("Bye World");
            exchange.getOut(true).setHeader("one", "einz");
            exchange.getOut(true).setHeader("two", "twei");
        }
    }

}
