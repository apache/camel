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
package org.apache.camel.builder.endpoint;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class EndpointQueryParamTest extends CamelTestSupport {
    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().component("jetty").host("localhost").port(9999);
                rest().get("path/xyz")
                    .to("log:myLogger?level=INFO&showAll=true")
                    .to("mock:result");
                from(direct("test"))
                        .to(http("localhost:9999/path/xyz?param1=1&param2=2").httpMethod("GET"));
                from(direct("test2"))
                        .to("http://localhost:9999/path/xyz?param1=1&param2=2&httpMethod=GET");
            }
        };
    }

    @Test
    public void testRoute() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");

        mockEndpoint.expectedHeaderReceived("param1", "1");
        mockEndpoint.expectedHeaderReceived("param2", "2");

        template.sendBody("direct:test2", null);
        mockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testEndpointDslRoute() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedHeaderReceived("param1", "1");
        mockEndpoint.expectedHeaderReceived("param2", "2");

        template.sendBody("direct:test", null);
        mockEndpoint.assertIsSatisfied();
    }
}
