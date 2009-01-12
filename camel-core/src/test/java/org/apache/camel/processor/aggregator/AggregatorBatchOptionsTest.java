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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test for the batch size options on aggregator.
 */
public class AggregatorBatchOptionsTest extends ContextTestSupport {

    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testAggregateOutBatchSize() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                // our route is aggregating from the direct queue and sending the response to the mock
                from("direct:start")
                    // aggregated by header id
                    // as we have not configured more on the aggregator it will default to aggregate the
                    // latest exchange only
                    .aggregator().header("id")
                    // wait for 0.5 seconds to aggregate
                    .batchTimeout(500L)
                    // batch size in is the limit of number of exchanges received, so when we have received 100
                    // exchanges then whatever we have in the collection will be sent
                    .batchSize(100)
                    // limit the out batch size to 3 so when we have aggregated 3 exchanges
                    // and we reach this limit then the exchanges is send
                    .outBatchSize(3)
                    .to("mock:result");
                // END SNIPPET: e1
            }
        });
        startCamelContext();

        // START SNIPPET: e2
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMinimumMessageCount(4);

        // then we sent all the message at once
        template.sendBodyAndHeader("direct:start", "Message 1a", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 2a", "id", "2");
        template.sendBodyAndHeader("direct:start", "Message 1b", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 2b", "id", "2");
        template.sendBodyAndHeader("direct:start", "Message 1c", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 3a", "id", "3");
        // when we send message 4 then we will reach the collection batch size limit and the
        // exchanges above is the ones we have aggregated in the first batch
        template.sendBodyAndHeader("direct:start", "Message 4", "id", "4");

        assertMockEndpointsSatisfied();

        // first batch
        assertEquals("Message 1c", result.getExchanges().get(0).getIn().getBody());
        assertEquals("Message 2b", result.getExchanges().get(1).getIn().getBody());
        assertEquals("Message 3a", result.getExchanges().get(2).getIn().getBody());

        // second batch
        assertEquals("Message 4", result.getExchanges().get(3).getIn().getBody());
        // END SNIPPET: e2
    }

    public void testAggregateBatchSize() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e3
                // our route is aggregating from the direct queue and sending the response to the mock
                from("direct:start")
                    // aggregated by header id
                    // as we have not configured more on the aggregator it will default to aggregate the
                    // latest exchange only
                    .aggregator().header("id")
                    // wait for 0.5 seconds to aggregate
                    .batchTimeout(500L)
                    // batch size in is the limit of number of exchanges received, so when we have received 5
                    // exchanges then whatever we have in the collection will be sent
                    .batchSize(5)
                    .to("mock:result");
                // END SNIPPET: e3
            }
        });
        startCamelContext();

        // START SNIPPET: e4
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMinimumMessageCount(5);

        // then we sent all the message at once
        template.sendBodyAndHeader("direct:start", "Message 1a", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 2a", "id", "2");
        template.sendBodyAndHeader("direct:start", "Message 1b", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 2b", "id", "2");
        template.sendBodyAndHeader("direct:start", "Message 1c", "id", "1");

        // need a little sleep between batches
        Thread.sleep(10);
        // when we sent the next message we have reached the in batch size limit and the current
        // aggregated exchanges will be sent
        template.sendBodyAndHeader("direct:start", "Message 3a", "id", "3");
        template.sendBodyAndHeader("direct:start", "Message 4", "id", "4");
        template.sendBodyAndHeader("direct:start", "Message 3b", "id", "3");
        template.sendBodyAndHeader("direct:start", "Message 3c", "id", "3");
        template.sendBodyAndHeader("direct:start", "Message 1d", "id", "1");

        assertMockEndpointsSatisfied();

        // first batch
        assertEquals("Message 1c", result.getExchanges().get(0).getIn().getBody());
        assertEquals("Message 2b", result.getExchanges().get(1).getIn().getBody());

        // second batch
        assertEquals("Message 3c", result.getExchanges().get(2).getIn().getBody());
        assertEquals("Message 4", result.getExchanges().get(3).getIn().getBody());
        assertEquals("Message 1d", result.getExchanges().get(4).getIn().getBody());
        // END SNIPPET: e4
    }

    public void testAggregateBatchTimeout() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e5
                // our route is aggregating from the direct queue and sending the response to the mock
                from("direct:start")
                    // aggregated by header id
                    // as we have not configured more on the aggregator it will default to aggregate the
                    // latest exchange only
                    .aggregator().header("id")
                    // wait for 0.5 seconds to aggregate
                    .batchTimeout(500L)
                    .to("mock:result");
                // END SNIPPET: e5
            }
        });
        startCamelContext();

        // START SNIPPET: e6
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMinimumMessageCount(6);

        // then we sent all the message at once
        template.sendBodyAndHeader("direct:start", "Message 1a", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 2a", "id", "2");
        template.sendBodyAndHeader("direct:start", "Message 1b", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 2b", "id", "2");
        template.sendBodyAndHeader("direct:start", "Message 1c", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 3a", "id", "3");
        Thread.sleep(600L);
        // these messages are not aggregated in the first batch as the timeout should have accoured
        template.sendBodyAndHeader("direct:start", "Message 4", "id", "4");
        template.sendBodyAndHeader("direct:start", "Message 3b", "id", "3");
        template.sendBodyAndHeader("direct:start", "Message 3c", "id", "3");
        template.sendBodyAndHeader("direct:start", "Message 1d", "id", "1");

        assertMockEndpointsSatisfied();

        // first batch
        assertEquals("Message 1c", result.getExchanges().get(0).getIn().getBody());
        assertEquals("Message 2b", result.getExchanges().get(1).getIn().getBody());
        assertEquals("Message 3a", result.getExchanges().get(2).getIn().getBody());

        // second batch
        assertEquals("Message 4", result.getExchanges().get(3).getIn().getBody());
        assertEquals("Message 3c", result.getExchanges().get(4).getIn().getBody());
        assertEquals("Message 1d", result.getExchanges().get(5).getIn().getBody());
        // END SNIPPET: e6
    }

}