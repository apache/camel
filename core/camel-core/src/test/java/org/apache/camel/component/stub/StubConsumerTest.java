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
package org.apache.camel.component.stub;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

public class StubConsumerTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:InOnly").setExchangePattern(ExchangePattern.InOnly).to("stub:foo").to("mock:result");

                from("direct:InOut").setExchangePattern(ExchangePattern.InOut).to("stub:foo").to("mock:result");

                from("stub:foo").transform().constant("Bye World");
            }
        };
    }

    final void test(ExchangePattern mep) throws InterruptedException {
        if (mep == ExchangePattern.InOut) {
            getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");
        } else {
            getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        }
        getMockEndpoint("mock:result").setExpectedMessageCount(1);

        template.sendBody("direct:" + mep.name(), "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnly() throws InterruptedException {
        test(ExchangePattern.InOnly);
    }

    @Test
    public void testInOut() throws InterruptedException {
        test(ExchangePattern.InOut);
    }

}
