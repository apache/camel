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

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Based on end user on forum how to get the 404 error code in his enrich
 * aggregator
 */
public class JettySimplifiedHandle404Test extends BaseJettyTest {

    @Test
    public void testSimulate404() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Page not found");
        mock.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 404);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // disable error handling
                errorHandler(noErrorHandler());

                // START SNIPPET: e1
                // We set throwExceptionOnFailure to false to let Camel return
                // any response from the remove HTTP server without thrown
                // HttpOperationFailedException in case of failures.
                // This allows us to handle all responses in the aggregation
                // strategy where we can check the HTTP response code
                // and decide what to do. As this is based on an unit test we
                // assert the code is 404
                from("direct:start").enrich("http://localhost:{{port}}/myserver?throwExceptionOnFailure=false&user=Camel", new AggregationStrategy() {
                    public Exchange aggregate(Exchange original, Exchange resource) {
                        // get the response code
                        Integer code = resource.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
                        assertEquals(404, code.intValue());
                        return resource;
                    }
                }).to("mock:result");

                // this is our jetty server where we simulate the 404
                from("jetty://http://localhost:{{port}}/myserver").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getOut().setBody("Page not found");
                        exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
                    }
                });
                // END SNIPPET: e1
            }
        };
    }
}
