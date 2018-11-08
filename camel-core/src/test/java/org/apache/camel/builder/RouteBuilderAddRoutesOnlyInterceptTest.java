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
package org.apache.camel.builder;

import org.apache.camel.ContextTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class RouteBuilderAddRoutesOnlyInterceptTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                includeRoutes(new MyExtraRoute());

                from("direct:start").to("mock:result");
            }
        };
    }

    @Test
    public void testAddRoutes() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Foo was here");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    private static class MyExtraRoute extends RouteBuilder {

        public void configure() throws Exception {
            interceptSendToEndpoint("mock:result").transform(constant("Foo was here"));
        }
    }
}