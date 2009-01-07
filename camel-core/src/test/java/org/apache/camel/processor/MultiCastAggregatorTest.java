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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;

public class MultiCastAggregatorTest extends ContextTestSupport {

    public void testMulticastReceivesItsOwnExchangeParallelly() throws Exception {
        sendingAMessageUsingMulticastReceivesItsOwnExchange(true);
    }

    public void testMulticastReceivesItsOwnExchangeSequentially() throws Exception {
        sendingAMessageUsingMulticastReceivesItsOwnExchange(false);
    }

    private void sendingAMessageUsingMulticastReceivesItsOwnExchange(boolean isParallel) throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("inputx+inputy+inputz");

        String url;
        if (isParallel) {
            url = "direct:parallel";
        } else {
            url = "direct:sequential";
        }

        // use InOut
        Exchange exchange = template.request(url, new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody("input");
                in.setHeader("foo", "bar");
            }
        });

        assertNotNull("We should get result here", exchange);
        assertEquals("Can't get the right result", "inputx+inputy+inputz", exchange.getOut().getBody(String.class));

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            public void configure() {
                ThreadPoolExecutor tpExecutor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
                // START SNIPPET: example
                // The message will be sent parallelly to the endpoints
                from("direct:parallel")
                    .multicast(new BodyOutAggregatingStrategy(), true).executor(tpExecutor)
                        .to("direct:x", "direct:y", "direct:z");
                // Multicast the message in a sequential way
                from("direct:sequential").multicast(new BodyOutAggregatingStrategy()).to("direct:x", "direct:y", "direct:z");

                from("direct:x").process(new AppendingProcessor("x")).to("direct:aggregator");
                from("direct:y").process(new AppendingProcessor("y")).to("direct:aggregator");
                from("direct:z").process(new AppendingProcessor("z")).to("direct:aggregator");

                from("direct:aggregator").aggregator(header("cheese"), new BodyInAggregatingStrategy()).
                completedPredicate(header(Exchange.AGGREGATED_COUNT).isEqualTo(3)).to("mock:result");
                // END SNIPPET: example
            }
        };

    }

}
