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
package org.apache.camel.processor.async;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class AsyncEndpointDoWhileLoopTest extends ContextTestSupport {

    @Test
    public void testAsyncEndpoint() throws Exception {
        getMockEndpoint("mock:line").expectedBodiesReceived("Bye Camel", "Bye Camel", "Bye Camel", "Bye Camel");
        getMockEndpoint("mock:result").expectedBodiesReceived("done");

        template.requestBody("direct:start", "World", String.class);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("async", new MyAsyncComponent());

                from("direct:start")
                    .loopDoWhile(body().isNotEqualTo("done"))
                        .to("async:bye:camel")
                        .to("mock:line")
                        .filter(exchangeProperty(Exchange.LOOP_INDEX).isEqualTo(3))
                            .setBody().constant("done")
                        .end()
                    .end()
                    .to("mock:result");
            }
        };
    }
    
}