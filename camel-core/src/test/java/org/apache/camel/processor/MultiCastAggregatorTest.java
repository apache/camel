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

        Exchange exchange = template.send(url, new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody("input");
                in.setHeader("foo", "bar");
            }
        });

        assertNotNull("We should get result here", exchange);
        assertEquals("Can't get the right result", "inputx+inputy+inputz", exchange.getOut().getBody(String.class));

        assertMockEndpointsSatisifed();
    }

    private class AppendingProcessor implements Processor {
        private String appendingString;

        public AppendingProcessor(String string) {
            appendingString = string;
        }

        public void process(Exchange exchange) {
            // lets transform the IN message
            Message in = exchange.getIn();
            String body = in.getBody(String.class);
            in.setBody(body + appendingString);
        }
    }

    private class BodyOutAggregatingStrategy implements AggregationStrategy {

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            Message newOut = newExchange.getOut();
            String oldBody = oldExchange.getOut().getBody(String.class);
            String newBody = newOut.getBody(String.class);
            newOut.setBody(oldBody + "+" + newBody);
            return newExchange;
        }

    }

    private class BodyInAggregatingStrategy implements AggregationStrategy {

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            Exchange copy = newExchange.copy();
            Message newIn = copy.getIn();
            String oldBody = oldExchange.getIn().getBody(String.class);
            String newBody = newIn.getBody(String.class);
            newIn.setBody(oldBody + "+" + newBody);
            Integer old = (Integer) oldExchange.getProperty("aggregated");
            if (old == null) {
                old = 1;
            }
            copy.setProperty("aggregated", old + 1);
            return copy;
        }
    }

    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            public void configure() {
                ThreadPoolExecutor tpExecutor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
                // START SNIPPET: example
                // The message will be sent parallelly to the endpoints
                from("direct:parallel")
                    .multicast(new BodyOutAggregatingStrategy(), true).setThreadPoolExecutor(tpExecutor)
                        .to("direct:x", "direct:y", "direct:z");
                // Multicast the message in a sequential way
                from("direct:sequential").multicast(new BodyOutAggregatingStrategy()).to("direct:x", "direct:y", "direct:z");

                from("direct:x").process(new AppendingProcessor("x")).to("direct:aggregater");
                from("direct:y").process(new AppendingProcessor("y")).to("direct:aggregater");
                from("direct:z").process(new AppendingProcessor("z")).to("direct:aggregater");

                from("direct:aggregater").aggregator(header("cheese"), new BodyInAggregatingStrategy()).
                completedPredicate(header("aggregated").isEqualTo(3)).to("mock:result");
                // END SNIPPET: example
            }
        };

    }

}
