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
package org.apache.camel.test.junit5.patterns;

import java.util.concurrent.TimeUnit;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.RepeatedTest;

public class MockEndpointsInterceptSendToEndpointTest extends CamelTestSupport {

    @Override
    public String isMockEndpoints() {
        return "*";
    }

    @RepeatedTest(20)
    public void testMockEndpointsAndInterceptSendToEndpoint() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:intercepted").expectedMessageCount(3);

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("*").log("intercepted ${exchangeProperty.CamelInterceptedEndpoint}")
                        .to("mock:intercepted");

                from("direct:start").to("log:foo").to("mock:result");
            }
        };
    }

}
