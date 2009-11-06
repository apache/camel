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

import java.util.concurrent.Future;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version $Revision$
 */
public class JettyHttpProducerHeaderBasedCBRTestTest extends CamelTestSupport {

    private String url = "jetty://http://0.0.0.0:9123/foo";
    private static String step;

    @Test
    @SuppressWarnings("unchecked")
    public void testSlowReplyCBRRoutedOnHeader() throws Exception {
        step = "";

        MockEndpoint gold = getMockEndpoint("mock:gold");
        gold.expectedMessageCount(1);
        gold.whenAnyExchangeReceived(new Processor() {
            public void process(Exchange exchange) throws Exception {
                // add the step when we received the message
                step += "C";
            }
        });

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // let it wait for the body now using the future handle
        // Note: we could just use getBody(String.class) and Camel will then automatic wait for you
        Future<String> future = (Future<String>) gold.getReceivedExchanges().get(0).getIn().getBody();
        assertEquals("Bye World", future.get());

        // and ensure the we could CBR on the header before we got the reply body
        assertEquals("ACB", step);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to(url)
                    .choice()
                        .when(header("customer").isEqualTo("gold")).to("mock:gold")
                        .otherwise().to("mock:unknown");

                from(url).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        HttpServletResponse res = exchange.getIn().getBody(HttpServletResponse.class);
                        res.setStatus(200);
                        res.setHeader("customer", "gold");

                        step += "A";

                        // write empty string to force flushing
                        res.getWriter().write("");
                        res.flushBuffer();

                        Thread.sleep(2000);

                        res.getWriter().write("Bye World");
                        res.flushBuffer();

                        step += "B";
                    }
                });
            }
        };
    }
}