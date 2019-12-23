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
package org.apache.camel.issues;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class MulticastPipelineAggregateIssueTest extends ContextTestSupport {

    @Test
    public void testMulticastPipelineAggregateIssue() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived(8);
        getMockEndpoint("mock:b").expectedBodiesReceived(8);
        getMockEndpoint("mock:c").expectedBodiesReceived(8);

        template.sendBody("direct:a", 1);
        template.sendBody("direct:b", 1);
        template.sendBody("direct:c", 1);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a").multicast(new SumAggregateBean()).pipeline().bean(IncreaseOne.class).bean(new IncreaseTwo()).end().pipeline().bean(IncreaseOne.class)
                    .bean(new IncreaseTwo()).end().end().to("mock:a");

                from("direct:b").multicast(new SumAggregateBean()).pipeline().transform(method(IncreaseOne.class)).bean(new IncreaseTwo()).end().pipeline()
                    .transform(method(IncreaseOne.class)).bean(new IncreaseTwo()).end().end().to("mock:b");

                from("direct:c").multicast(new SumAggregateBean()).pipeline().transform(method(IncreaseOne.class)).transform(method(new IncreaseTwo())).end().pipeline()
                    .transform(method(IncreaseOne.class)).transform(method(new IncreaseTwo())).end().end().to("mock:c");
            }
        };
    }

    public static class IncreaseOne {

        public int addOne(int num) {
            return num + 1;
        }
    }

    public static class IncreaseTwo {

        public int addTwo(int num) {
            return num + 2;
        }
    }

    public static class SumAggregateBean implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }

            int num1 = oldExchange.getIn().getBody(int.class);
            int num2 = newExchange.getIn().getBody(int.class);

            newExchange.getIn().setBody(num1 + num2);
            return newExchange;
        }
    }

}
