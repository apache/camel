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
import org.apache.camel.http.base.HttpOperationFailedException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Based on end user on forum how to get the 404 error code in his enrich aggregator
 */
public class JettyHandle404Test extends BaseJettyTest {

    private static final Logger LOG = LoggerFactory.getLogger(JettyHandle404Test.class);

    public String getProducerUrl() {
        return "http://localhost:{{port}}/myserver?user=Camel";
    }

    @Test
    public void testSimulate404() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Page not found");
        mock.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 404);

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testCustomerErrorHandler() {
        String response
                = template.requestBody("http://localhost:{{port}}/myserver1?throwExceptionOnFailure=false", null, String.class);
        // look for the error message which is sent by MyErrorHandler
        LOG.info("Response: {}", response);
        assertTrue(response.indexOf("MyErrorHandler") > 0, "Get a wrong error message");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // setup the jetty component with the customx error handler
                JettyHttpComponent jettyComponent = (JettyHttpComponent) context.getComponent("jetty");
                jettyComponent.setErrorHandler(new MyErrorHandler());

                // disable error handling
                errorHandler(noErrorHandler());

                from("direct:start").enrich("direct:tohttp", new AggregationStrategy() {
                    public Exchange aggregate(Exchange original, Exchange resource) {
                        // get the response code
                        Integer code = resource.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
                        assertEquals(404, code.intValue());
                        return resource;
                    }
                }).to("mock:result");

                // use this sub route as indirection to handle the
                // HttpOperationFailedException
                // and set the data back as data on the exchange to not cause
                // the exception to be thrown
                from("direct:tohttp").doTry().to(getProducerUrl()).doCatch(HttpOperationFailedException.class)
                        .process(new Processor() {
                            public void process(Exchange exchange) {
                                // copy the caused exception values to the exchange as
                                // we want the response in the regular exchange
                                // instead as an exception that will get thrown and thus
                                // the route breaks
                                HttpOperationFailedException cause
                                        = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, HttpOperationFailedException.class);
                                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, cause.getStatusCode());
                                exchange.getMessage().setBody(cause.getResponseBody());
                            }
                        }).end();

                // this is our jetty server where we simulate the 404
                from("jetty://http://localhost:{{port}}/myserver").process(new Processor() {
                    public void process(Exchange exchange) {
                        exchange.getMessage().setBody("Page not found");
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
                    }
                });
            }
        };
    }
}
