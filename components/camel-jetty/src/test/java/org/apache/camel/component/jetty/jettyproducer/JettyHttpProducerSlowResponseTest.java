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

import javax.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version 
 */
public class JettyHttpProducerSlowResponseTest extends BaseJettyTest {

    private String url = "jetty://http://0.0.0.0:" + getPort() + "/foo";

    @Test
    public void testSlowReply() throws Exception {
        // these tests does not run well on Windows
        if (isPlatform("windows")) {
            return;
        }

        // give Jetty time to startup properly
        Thread.sleep(1000);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        Exchange exchange = template.request(url, null);

        assertMockEndpointsSatisfied();

        assertNotNull(exchange);

        String reply = exchange.getOut().getBody(String.class);
        assertEquals("Bye World", reply);

        assertEquals(5, exchange.getOut().getHeaders().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(url).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        HttpServletResponse res = exchange.getIn().getBody(HttpServletResponse.class);
                        res.setStatus(200);
                        res.setHeader("customer", "gold");

                        // write empty string to force flushing
                        res.getWriter().write("");
                        res.flushBuffer();

                        Thread.sleep(1000);

                        res.getWriter().write("Bye World");
                        res.flushBuffer();
                    }
                }).to("mock:result");
            }
        };
    }
}