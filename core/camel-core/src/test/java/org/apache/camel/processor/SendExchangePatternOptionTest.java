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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

public class SendExchangePatternOptionTest extends ContextTestSupport {

    @Test
    public void testExchangePatternOptionInOnly() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").message(0).exchangePattern().isEqualTo(ExchangePattern.InOnly);

        getMockEndpoint("mock:stub").expectedMessageCount(1);
        getMockEndpoint("mock:stub").message(0).exchangePattern().isEqualTo(ExchangePattern.InOnly);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testExchangePatternOptionInOut() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").message(0).exchangePattern().isEqualTo(ExchangePattern.InOut);

        getMockEndpoint("mock:stub").expectedMessageCount(1);
        getMockEndpoint("mock:stub").message(0).exchangePattern().isEqualTo(ExchangePattern.InOnly);

        template.requestBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("stub:foo?exchangePattern=InOnly").to("mock:result");

                from("stub:foo").to("mock:stub");
            }
        };
    }
}
