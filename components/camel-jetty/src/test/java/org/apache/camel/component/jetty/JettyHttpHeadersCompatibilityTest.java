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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class JettyHttpHeadersCompatibilityTest extends CamelTestSupport {

    private String targetProducerUri = "http://localhost:8542/someservice?bridgeEndpoint=true&throwExceptionOnFailure=false";
    private String targetConsumerUri = "jetty:http://localhost:8542/someservice?matchOnUriPrefix=true";
    private String sourceUri = "jetty:http://localhost:6323/myservice?matchOnUriPrefix=true";
    private String sourceProducerUri = "http://localhost:6323/myservice";

    @Test
    void testGetRootPath() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hi! /someservice");

        template.sendBody("direct:root", "");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testGetWithRelativePath() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hi! /someservice/relative");

        template.sendBody("direct:relative", "");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(targetConsumerUri)
                        .process(exchange -> {
                            String path = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
                            exchange.getMessage().setBody("Hi! " + path);
                        });

                from(sourceUri)
                        .to(targetProducerUri);

                from("direct:root")
                        .to(sourceProducerUri)
                        .to("mock:result");

                from("direct:relative")
                        .to(sourceProducerUri + "/relative")
                        .to("mock:result");
            }
        };
    }
}
