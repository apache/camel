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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.AggregationStrategy;

/**
 * @version 
 */
public class MulticastSingleAggregateIssueTest extends ContextTestSupport {

    public void testMulticastSingleAggregateIssue() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived(2);
        getMockEndpoint("mock:a").expectedHeaderReceived("foo", "I was here");

        template.sendBody("direct:a", 1);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a").multicast(new SumAggregateBean())
                    .to("direct:foo")
                .end()
                .to("mock:a");

                from("direct:foo")
                    .bean(IncreaseOne.class);
            }
        };
    }

    public static class IncreaseOne {

        public int addOne(int num) {
            return num + 1;
        }
    }

    public static class SumAggregateBean implements AggregationStrategy {

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            newExchange.getIn().setHeader("foo", "I was here");

            if (oldExchange == null) {
                return newExchange;
            }

            int num1 = oldExchange.getIn().getBody(int.class);
            int num2 = newExchange.getIn().getBody(int.class);

            newExchange.getIn().setHeader("foo", "I was here");
            newExchange.getIn().setBody(num1 + num2);
            return newExchange;
        }
    }

}
