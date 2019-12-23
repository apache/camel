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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.UseOriginalAggregationStrategy;
import org.junit.Test;

public class SplitterUseOriginalLoopTest extends ContextTestSupport {

    @Test
    public void testUseOriginalLoop() throws Exception {
        getMockEndpoint("mock:line").expectedMessageCount(6);
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello,World");
        getMockEndpoint("mock:result").expectedHeaderReceived("looping", 2);
        getMockEndpoint("mock:result").message(0).header("myHeader").isNull();

        template.sendBody("direct:start", "Hello,World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").loop(3).setHeader("looping", exchangeProperty(Exchange.LOOP_INDEX)).split(body(), new UseOriginalAggregationStrategy(null, false))
                    .setHeader("myHeader", exchangeProperty(Exchange.LOOP_INDEX)).to("mock:line").end().end().log("${headers}").to("mock:result");
            }
        };
    }

}
