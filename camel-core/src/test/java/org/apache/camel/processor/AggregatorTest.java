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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class AggregatorTest extends ContextTestSupport {
    protected int messageCount = 100;

    public void testSendingLotsOfMessagesGetAggregatedToTheLatestMessage() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);

        resultEndpoint.expectedBodiesReceived("message:" + messageCount);

        // lets send a large batch of messages
        for (int i = 1; i <= messageCount; i++) {
            String body = "message:" + i;
            template.sendBodyAndHeader("direct:start", body, "cheese", 123);
        }

        resultEndpoint.assertIsSatisfied();
    }

    public void testPredicate() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);

        resultEndpoint.expectedMessageCount(messageCount / 5);
        // lets send a large batch of messages
        for (int i = 1; i <= messageCount; i++) {
            String body = "message:" + i;
            template.sendBodyAndHeader("direct:predicate", body, "cheese", 123);
        }

        resultEndpoint.assertIsSatisfied();
    }

    public void testOneMessage() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);

        resultEndpoint.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:predicate", "test", "aggregated", 5);
        resultEndpoint.assertIsSatisfied();
    }

    public void testBatchTimeoutExpiry() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.setSleepForEmptyTest(2 * BatchProcessor.DEFAULT_BATCH_TIMEOUT);
        template.sendBodyAndHeader("direct:start", "message:1", "cheese", 123);
        resultEndpoint.assertIsSatisfied();
    }

    //TODO fix this test
    public void xtestAggregatorNotAtStart() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).header("visited").isNotNull();
        resultEndpoint.setSleepForEmptyTest(2 * BatchProcessor.DEFAULT_BATCH_TIMEOUT);
        template.sendBodyAndHeader("seda:header", "message:1", "cheese", 123);
        resultEndpoint.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: ex
                from("direct:start").aggregator(header("cheese")).to("mock:result");

                from("seda:header").setHeader("visited", constant(true)).aggregator(header("cheese")).to("mock:result");

                from("direct:predicate").aggregator(header("cheese"), new MyAggregationStrategy()).
                        completedPredicate(header("aggregated").isEqualTo(5)).to("mock:result");
                // END SNIPPET: ex
            }
        };
    }
}
