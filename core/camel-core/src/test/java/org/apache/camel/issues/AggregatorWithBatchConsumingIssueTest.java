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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.junit.Test;

public class AggregatorWithBatchConsumingIssueTest extends ContextTestSupport {

    @Test
    public void testAggregateLostGroupIssue() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(4);

        mock.message(0).body().isEqualTo("0+1+2");
        mock.message(1).body().isEqualTo("3+4+5");
        mock.message(2).body().isEqualTo("6+7+8");
        mock.message(3).body().isEqualTo("9+10+11");

        for (int i = 0; i < 12; i++) {
            sendMessage(i);
        }

        assertMockEndpointsSatisfied();
    }

    private void sendMessage(final int index) {
        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(index);
                exchange.getIn().setHeader("aggregateGroup", "group1");

                // simulate Batch as in JpaConsumer
                exchange.setProperty(Exchange.BATCH_SIZE, 3);
                exchange.setProperty(Exchange.BATCH_INDEX, index % 3);
                exchange.setProperty(Exchange.BATCH_COMPLETE, (index % 3) == 3 - 1);
            }
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").aggregate(header("aggregateGroup"), new BodyInAggregatingStrategy()).completionFromBatchConsumer().to("log:aggregated").to("mock:result");
            }
        };
    }

}
