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
package org.apache.camel.processor.intercept;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class DualInterceptSimpleRouteTest extends ContextTestSupport {

    public void testDualIntercept() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        getMockEndpoint("mock:intercepted").expectedMessageCount(6);
        getMockEndpoint("mock:a").expectedMessageCount(3);
        getMockEndpoint("mock:b").expectedMessageCount(3);

        template.sendBody("direct:start", "Hello World");

        // TODO: Using multiple intercept should be avoided
        // assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setTracing(true);

                // it should generally be avoid to have dual interceptors as its a bit confusing
                // but you can do it anyway
                intercept().to("mock:intercepted");

                intercept().to("mock:a").to("mock:b");

                from("direct:start")
                    .to("mock:foo").to("mock:bar").to("mock:result");
            }
        };
    }
}