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
package org.apache.camel.component.undertow.rest;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.junit.Test;

public class RestUndertowHttpMultipleVerbsTest extends BaseUndertowTest {
    
    @Test
    public void testProducerGetPut() throws Exception {
        getMockEndpoint("mock:get").expectedMessageCount(1);
        getMockEndpoint("mock:put").expectedMessageCount(0);
        template.requestBodyAndHeader("undertow:http://localhost:{{port}}/example/123", null, Exchange.HTTP_METHOD, "GET");
        assertMockEndpointsSatisfied();

        resetMocks();

        getMockEndpoint("mock:get").expectedMessageCount(0);
        getMockEndpoint("mock:put").expectedMessageCount(1);
        template.requestBodyAndHeader("undertow:http://localhost:{{port}}/example/456", "Hello World", Exchange.HTTP_METHOD, "PUT");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use undertow on localhost with the given port
                restConfiguration().component("undertow").host("localhost").port(getPort());

                rest("/example")
                    .get("{pathParamHere}").to("mock:get")
                    .put("{pathParamHere}").to("mock:put");
            }
        };
    }

}
