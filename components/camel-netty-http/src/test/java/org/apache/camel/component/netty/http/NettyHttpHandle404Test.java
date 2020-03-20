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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class NettyHttpHandle404Test extends BaseNettyTest {

    public String getProducerUrl() {
        return "netty-http:http://localhost:{{port}}/myserver?user=Camel";
    }

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

                from("direct:start").enrich("direct:tohttp", (original, resource) -> {
                    // get the response code
                    Integer code = resource.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
                    assertEquals(404, code.intValue());
                    return resource;
                }).to("mock:result");

                // use this sub route as indirection to handle the HttpOperationFailedException
                // and set the data back as data on the exchange to not cause the exception to be thrown
                from("direct:tohttp")
                    .doTry()
                        .to(getProducerUrl())
                    .doCatch(NettyHttpOperationFailedException.class)
                        .process(exchange -> {
                            // copy the caused exception values to the exchange as we want the response in the regular exchange
                            // instead as an exception that will get thrown and thus the route breaks
                            NettyHttpOperationFailedException cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, NettyHttpOperationFailedException.class);
                            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, cause.getStatusCode());
                            exchange.getMessage().setBody(cause.getContentAsString());
                        })
                        .end();


                // this is our jetty server where we simulate the 404
                from("netty-http:http://localhost:{{port}}/myserver")
                        .process(exchange -> {
                            exchange.getMessage().setBody("Page not found");
                            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
                        });
            }
        };
    }

}
