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
package org.apache.camel.processor.aggregator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.AggregateDefinition;
import org.junit.Test;

public class AlbertoAggregatorTest extends ContextTestSupport {
    private static final String SURNAME_HEADER = "surname";
    private static final String TYPE_HEADER = "type";
    private static final String BROTHERS_TYPE = "brothers";

    @Test
    public void testAggregator() throws Exception {

        String allNames = "Harpo Marx,Fiodor Karamazov,Chico Marx,Ivan Karamazov,Groucho Marx,Alexei Karamazov,Dimitri Karamazov";

        List<String> marxBrothers = new ArrayList<>();
        marxBrothers.add("Harpo");
        marxBrothers.add("Chico");
        marxBrothers.add("Groucho");

        List<String> karamazovBrothers = new ArrayList<>();
        karamazovBrothers.add("Fiodor");
        karamazovBrothers.add("Ivan");
        karamazovBrothers.add("Alexei");
        karamazovBrothers.add("Dimitri");

        Map<String, List<String>> allBrothers = new HashMap<>();
        allBrothers.put("Marx", marxBrothers);
        allBrothers.put("Karamazov", karamazovBrothers);

        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedBodiesReceived(allBrothers);

        template.sendBody("direct:start", allNames);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        return new RouteBuilder() {
            AggregationStrategy surnameAggregator = new AggregationStrategy() {
                @SuppressWarnings("unchecked")
                public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                    debugIn("Surname Aggregator", oldExchange, newExchange);

                    Exchange answer = newExchange;

                    if (oldExchange != null) {
                        List<String> brothers = oldExchange.getIn().getBody(List.class);
                        brothers.add(newExchange.getIn().getBody(String.class));
                        answer = oldExchange;
                    } else {
                        List<String> brothers = new ArrayList<>();
                        brothers.add(newExchange.getIn().getBody(String.class));
                        newExchange.getIn().setBody(brothers);
                    }

                    debugOut("Surname Aggregator", answer);

                    return answer;
                }
            };

            @SuppressWarnings("unchecked")
            AggregationStrategy brothersAggregator = new AggregationStrategy() {
                public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                    debugIn("Brothers Aggregator", oldExchange, newExchange);

                    Exchange answer = newExchange;

                    if (oldExchange != null) {
                        Map<String, List<?>> brothers = oldExchange.getIn().getBody(Map.class);
                        brothers.put(newExchange.getIn().getHeader(SURNAME_HEADER, String.class), newExchange.getIn().getBody(List.class));
                        answer = oldExchange;
                    } else {
                        Map<String, List<?>> brothers = new HashMap<>();
                        brothers.put(newExchange.getIn().getHeader(SURNAME_HEADER, String.class), newExchange.getIn().getBody(List.class));
                        newExchange.getIn().setBody(brothers);
                    }

                    debugOut("Brothers Aggregator", answer);

                    return answer;
                }
            };

            private void debugIn(String stringId, Exchange oldExchange, Exchange newExchange) {
                if (oldExchange != null) {
                    log.debug(stringId + " old headers in: " + oldExchange.getIn().getHeaders());
                    log.debug(stringId + " old body in: " + oldExchange.getIn().getBody());
                }
                log.debug(stringId + " new headers in: " + newExchange.getIn().getHeaders());
                log.debug(stringId + " new body in: " + newExchange.getIn().getBody());
            }

            private void debugOut(String stringId, Exchange exchange) {
                log.debug(stringId + " old headers out: " + exchange.getIn().getHeaders());
                log.debug(stringId + " old body out: " + exchange.getIn().getBody());
            }

            @Override
            public void configure() throws Exception {

                from("direct:start")
                    // Separate people
                    .split(bodyAs(String.class).tokenize(",")).process(

                    // Split
                    // the
                    // name,
                    // erase
                    // the
                    // surname
                    // and
                    // put it
                    // in a
                    // header
                    new Processor() {
                        public void process(Exchange exchange) throws Exception {

                            String[] parts = exchange.getIn().getBody(String.class).split(" ");
                            exchange.getIn().setBody(parts[0]);
                            exchange.getIn().setHeader(SURNAME_HEADER, parts[1]);
                        } // process
                    }) // Processor

                    .to("direct:joinSurnames");

                from("direct:joinSurnames").aggregate(header(SURNAME_HEADER), surnameAggregator).completionTimeout(100).completionTimeoutCheckerInterval(10)
                    .setHeader(TYPE_HEADER, constant(BROTHERS_TYPE)).to("direct:joinBrothers");

                // Join all brothers lists and remove surname and type headers
                AggregateDefinition agg = from("direct:joinBrothers").aggregate(header(TYPE_HEADER), brothersAggregator);

                agg.completionTimeout(100L);
                agg.completionTimeoutCheckerInterval(10L);
                agg.removeHeader(SURNAME_HEADER).removeHeader(TYPE_HEADER).to("mock:result");
            }
        };
    }
}
