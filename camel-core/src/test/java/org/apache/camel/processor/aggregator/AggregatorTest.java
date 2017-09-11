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
package org.apache.camel.processor.aggregator;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;

/**
 * @version 
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

    public void testOneMessage() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);

        resultEndpoint.expectedMessageCount(1);
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("cheese", 123);
        headers.put("bar", "viper bar");

        template.sendBodyAndHeaders("direct:predicate", "test", headers);

        resultEndpoint.assertIsSatisfied();
    }

    public void testBatchTimeoutExpiry() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "message:1", "cheese", 123);

        resultEndpoint.assertIsSatisfied();
    }

    public void testAggregatorNotAtStart() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).header("visited").isNotNull();

        template.sendBodyAndHeader("seda:header", "message:1", "cheese", 123);

        resultEndpoint.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: ex
                // in this route we aggregate all from direct:state based on the header id cheese
                from("direct:start")
                    .aggregate(header("cheese"), new UseLatestAggregationStrategy()).completionTimeout(100).completionTimeoutCheckerInterval(10)
                        .to("mock:result");

                from("seda:header").setHeader("visited", constant(true))
                    .aggregate(header("cheese"), new UseLatestAggregationStrategy()).completionTimeout(100).completionTimeoutCheckerInterval(10)
                        .to("mock:result");

                // in this sample we aggregate with a completion predicate
                from("direct:predicate")
                    .aggregate(header("cheese"), new UseLatestAggregationStrategy()).completionTimeout(100).completionTimeoutCheckerInterval(10)
                        .completionPredicate(header("cheese").isEqualTo(123))
                        .to("mock:result");
                // END SNIPPET: ex
            }
        };
    }
    
}
