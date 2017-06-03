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
package org.apache.camel.component.ahc;


import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.junit.Test;

public class AhcComponentClientConfigTest extends BaseAhcTest {

    public void configureComponent() {
        // START SNIPPET: e1
        // create a client config builder
        DefaultAsyncHttpClientConfig.Builder builder = new DefaultAsyncHttpClientConfig.Builder();
        // use the builder to set the options we want, in this case we want to follow redirects and try
        // at most 3 retries to send a request to the host
        DefaultAsyncHttpClientConfig config = builder.setFollowRedirect(true).setMaxRequestRetry(3).build();

        // lookup AhcComponent
        AhcComponent component = context.getComponent("ahc", AhcComponent.class);
        // and set our custom client config to be used
        component.setClientConfig(config);
        // END SNIPPET: e1
    }

    @Test
    public void testAhcComponentClientConfig() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", null);

        assertMockEndpointsSatisfied();
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                configureComponent();

                from("direct:start")
                    .to(getAhcEndpointUri())
                    .to("mock:result");

                from(getTestServerEndpointUri())
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                // redirect to test the client config worked as we told it to follow redirects
                                exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, "301");
                                exchange.getOut().setHeader("Location", getTestServerEndpointTwoUrl());
                            }
                        });

                from(getTestServerEndpointTwoUri())
                        .transform(constant("Bye World"));
            }
        };
    }
}
