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
import org.junit.jupiter.api.Test;

public class LoopOnPrepareTest extends ContextTestSupport {

    @Test
    public void testLoopOnPrepare() throws Exception {
        getMockEndpoint("mock:loop").expectedBodiesReceived("AB", "AB", "AB");
        getMockEndpoint("mock:result").expectedBodiesReceived("AB");

        template.sendBody("direct:start", "A");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .loop(3).onPrepare(new MyPrepare()).transform(body().append("B")).to("mock:loop").end().to("mock:result");
            }
        };
    }

    private class MyPrepare implements Processor {

        private String original;

        @Override
        public void process(Exchange exchange) throws Exception {
            if (original == null) {
                original = exchange.getMessage().getBody(String.class);
            } else {
                // restore original input for each loop
                exchange.getMessage().setBody(original);
            }
        }
    }
}
