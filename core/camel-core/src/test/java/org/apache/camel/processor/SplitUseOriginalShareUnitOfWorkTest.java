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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SplitUseOriginalShareUnitOfWorkTest extends ContextTestSupport {

    @Test
    public void testWithoutShareUnitOfWork() throws Exception {
        getMockEndpoint("mock:split").expectedMessageCount(2);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:noShare", "A,B");

        assertMockEndpointsSatisfied();

        Exchange out = getMockEndpoint("mock:result").getReceivedExchanges().get(0);
        assertEquals("A,B", out.getIn().getBody(String.class));
    }

    @Test
    public void testWithShareUnitOfWork() throws Exception {
        getMockEndpoint("mock:split").expectedMessageCount(2);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:share", "A,B");

        assertMockEndpointsSatisfied();

        Exchange out = getMockEndpoint("mock:result").getReceivedExchanges().get(0);
        assertEquals("A,B", out.getIn().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:noShare")
                        .split(body().tokenize(","), new UseOriginalAggregationStrategy())
                            .to("mock:split")
                        .end()
                        .to("mock:result");

                from("direct:share")
                        .split(body().tokenize(","), new UseOriginalAggregationStrategy()).shareUnitOfWork()
                            .to("mock:split")
                        .end()
                        .to("mock:result");
            }
        };
    }
}
