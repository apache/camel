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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;

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
        testSendALargeBatch("direct:predicate");
    }
    
    public void testOneMessage() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);

        resultEndpoint.expectedMessageCount(1);
        Map headers = new HashMap();
        headers.put("cheese", 123);
        headers.put("aggregated", 5);

        template.sendBodyAndHeaders("direct:predicate", "test", headers);
        resultEndpoint.assertIsSatisfied();
    }

    public void testBatchTimeoutExpiry() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.setSleepForEmptyTest(2 * BatchProcessor.DEFAULT_BATCH_TIMEOUT);
        template.sendBodyAndHeader("direct:start", "message:1", "cheese", 123);
        resultEndpoint.assertIsSatisfied();
    }


    public void testAggregatorNotAtStart() throws Exception {
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
                // in this route we aggregate all from direct:state based on the header id cheese
                from("direct:start")
                    .aggregate(header("cheese"), new UseLatestAggregationStrategy()).completionTimeout(1000L)
                        .to("mock:result");

                from("seda:header").setHeader("visited", constant(true))
                    .aggregate(header("cheese"), new UseLatestAggregationStrategy()).completionTimeout(1000L)
                        .to("mock:result");

                // in this sample we aggregate using our own strategy with a completion predicate
                // stating that the aggregated header is equal to 5.
                from("direct:predicate")
                    .aggregate(header("cheese"), new MyAggregationStrategy()).completionPredicate(header("aggregated").isEqualTo(5))
                        .to("mock:result");
                // END SNIPPET: ex
            }
        };
    }
    
    private void testSendALargeBatch(String endpointUri) throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);

        resultEndpoint.expectedMessageCount(messageCount / 5);
        // lets send a large batch of messages
        for (int i = 1; i <= messageCount; i++) {
            String body = "message:" + i;
            template.sendBodyAndHeader(endpointUri, body, "cheese", 123);
            // need a little sleep when sending large batches
            Thread.sleep(2);
        }

        resultEndpoint.assertIsSatisfied();
        
    }
}
