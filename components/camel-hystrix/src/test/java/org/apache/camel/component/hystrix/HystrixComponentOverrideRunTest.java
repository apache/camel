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
package org.apache.camel.component.hystrix;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class HystrixComponentOverrideRunTest extends HystrixComponentBase {

    @EndpointInject(uri = "mock:run2")
    protected MockEndpoint run2Endpoint;

    @Test
    public void invokesOverrideHeader() throws Exception {
        run2Endpoint.expectedMessageCount(1);
        resultEndpoint.expectedMessageCount(0);
        errorEndpoint.expectedMessageCount(0);

        Map headers = new HashMap<>();
        headers.put("key", "cacheKey");
        headers.put(HystrixConstants.CAMEL_HYSTRIX_RUN_ENDPOINT, "direct:run2");

        template.sendBodyAndHeaders("body", headers);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                from("direct:fallback")
                        .to("mock:error");

                from("direct:run")
                        .to("mock:result");

                from("direct:run2")
                        .to("mock:run2");

                from("direct:start")
                        .to("hystrix:testKey?runEndpoint=direct:run&fallbackEndpoint=direct:fallback&initializeRequestContext=true");
            }
        };
    }
}

