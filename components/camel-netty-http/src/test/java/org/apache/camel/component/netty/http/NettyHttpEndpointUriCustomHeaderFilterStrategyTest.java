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

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

public class NettyHttpEndpointUriCustomHeaderFilterStrategyTest extends BaseNettyTest {

    @BindToRegistry("customHeaderFilterStrategy")
    private CustomHeaderFilterStrategy customHeaderFilterStrategy = new CustomHeaderFilterStrategy();

    @Test
    public void testEndpointUriWithCustomHeaderStrategy() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:outbound");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("Date", "31-03-2014");

        Exchange out = template.request("direct:request", null);

        MockEndpoint.assertIsSatisfied(context);

        String date = out.getMessage().getHeader("sub-date", String.class);
        assertNull(date);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:request").setHeader("Date", constant("31-03-2014"))
                        .to("netty-http:http://localhost:{{port}}/myapp/mytest?headerFilterStrategy=#customHeaderFilterStrategy");

                from("netty-http:http://localhost:{{port}}/myapp/mytest").to("mock:outbound").setHeader("sub-date",
                        constant("31-05-2014"));
            }
        };
    }

    private static class CustomHeaderFilterStrategy extends DefaultHeaderFilterStrategy {
        CustomHeaderFilterStrategy() {
            // allow all outbound headers to pass through but only filter out
            // below inbound header
            getInFilter().add("sub-date");
        }
    }
}
