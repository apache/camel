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
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class TryCatchNestedChoiceTest extends ContextTestSupport {

    @Test
    public void testFoo() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:other").expectedMessageCount(0);
        getMockEndpoint("mock:catch").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", 123);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOther() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(0);
        getMockEndpoint("mock:other").expectedMessageCount(1);
        getMockEndpoint("mock:catch").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "Hello Bar", "bar", 456);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCatch() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(0);
        getMockEndpoint("mock:other").expectedMessageCount(0);
        getMockEndpoint("mock:catch").expectedMessageCount(1);

        template.sendBody("direct:start", "Kaboom");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").doTry().to("direct:bar").choice().when().simple("${header.foo} == 123").to("mock:foo").otherwise().to("mock:other").endDoTry()
                    .doCatch(Exception.class).to("mock:catch").end();

                from("direct:bar").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        String body = exchange.getIn().getBody(String.class);
                        if (body.contains("Kaboom")) {
                            throw new IllegalArgumentException("Forced error");
                        }
                    }
                });
            }
        };
    }
}
