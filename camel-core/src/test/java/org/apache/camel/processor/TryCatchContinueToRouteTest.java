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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class TryCatchContinueToRouteTest extends ContextTestSupport {

    @Test
    public void testTryCatchContinueToRoute() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:catch").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:catch").message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).isInstanceOf(IllegalArgumentException.class);
        getMockEndpoint("mock:c").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:c").message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).isInstanceOf(IllegalArgumentException.class);

        template.sendBody("direct:a", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a")
                    .to("mock:a")
                    .to("direct:b")
                    .to("direct:c");

                from("direct:b")
                    .doTry()
                        .to("mock:b")
                        .throwException(new IllegalArgumentException("Damn"))
                    .doCatch(Exception.class)
                        .to("mock:catch")
                    .end();

                from("direct:c")
                    .to("mock:c");
            }
        };
    }
}
